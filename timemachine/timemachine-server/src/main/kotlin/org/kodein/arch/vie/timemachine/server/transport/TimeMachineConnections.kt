package org.kodein.arch.vie.timemachine.server.transport

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.server.store.Command
import org.kodein.arch.vie.timemachine.server.store.ConnectionId
import org.kodein.arch.vie.timemachine.server.store.TransportCommand
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * The live WebSocket connections, by [ConnectionId], and the executor of the store's
 * [TransportCommand]s.
 *
 * [TimeMachineServer] registers a connection before posting any event for it, and unregisters it
 * after posting its disconnect: a command for an id not registered, already gone, is ignored.
 * [close] does not need a registration: it takes the session itself.
 *
 * @param scope Where the close deadlines run.
 * @param closeTimeout How long a closing connection has to end before it is cancelled, be it
 *   [Command.Close]d or a handshake refused: ktor stops pinging a closing session, and a half-open
 *   peer never answers the close.
 */
class TimeMachineConnections(
    private val scope: CoroutineScope,
    private val closeTimeout: Duration = 10.seconds,
) {

    private val sessions = ConcurrentHashMap<ConnectionId, WebSocketSession>()

    fun register(id: ConnectionId, session: WebSocketSession) {
        sessions[id] = session
    }

    fun unregister(id: ConnectionId) {
        sessions.remove(id)
    }

    /**
     * Runs [command]. Never suspends: a session's outgoing channel is unlimited, so a frame queued
     * with `trySend` keeps its order and never waits.
     */
    fun execute(command: TransportCommand) {
        when (command) {
            is Command.Send -> sessions[command.connection]?.outgoing?.trySend(
                Frame.Text(TimeMachineJson.encodeToString(TimeMachineServerMessage.serializer(), command.message))
            )
            is Command.Close -> sessions[command.connection]?.let { close(it, CloseReason(CloseReason.Codes.NORMAL, "")) }
        }
    }

    /**
     * Tells [session]'s peer why it is closed, and cancels the session after [closeTimeout] if it
     * has not ended by then. Works for a session that is not registered, or no longer is: a
     * session that ended makes the cancellation a no-op.
     */
    fun close(session: WebSocketSession, reason: CloseReason) {
        session.outgoing.trySend(Frame.Close(reason))
        scope.launch {
            delay(closeTimeout)
            session.cancel()
        }
    }
}
