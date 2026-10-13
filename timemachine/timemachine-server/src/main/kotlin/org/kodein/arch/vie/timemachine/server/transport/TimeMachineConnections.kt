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
 *
 * @param scope Where the close deadlines run.
 * @param closeTimeout How long a [Command.Close]d connection has to end before it is cancelled: ktor
 *   stops pinging a closing session, and a half-open peer never answers the close.
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
            is Command.Close -> {
                val session = sessions[command.connection] ?: return
                session.outgoing.trySend(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "")))
                scope.launch {
                    delay(closeTimeout)
                    if (sessions[command.connection] === session) session.cancel()
                }
            }
        }
    }
}
