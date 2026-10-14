package org.kodein.arch.vie.timemachine.client

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertIs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineTimelineMessage


/**
 * The smallest inspector a probe can talk to: answers each connection's `Hello` with [answer], then
 * records every message a welcomed probe sends.
 *
 * Each timeline message is acknowledged with a [TimeMachineServerMessage.Received] while
 * [acknowledge] is `true`, before it is recorded: a test that reacts to a message, by closing the
 * connection for example, does so after its acknowledgement was queued.
 */
internal class FakeInspector(
    private val answer: suspend (connection: Int) -> TimeMachineServerMessage = { TimeMachineServerMessage.Welcome },
) : AutoCloseable {
    val hellos = Channel<TimeMachineClientMessage.Hello>(Channel.UNLIMITED)
    val received = Channel<TimeMachineClientMessage>(Channel.UNLIMITED)
    val welcomed = Channel<DefaultWebSocketServerSession>(Channel.UNLIMITED)

    @Volatile
    var acknowledge: Boolean = true

    private val connections = AtomicInteger(0)

    private val server = embeddedServer(CIO, port = 0) {
        install(WebSockets)
        routing {
            webSocket(TimeMachineConnection.path) {
                val hello = assertIs<TimeMachineClientMessage.Hello>(receiveMessage())
                hellos.send(hello)
                val answer = answer(connections.getAndIncrement())
                send(TimeMachineServerMessage.serializer(), answer)
                if (answer != TimeMachineServerMessage.Welcome) {
                    close()
                    return@webSocket
                }
                welcomed.send(this)
                while (true) {
                    val message = receiveMessage() ?: break
                    if (acknowledge && message is TimeMachineTimelineMessage) {
                        send(TimeMachineServerMessage.serializer(), TimeMachineServerMessage.Received(message.sequence))
                    }
                    received.send(message)
                }
            }
        }
    }.start(wait = false)

    val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }

    override fun close() {
        server.stop(0, 0)
    }
}

internal suspend fun DefaultWebSocketServerSession.receiveMessage(): TimeMachineClientMessage? {
    for (frame in incoming) {
        if (frame is Frame.Text) return TimeMachineJson.decodeFromString(TimeMachineClientMessage.serializer(), frame.readText())
    }
    return null
}

internal suspend fun <T> DefaultWebSocketServerSession.send(serializer: KSerializer<T>, message: T) {
    send(Frame.Text(TimeMachineJson.encodeToString(serializer, message)))
}
