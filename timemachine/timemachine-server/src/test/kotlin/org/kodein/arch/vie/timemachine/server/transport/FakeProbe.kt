package org.kodein.arch.vie.timemachine.server.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.cio.webSocketRawSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    /** Opens a raw session: it answers neither pings nor the inspector's close, as a half-open peer would not. */
    suspend fun halfOpen(): HalfOpen =
        HalfOpen(http.webSocketRawSession(host = "localhost", port = port, path = TimeMachineConnection.path))

    /** Connects and says hello as [session], expecting a welcome, then reads nothing more: see [halfOpen]. */
    suspend fun welcomedHalfOpen(session: TimeMachineSession): HalfOpen = halfOpen().apply {
        send(TimeMachineClientMessage.Hello(session, TimeMachineConnection.protocolVersion))
        check(receive() == TimeMachineServerMessage.Welcome)
    }

    override fun close() {
        http.close()
    }

    class HalfOpen(private val raw: ClientWebSocketSession) {

        suspend fun send(message: TimeMachineClientMessage) {
            raw.send(Frame.Text(TimeMachineJson.encodeToString(TimeMachineClientMessage.serializer(), message)))
        }

        /** The next message, skipping control frames, or `null` once the inspector sent its close. */
        suspend fun receive(): TimeMachineServerMessage? {
            for (frame in raw.incoming) {
                when (frame) {
                    is Frame.Text -> return TimeMachineJson.decodeFromString(TimeMachineServerMessage.serializer(), frame.readText())
                    is Frame.Close -> return null
                    else -> Unit
                }
            }
            return null
        }

        /** The reason the inspector gave when closing the connection; fails on any text frame received before it. */
        suspend fun closeReason(): String? {
            for (frame in raw.incoming) {
                when (frame) {
                    is Frame.Close -> return frame.readReason()?.message
                    is Frame.Text -> fail("Expected the close, got ${frame.readText()}")
                    else -> Unit
                }
            }
            fail("Expected the close, got the end of the stream")
        }

        /**
         * Returns once the inspector has cut the connection. A raw peer sees a cut only by writing to
         * it, so this writes pongs: a text frame would unblock the inspector's `incoming` after a ping
         * period, a ping would fail its ponger, and a pong is ignored, so it hides no leak. `trySend`
         * throws nothing, so no `runCatching` swallows the cancellation of a surrounding timeout.
         */
        suspend fun awaitCut() {
            while (raw.outgoing.trySend(Frame.Pong(ByteArray(0))).isSuccess) delay(50.milliseconds)
        }
    }

    class Connection(private val session: DefaultClientWebSocketSession) {

        suspend fun sendText(text: String) {
            session.send(Frame.Text(text))
        }

        suspend fun sendBinary(bytes: ByteArray) {
            session.send(Frame.Binary(true, bytes))
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

        /** The reason the inspector gave when closing the connection; fails on any message received before it. */
        suspend fun closeReason(): String? {
            receive()?.let { fail("Expected the close, got $it") }
            return session.closeReason.await()?.message
        }

        /** The next message other than a [TimeMachineServerMessage.Received]. */
        suspend fun receiveRequest(): TimeMachineServerMessage? {
            while (true) {
                val message = receive()
                if (message !is TimeMachineServerMessage.Received) return message
            }
        }

        suspend fun close() {
            session.close()
        }

        /** Drops the connection without a close handshake, as a dead peer would. */
        fun abort() {
            session.cancel()
        }
    }
}
