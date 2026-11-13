package org.kodein.arch.vie.timemachine.client.timeline

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.update
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.client.ProbeLog
import org.kodein.arch.vie.timemachine.client.callingApp
import org.kodein.arch.vie.timemachine.client.defaultMaxObservationLength
import org.kodein.arch.vie.timemachine.client.role.VIERole
import org.kodein.arch.vie.timemachine.client.viewmodel.TimeMachineMiddleware
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.TimeMachineTimelineMessage
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo


/**
 * Turns what a [TimeMachineMiddleware] reports into [TimeMachineTimelineMessage]s, handed to [outlet]
 * as [Entry]s, and keeps the registry of the ViewModels that are alive.
 *
 * Each message's `at` is captured when it is reported. Its `sequence` is not: the outlet's consumer
 * passes it to [Entry.describe], so that the messages are numbered in the order they are sent.
 *
 * Only capturing `at` happens on the reporting thread. An observed object is kept by reference, and
 * its `toString()`, encoding and type name are computed by [Entry.describe], wherever the outlet
 * calls it: VIE objects are immutable, and describing a large model on every update must not slow
 * down the ViewModel's own dispatcher. The object therefore stays reachable until its entry is
 * described, just before it is sent, or dropped.
 *
 * The registry never keeps a ViewModel reachable: it knows each one through its
 * [TimeMachineIntervener], which holds it weakly. A ViewModel that is garbage-collected without ever
 * having been cleared — built by hand, or dropped by a scope that does not clear it — is reported
 * closed, and forgotten, the next time the registry is looked up, and at the latest once as many
 * ViewModels have started as it held.
 *
 * Once [close]d, when its probe stops, the sink reports nothing and knows no ViewModel: a middleware
 * that outlives its probe then costs its ViewModel one check per object.
 *
 * An observation's `toString()` is cut at half of [maxObservationLength], with a marker saying how much
 * was left out, and the observation says it was [VIEObservation.truncated]: the other half is what
 * its JSON may take, which [TimeMachineOutbox] enforces where it knows its length.
 *
 * @property log Where a failure to describe an object is printed, and where the middlewares that
 *   report to this sink print: a middleware knows its probe through its sink alone.
 * @param maxObservationLength See [org.kodein.arch.vie.timemachine.client.TimeMachineConfig.maxObservationLength].
 * @param outlet Receives every entry, in report order for any single calling thread. Must not
 *   throw nor suspend.
 */
