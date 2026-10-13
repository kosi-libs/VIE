package org.kodein.arch.vie.timemachine.server.transport

import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.server.store.ConnectionId
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.store.TimeMachineStore
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * The inspector's WebSocket server: turns each probe connection's frames into [TimeMachineEvent]s
 * posted to [store], whose commands [connections] executes.
 *
 * Bound to all interfaces, so that a probe on a device can reach it. Every connection is pinged
 * every [pingPeriod], and closed when a pong takes longer than [timeout], so that a half-open one is
 * noticed.
 *
 * A connection's first text frame must be a [TimeMachineClientMessage.Hello], within
 * [handshakeTimeout]; otherwise the connection is closed. Every later frame that does not decode, or
 * is a further `Hello`, is printed and skipped.
 */
class TimeMachineServer(
    private val store: TimeMachineStore,
    private val connections: TimeMachineConnections,
    private val port: Int = TimeMachineConnection.port,
    private val pingPeriod: Duration = 5.seconds,
    private val timeout: Duration = 10.seconds,
    private val handshakeTimeout: Duration = 10.seconds,
) {

    private val nextId = AtomicLong()

    /** Held by each attempt to listen, so that two retries cannot build two engines. */
    private val listening = Mutex()

    /** Whether the last attempt failed, read under [listening]: the store's state may not show it yet. */
    private var failed = false

    /**
     * The engine's own coroutines fail on their own, besides [start] throwing: a failed bind is one. Left
     * alone, that reaches the thread's uncaught handler, which `runTest` reports against the next test of
     * the same JVM. [start] reports the failure, so it is only printed here.
     */
    private val engineFailures = CoroutineExceptionHandler { _, e ->
        println("TimeMachine: the server's engine failed")
        e.printStackTrace()
    }

    @Volatile
    private var server = engine()

    private fun engine() = embeddedServer(
        CIO,
        serverConfig {
            parentCoroutineContext = engineFailures
            module {
                install(WebSockets) {
                    pingPeriod = this@TimeMachineServer.pingPeriod
                    timeout = this@TimeMachineServer.timeout
                }
                routing {
                    webSocket(TimeMachineConnection.path) { serve(ConnectionId(nextId.getAndIncrement())) }
                }
            }
        },
    ) {
        connector {
            host = "0.0.0.0"
            port = this@TimeMachineServer.port
        }
    }

    /**
     * Starts listening, and reports the outcome to [store] as a [TimeMachineEvent.ListenerChanged]:
     * never throws, except the cancellation of the calling coroutine, which reports nothing.
     */
    suspend fun start() {
        listening.withLock { listen() }
    }

    /**
     * Listens again after a failure, on a new engine, since one cannot start twice: reports
     * [ListenerState.Starting], then the outcome, as [start] does.
     *
     * Does nothing unless the listener has failed, so a listening server's connections are never
     * cut off.
     */
    suspend fun retry() {
        listening.withLock {
            if (!failed) return
            server.stop(0, 0)
            server = engine()
            store.post(TimeMachineEvent.ListenerChanged(ListenerState.Starting))
            listen()
        }
    }

    private suspend fun listen() {
        try {
            server.startSuspend(wait = false)
        } catch (e: Exception) {
            // A cancellation is reported only when it is not this coroutine's: CIO may throw the bind
            // failure wrapped in one.
            currentCoroutineContext().ensureActive()
            val cause = generateSequence<Throwable>(e) { it.cause }.last()
            failed = true
            store.post(TimeMachineEvent.ListenerChanged(ListenerState.Failed(cause.message ?: cause.toString())))
            return
        }
        failed = false
        // Already complete once start returns; it would never complete after a failure.
        val bound = server.engine.resolvedConnectors().first().port
        store.post(TimeMachineEvent.ListenerChanged(ListenerState.Listening(bound)))
    }

    fun stop() {
        server.stop(0, 0)
    }

    private suspend fun DefaultWebSocketServerSession.serve(id: ConnectionId) {
        connections.register(id, this)
        try {
            val hello = withTimeoutOrNull(handshakeTimeout) { incoming.receiveCatching().getOrNull() }
                ?.let { (it as? Frame.Text)?.readText() }
                ?.let { decode(it) as? TimeMachineClientMessage.Hello }
            if (hello == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Expected a hello within $handshakeTimeout"))
                return
            }
            store.post(TimeMachineEvent.HelloReceived(id, hello, Clock.System.now()))
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                when (val message = decode(text)) {
                    null -> println("TimeMachine: skipped an undecodable ${text.length}-character frame on connection ${id.value}: ${text.excerpt()}")
                    is TimeMachineClientMessage.Hello -> println("TimeMachine: skipped a further hello on connection ${id.value}")
                    else -> store.post(TimeMachineEvent.MessageReceived(id, message, text.length, Clock.System.now()))
                }
            }
        } finally {
            store.post(TimeMachineEvent.Disconnected(id, Clock.System.now()))
            connections.unregister(id)
        }
    }

    private fun decode(text: String): TimeMachineClientMessage? =
        try {
            TimeMachineJson.decodeFromString(TimeMachineClientMessage.serializer(), text)
        } catch (_: Exception) {
            null
        }

    /** The start of a frame: an observation carries a whole object, of any size. */
    private fun String.excerpt(): String = if (length <= 100) this else "${take(100)}…"
}
