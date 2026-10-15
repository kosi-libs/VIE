package org.kodein.arch.vie.timemachine.client

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.update
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
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
 * @param outlet Receives every entry, in report order for any single calling thread. Must not
 *   throw nor suspend.
 */
internal class TimeMachineSink(private val outlet: (Entry) -> Unit) {

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

    /** A ViewModel started and not closed yet. */
    class LiveViewModel(val info: VIEViewModelInfo, val intervener: TimeMachineIntervener)

    private val live = AtomicReference<Map<Uuid, LiveViewModel>>(emptyMap())

    /** The ViewModels started and not closed yet, by identifier. */
    val liveViewModels: Map<Uuid, LiveViewModel> get() = live.load()

    /** Reports that the ViewModel described by [info] started, and that [intervener] intervenes in it. */
    fun started(info: VIEViewModelInfo, intervener: TimeMachineIntervener) {
        live.update { it + (info.id to LiveViewModel(info, intervener)) }
        val at = Clock.System.now()
        outlet(Entry(info.id) { TimeMachineClientMessage.ViewModelStarted(info = info, at = at, sequence = it) })
    }

    /**
     * Reports an object observed flowing through the ViewModel [viewModelId].
     *
     * @param interventionId The request that placed the object, or `null` if the app produced it.
     */
    fun <T : Any> observed(viewModelId: Uuid, kind: VIEObjectKind, role: VIERole<T>, value: T, interventionId: Uuid? = null) {
        val at = Clock.System.now()
        outlet(Entry(viewModelId) { sequence ->
            val asString = try {
                value.toString()
            } catch (e: Throwable) {
                "<toString() failed: $e>"
            }
            val observation = VIEObservation(
                viewModelId = viewModelId,
                at = at,
                kind = kind,
                typeName = describing({ "name $kind $asString" }) { role.typeNameOf(value) } ?: role.typeName,
                asString = asString,
                asJson = describing({ "encode $kind $asString" }) { role.encode(value) },
                interventionId = interventionId,
                delivery = deliveryOf(kind, value),
            )
            TimeMachineClientMessage.Observed(observation = observation, sequence = sequence)
        })
    }

    /** Reports that the ViewModel [viewModelId] was closed. */
    fun closed(viewModelId: Uuid) {
        live.update { it - viewModelId }
        val at = Clock.System.now()
        outlet(Entry(viewModelId) { TimeMachineClientMessage.ViewModelClosed(viewModelId = viewModelId, at = at, sequence = it) })
    }
}

/** The delivery kind [value] picked, when it is a Signal. */
private fun deliveryOf(kind: VIEObjectKind, value: Any): VIESignalDelivery? =
    if (kind != VIEObjectKind.Signal) null
    else when (value) {
        is VIE.Signal.Effect -> VIESignalDelivery.Effect
        is VIE.Signal.Event -> VIESignalDelivery.Event
        else -> null
    }

/** Runs [block], app code describing an object, printing and swallowing anything it throws:
 *  unlike [guarded], errors and cancellation too, since they cannot come from the probe itself. */
private inline fun <R> describing(what: () -> String, block: () -> R): R? =
    try {
        block()
    } catch (e: Throwable) {
        println("TimeMachine: failed to ${what()}: $e")
        null
    }

/** Makes one live ViewModel emit or receive the object an intervention request describes. */
internal fun interface TimeMachineIntervener {
    /** Places [request]'s object, or says why it cannot. Never throws nor suspends. */
    fun intervene(request: TimeMachineServerMessage.Intervene): VIEInterventionResult
}

/** Runs [block], printing and swallowing any failure but cancellation: nothing the probe does may
 *  throw into the host app. For a [block] that suspends, use [guardedSuspending]. */
internal inline fun <R> guarded(what: () -> String, block: () -> R): R? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("TimeMachine: failed to ${what()}: $e")
        null
    }

/** Like [guarded] for a [block] that suspends: a `CancellationException` ends the caller only if the
 *  caller itself was cancelled, otherwise it is a failure of [block] like any other. */
internal suspend inline fun <R> guardedSuspending(what: () -> String, block: () -> R): R? =
    try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) currentCoroutineContext().ensureActive()
        println("TimeMachine: failed to ${what()}: $e")
        null
    }