internal class TimeMachineSink(
    val log: ProbeLog,
    maxObservationLength: Int = defaultMaxObservationLength,
    private val outlet: (Entry) -> Unit,
) {

    private val maxStringLength = maxObservationLength / 2

    /**
     * A timeline message whose content is only built by [describe].
     *
     * @property viewModelId The ViewModel the message is about, to which its loss is attributed.
     */
    class Entry(val viewModelId: Uuid, private val build: (sequence: TimeMachineSequence) -> TimeMachineTimelineMessage) {
        /** Builds the message, at position [sequence]. Never throws: a failure to describe an object
         *  is printed, and the message still built without the failed part. That holds for anything
         *  the app's code throws, an `Error` or a `CancellationException` included: this runs on the
         *  probe's own coroutine, which must not end because of an object the app produced. */
        fun describe(sequence: TimeMachineSequence): TimeMachineTimelineMessage = build(sequence)
    }

    /** A ViewModel started and not closed yet. Does not keep it reachable: see [TimeMachineIntervener]. */
    class LiveViewModel(val info: VIEViewModelInfo, val intervener: TimeMachineIntervener)

    // `null` once closed: a ViewModel that starts while the sink closes is then never registered.
    private val live = AtomicReference<Map<Uuid, LiveViewModel>?>(emptyMap())

    /** Whether [close] was called. */
    val isClosed: Boolean get() = live.load() == null

    // The registry size from which [started] prunes it: twice what the last pruning left, so that
    // pruning costs each start a constant on average. Only a hint: a race on it prunes once more or less.
    private val pruneFrom = AtomicInt(minPruneFrom)

    /**
     * The ViewModels started and not closed yet, by identifier; none once the sink is closed. Looking
     * them up first reports closed those that were garbage-collected without having been cleared.
     */
    val liveViewModels: Map<Uuid, LiveViewModel>
        get() {
            prune()
            return live.load().orEmpty()
        }

    /** Stops reporting, for good, and lets go of the live ViewModels. */
    fun close() {
        live.store(null)
    }

    /** Reports that the ViewModel described by [info] started, and that [intervener] intervenes in it. */
    fun started(info: VIEViewModelInfo, intervener: TimeMachineIntervener) {
        if (isClosed) return
        live.update { it?.plus(info.id to LiveViewModel(info, intervener)) }
        val at = Clock.System.now()
        outlet(Entry(info.id) { TimeMachineClientMessage.ViewModelStarted(info = info, at = at, sequence = it) })
        // Without this, an app that never clears its ViewModels would grow the registry for as long
        // as no inspector looks it up.
        if ((live.load()?.size ?: 0) >= pruneFrom.load()) pruneFrom.store(maxOf(minPruneFrom, 2 * prune()))
    }

    /**
     * Reports an object observed flowing through the ViewModel [viewModelId].
     *
     * @param interventionId The request that placed the object, or `null` if the app produced it.
     */
    fun <T : Any> observed(viewModelId: Uuid, kind: VIEObjectKind, role: VIERole<T>, value: T, interventionId: Uuid? = null) {
        // Checked first: an object of a stopped probe costs neither a clock reading nor an entry.
        if (isClosed) return
        val at = Clock.System.now()
        outlet(Entry(viewModelId) { sequence ->
            // Not printed: the failure takes the place of the string, where the inspector shows it.
            val rendered = callingApp(onFailure = { "<toString() failed: $it>" }) { value.toString() }
            // What the failure messages below print, and what is sent: never the whole of a huge object.
            val asString = rendered.cutAt(maxStringLength)
            val observation = VIEObservation(
                viewModelId = viewModelId,
                at = at,
                kind = kind,
                typeName = log.callingApp({ "name $kind $asString" }) { role.typeNameOf(value) } ?: role.typeName,
                asString = asString,
                asJson = log.callingApp({ "encode $kind $asString" }) { role.encode(value) },
                interventionId = interventionId,
                delivery = deliveryOf(kind, value),
                truncated = rendered.length > maxStringLength,
            )
            TimeMachineClientMessage.Observed(observation = observation, sequence = sequence)
        })
    }

    /** Reports that the ViewModel [viewModelId] was closed, unless it is not alive: never started,
     *  or already reported closed. */
    fun closed(viewModelId: Uuid) {
        if (!forget(viewModelId)) return
        val at = Clock.System.now()
        outlet(Entry(viewModelId) { TimeMachineClientMessage.ViewModelClosed(viewModelId = viewModelId, at = at, sequence = it) })
    }

    /** Removes [viewModelId] from the registry: `true` for the one call that does, which is the one
     *  that reports the close, however many threads find out at once that the ViewModel is gone. */
    private fun forget(viewModelId: Uuid): Boolean {
        while (true) {
            val current = live.load() ?: return false
            if (viewModelId !in current) return false
            if (live.compareAndSet(current, current - viewModelId)) return true
        }
    }

    /** Reports closed the ViewModels that were garbage-collected without having been cleared, and
     *  returns how many are left. */
    private fun prune(): Int {
        val current = live.load() ?: return 0
        var left = current.size
        for ((viewModelId, viewModel) in current) {
            if (!viewModel.intervener.isViewModelCollected) continue
            closed(viewModelId)
            left--
        }
        return left
    }

    private companion object {
        const val minPruneFrom = 16
    }
}

/**
 * This string cut to [length] characters, followed by a marker saying how many were left out; this
 * string itself when it fits. Never cuts a surrogate pair in two.
 */
private fun String.cutAt(length: Int): String {
    if (this.length <= length) return this
    var end = length
    if (end > 0 && this[end - 1].isHighSurrogate()) end--
    return substring(0, end) + "… (${this.length - end} more characters)"
}

/** The delivery kind [value] picked, when it is a Signal. */
private fun deliveryOf(kind: VIEObjectKind, value: Any): VIESignalDelivery? =
    if (kind != VIEObjectKind.Signal) null
    else when (value) {
        is VIE.Signal.Effect -> VIESignalDelivery.Effect
        is VIE.Signal.Event -> VIESignalDelivery.Event
        else -> null
    }

