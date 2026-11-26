package org.kodein.arch.vie.timemachine.server.store

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import org.kodein.arch.vie.timemachine.protocol.TimeMachineTimelineMessage
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid


/**
 * The inspector's store: one coroutine in [scope] applies the [post]ed events in order, through the
 * pure [TimeMachineState.on], so no lock is needed anywhere.
 *
 * The events waiting when the writer wakes are applied as one batch: [state] is published once per
 * batch, then each [TransportCommand] is handed, in order, to [execute], which must not suspend. The
 * store runs each [Command.StartDeadline] itself, so the core stays free of clocks.
 *
 * A bug must not stop the writer: no `Received` would be sent again, and every probe would stall. A
 * batch the core throws on is applied again event by event, each acknowledged on its own, without
 * the events it throws on; a command [execute] throws on is skipped. Each failure is reported, and
 * the writer goes on, whatever they throw, an `Error` included.
 *
 * Should the writer stop all the same, on a failure outside those guards or because [scope] was
 * cancelled, [state] says so in [TimeMachineState.writer], and [post] drops every later event: no
 * probe is acknowledged any more, but nothing piles up either.
 *
 * @param limits How much timeline each ViewModel keeps.
 * @property interventionTimeout How long an intervention waits for its result or its tagged row before
 *   its deadline passes.
 * @param reduce The core: [TimeMachineState.on]. Replaced only by tests.
 */
class TimeMachineStore(
    private val scope: CoroutineScope,
    limits: Limits = Limits(),
    val interventionTimeout: Duration = 5.seconds,
    private val reduce: TimeMachineState.(List<TimeMachineEvent>) -> Transition = { on(it) },
    private val execute: (TransportCommand) -> Unit,
) {

    private val events = Channel<TimeMachineEvent>(Channel.UNLIMITED)

    private val mutableState = MutableStateFlow(TimeMachineState(limits = limits))

    val state: StateFlow<TimeMachineState> = mutableState.asStateFlow()

    init {
        scope.launch {
            for (first in events) {
                val batch = buildList {
                    add(first)
                    while (true) add(events.tryReceive().getOrNull() ?: break)
                }
                val transition = apply(mutableState.value, batch)
                mutableState.value = transition.state
                transition.commands.forEach { command ->
                    when (command) {
                        is TransportCommand -> try {
                            execute(command)
                        } catch (e: Throwable) { // execute does not suspend: no cancellation to preserve
                            report("skipped a command it failed to execute: ${command.summary()}", e)
                        }
                        is Command.StartDeadline -> scope.launch {
                            delay(interventionTimeout)
                            post(TimeMachineEvent.DeadlinePassed(command.requestId, Clock.System.now()))
                        }
                    }
                }
            }
        }.invokeOnCompletion(::stopped)
    }

    /**
     * Records that the writer stopped, and closes [events]. Runs on whichever thread the writer ended
     * on, and must not throw. The failure is not swallowed: it goes on to [scope]'s handler.
     */
    private fun stopped(cause: Throwable?) {
        // Closes the channel and frees its backlog; close() would keep the unbounded queue nobody reads.
        events.cancel()
        val writer = if (cause == null || cause is CancellationException) WriterState.Stopped else WriterState.Failed(cause.message ?: cause.toString())
        mutableState.update { it.copy(writer = writer) }
        if (writer is WriterState.Failed) println("TimeMachine: the store stopped on a failure, and applies no event any more: ${writer.message}")
    }

    /**
     * [batch] applied to [state]; when the core throws, its events applied one at a time, without
     * those it throws on. A timeline frame it throws on is applied as a [TimeMachineEvent.Skipped]
     * instead: not held, its sequence stays missing, but acknowledged, so that its probe forgets it.
     * Waiting on it, the probe could stall for good, once the frames it waits on fill its window.
     *
     * Each event is then a batch of its own: a timeline frame gets its own `Received`, right after
     * its commands, not one per connection at the end as [TimeMachineState.on] gives a whole batch.
     * Every sequence is still acknowledged, in order, the highest last; the probe keeps only the
     * highest.
     */
    private fun apply(state: TimeMachineState, batch: List<TimeMachineEvent>): Transition = try {
        state.reduce(batch)
    } catch (_: Throwable) { // reduce does not suspend: no cancellation to preserve
        batch.fold(Transition(state)) { transition, event ->
            transition.then(event) ?: run {
                val skipped = event.skipped() ?: return@run transition
                transition.then(skipped) ?: transition
            }
        }
    }

    /** This transition followed by [event], or `null`, reported, when the core throws on it. */
    private fun Transition.then(event: TimeMachineEvent): Transition? = try {
        state.reduce(listOf(event)).let { Transition(it.state, commands + it.commands) }
    } catch (e: Throwable) { // reduce does not suspend: no cancellation to preserve
        report("skipped an event it failed to apply: ${event.summary()}", e)
        null
    }

    /** The [TimeMachineEvent.Skipped] standing for this event, when it delivers a timeline frame. */
    private fun TimeMachineEvent.skipped(): TimeMachineEvent.Skipped? {
        val message = (this as? TimeMachineEvent.MessageReceived)?.message as? TimeMachineTimelineMessage ?: return null
        return TimeMachineEvent.Skipped(connection, message.sequence)
    }

    /** What this command is and where it goes; not its message, as a frame can be large. */
    private fun TransportCommand.summary(): String = when (this) {
        is Command.Send -> "Send of ${message::class.simpleName} on $connection"
        is Command.Close -> "Close of $connection"
    }

    private fun report(message: String, e: Throwable) {
        println("TimeMachine: $message")
        e.printStackTrace()
    }

    /**
     * Queues [event]. Never suspends: events from any thread are applied in the order posted. Dropped
     * once the writer stopped.
     */
    fun post(event: TimeMachineEvent) {
        events.trySend(event)
    }

    /**
     * Asks ViewModel [viewModelId] of session [sessionId] to emit or receive the object [asJson], and
     * returns the request's identifier, under which [state] records the intervention. When the store no
     * longer holds that session or ViewModel by the time the request is applied, nor once the writer
     * stopped, nothing is recorded, and the identifier never appears in [state].
     *
     * The store only checks that it holds the session and the ViewModel. Whether the ViewModel is
     * alive, and whether the type was reported `@Serializable`, is the caller's to check before
     * offering the intervention: the store sends it all the same, and the probe refuses it, which
     * ends it [InterventionStatus.Failed].
     */
    fun intervene(sessionId: Uuid, viewModelId: Uuid, kind: VIEObjectKind, typeName: String, asJson: JsonElement): Uuid {
        val requestId = Uuid.random()
        post(TimeMachineEvent.Intervene(sessionId, viewModelId, kind, typeName, asJson, requestId, Clock.System.now()))
        return requestId
    }
}


/**
 * What this event is: for a frame, its message's class, its connection and, for a timeline frame,
 * its sequence, acknowledged by a [TimeMachineEvent.Skipped] in its place. Never its content: a
 * frame can be large.
 */
internal fun TimeMachineEvent.summary(): String = when (this) {
    is TimeMachineEvent.MessageReceived -> {
        val sequence = (message as? TimeMachineTimelineMessage)?.let { " at sequence ${it.sequence.value}, acknowledged" }.orEmpty()
        "${message::class.simpleName} on connection ${connection.value}$sequence"
    }
    else -> this::class.simpleName.orEmpty()
}
