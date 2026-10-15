package org.kodein.arch.vie.timemachine.server.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
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
 * batch the core throws on is applied again event by event, without the events it throws on; a
 * command [execute] throws on is skipped. Each failure is reported, and the writer goes on.
 *
 * @param maxRows How many rows each ViewModel keeps.
 * @param maxChars How many characters of frame text each ViewModel keeps.
 * @param interventionTimeout How long an intervention waits for its result or its tagged row before
 *   its deadline passes.
 * @param reduce The core: [TimeMachineState.on]. Replaced only by tests.
 */
class TimeMachineStore(
    private val scope: CoroutineScope,
    maxRows: Int = 1_000,
    maxChars: Long = 32L * 1024 * 1024,
    private val interventionTimeout: Duration = 5.seconds,
    private val reduce: TimeMachineState.(List<TimeMachineEvent>) -> Transition = { on(it) },
    private val execute: (TransportCommand) -> Unit,
) {

    private val events = Channel<TimeMachineEvent>(Channel.UNLIMITED)

    private val mutableState = MutableStateFlow(TimeMachineState(limits = Limits(maxRows, maxChars)))

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
                        } catch (e: Exception) {
                            report("skipped a command it failed to execute: $command", e)
                        }
                        is Command.StartDeadline -> scope.launch {
                            delay(interventionTimeout)
                            post(TimeMachineEvent.DeadlinePassed(command.requestId, Clock.System.now()))
                        }
                    }
                }
            }
        }
    }

    /**
     * [batch] applied to [state]; when the core throws, its events applied one at a time, without
     * those it throws on. A skipped frame is not held, its sequence stays missing; a later frame's
     * acknowledgement still covers it, so its probe is not stalled.
     */
    private fun apply(state: TimeMachineState, batch: List<TimeMachineEvent>): Transition = try {
        state.reduce(batch)
    } catch (_: Exception) {
        batch.fold(Transition(state)) { transition, event ->
            try {
                transition.state.reduce(listOf(event)).let { Transition(it.state, transition.commands + it.commands) }
            } catch (e: Exception) {
                // Only its type: a frame can be large.
                report("skipped an event it failed to apply: ${event::class.simpleName}", e)
                transition
            }
        }
    }

    private fun report(message: String, e: Exception) {
        println("TimeMachine: $message")
        e.printStackTrace()
    }

    /** Queues [event]. Never suspends: events from any thread are applied in the order posted. */
    fun post(event: TimeMachineEvent) {
        events.trySend(event)
    }

    /**
     * Asks ViewModel [viewModelId] of session [sessionId] to emit or receive the object [asJson], and
     * returns the request's identifier, under which [state] records the intervention. When the store no
     * longer holds that session or ViewModel by the time the request is applied, nothing is recorded,
     * and the identifier never appears in [state].
     */
    fun intervene(sessionId: Uuid, viewModelId: Uuid, kind: VIEObjectKind, typeName: String, asJson: JsonElement): Uuid {
        val requestId = Uuid.random()
        post(TimeMachineEvent.Intervene(sessionId, viewModelId, kind, typeName, asJson, requestId, Clock.System.now()))
        return requestId
    }
}
