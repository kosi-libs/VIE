package org.kodein.arch.vie.timemachine.server.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession


/** A hand-rolled probe on a raw ktor client: sends exactly what a test says, and nothing else. */
internal class FakeProbe(private val port: Int) : AutoCloseable {

    private val http = HttpClient(CIO) { install(WebSockets) }

    suspend fun connect(): Connection = Connection(http.webSocketSession(host = "localhost", port = port, path = TimeMachineConnection.path))

    /** Connects and says hello as [session], expecting a welcome. */
    suspend fun welcomed(session: TimeMachineSession): Connection = connect().apply {
        send(TimeMachineClientMessage.Hello(session, TimeMachineConnection.protocolVersion))
        check(receive() == TimeMachineServerMessage.Welcome)
    }

    override fun close() {
        http.close()
    }

    class Connection(private val session: DefaultClientWebSocketSession) {

        suspend fun sendText(text: String) {
            session.send(Frame.Text(text))
        }

        suspend fun send(message: TimeMachineClientMessage) {
            sendText(TimeMachineJson.encodeToString(TimeMachineClientMessage.serializer(), message))
        }

        /** The next message, or `null` once the inspector closed the connection. */
        suspend fun receive(): TimeMachineServerMessage? {
            for (frame in session.incoming) {
                if (frame is Frame.Text) return TimeMachineJson.decodeFromString(TimeMachineServerMessage.serializer(), frame.readText())
            }
            return null
        }

        /** The next message other than a [TimeMachineServerMessage.Received]. */
        suspend fun receiveRequest(): TimeMachineServerMessage? {
            while (true) {
                val message = receive()
                if (message !is TimeMachineServerMessage.Received) return message
            }
        }

        /** Whatever arrives within [timeout]: `null` when nothing does. */
        suspend fun receiveWithin(timeout: Duration = 200.milliseconds): TimeMachineServerMessage? =
            withTimeoutOrNull(timeout) { receive() }

        suspend fun close() {
            session.close()
        }

        /** Drops the connection without a close handshake, as a dead peer would. */
        fun abort() {
            session.cancel()
        }
    }
}
