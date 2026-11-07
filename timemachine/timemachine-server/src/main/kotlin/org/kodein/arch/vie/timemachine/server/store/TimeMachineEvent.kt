package org.kodein.arch.vie.timemachine.server.store

import kotlinx.serialization.json.JsonElement
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import kotlin.time.Instant
import kotlin.uuid.Uuid


/**
 * Identifies one WebSocket connection. Opaque to the store: the transport assigns it, and it never
 * outlives the transport's process.
 */
@JvmInline
value class ConnectionId(val value: Long)

/**
 * Every input of the store. Each one that depends on the clock carries the time it happened at as
 * `now`, so that [TimeMachineState.on] stays a pure function.
 */
sealed interface TimeMachineEvent {

    /** The first frame of [connection], decoded as a [TimeMachineClientMessage.Hello]. */
    data class HelloReceived(
        val connection: ConnectionId,
        val hello: TimeMachineClientMessage.Hello,
        val now: Instant,
    ) : TimeMachineEvent

    /**
     * A later frame of [connection].
     *
     * @property length The frame's length in characters: what the message weighs in its ViewModel's bound.
     */
    data class MessageReceived(
        val connection: ConnectionId,
        val message: TimeMachineClientMessage,
        val length: Int,
        val now: Instant,
    ) : TimeMachineEvent

    /** [connection] ended, whoever ended it. */
    data class Disconnected(
        val connection: ConnectionId,
        val now: Instant,
    ) : TimeMachineEvent

    /** Removes a disconnected session, or only the closed ViewModels of a connected one. */
    data class ClearSession(val sessionId: Uuid) : TimeMachineEvent

    /** Removes every disconnected session. */
    data object ClearDisconnected : TimeMachineEvent

    /** Hides the rejection of [sessionId], now and after any later attempt. */
    data class DismissRejection(val sessionId: Uuid) : TimeMachineEvent

    /**
     * Asks ViewModel [viewModelId] of session [sessionId] to emit or receive an object, as a
     * [TimeMachineServerMessage.Intervene] carrying [requestId]. Nothing happens when the store no
     * longer holds that session or ViewModel.
     */
    data class Intervene(
        val sessionId: Uuid,
        val viewModelId: Uuid,
        val kind: VIEObjectKind,
        val typeName: String,
        val asJson: JsonElement,
        val requestId: Uuid,
        val now: Instant,
    ) : TimeMachineEvent

    /** The deadline of intervention [requestId] passed. */
    data class DeadlinePassed(val requestId: Uuid, val now: Instant) : TimeMachineEvent

    /** The WebSocket listener changed state. */
    data class ListenerChanged(val listener: ListenerState) : TimeMachineEvent
}

/** What the core asks for: a [TransportCommand], or a deadline the store runs itself. */
sealed interface Command {

    /** Sends [message] on [connection], after everything sent on it before. */
    data class Send(val connection: ConnectionId, val message: TimeMachineServerMessage) : TransportCommand

    /** Closes [connection], after everything sent on it before. */
    data class Close(val connection: ConnectionId) : TransportCommand

    /**
     * Starts the deadline of intervention [requestId]: once the store's intervention timeout has
     * passed, it posts a [TimeMachineEvent.DeadlinePassed].
     */
    data class StartDeadline(val requestId: Uuid) : Command
}

/** What the store hands the transport to do. */
sealed interface TransportCommand : Command

/** The outcome of an event: the next state, and the commands to execute, in order. */
data class Transition(val state: TimeMachineState, val commands: List<Command> = emptyList())
