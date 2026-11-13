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
import io.ktor.websocket.FrameTooBigException
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
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
 * A connection's first frame must be a text frame holding a [TimeMachineClientMessage.Hello], within
 * [handshakeTimeout]; otherwise the connection is closed with a reason saying what arrived instead,
 * and a first frame that does not decode is printed. Every later text frame that does not decode, or
 * is a further `Hello`, is printed and skipped; a later non-text frame is ignored. A skipped frame
 * whose JSON object still carries a numeric `sequence` is posted as a [TimeMachineEvent.Skipped], so
 * that its probe, acknowledged, does not wait on it.
 *
 * A frame larger than [maxFrameSize] bytes, first or later, closes the connection before it is read:
 * the probe is told it was too big, and the closing is printed. The default matches the default of
 * [org.kodein.arch.vie.timemachine.server.store.Limits.maxChars]: a frame of that many bytes holds at
 * most that many characters, so the cap refuses no frame a timeline would keep.
 */
class TimeMachineServer(
    private val store: TimeMachineStore,
    private val connections: TimeMachineConnections,
    private val port: Int = TimeMachineConnection.port,
    private val pingPeriod: Duration = 5.seconds,
    private val timeout: Duration = 10.seconds,
    private val handshakeTimeout: Duration = 10.seconds,
    private val maxFrameSize: Long = 32L * 1024 * 1024,
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
                    maxFrameSize = this@TimeMachineServer.maxFrameSize
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
            val hello = handshake(id) ?: return
            store.post(TimeMachineEvent.HelloReceived(id, hello, Clock.System.now()))
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                when (val message = decode(text)) {
                    null -> {
                        val sequence = sequenceIn(text)
                        val acknowledged = if (sequence == null) "" else " at sequence ${sequence.value}, acknowledged"
                        println("TimeMachine: skipped an undecodable ${text.length}-character frame on connection ${id.value}$acknowledged: ${text.excerpt()}")
                        sequence?.let { store.post(TimeMachineEvent.Skipped(id, it)) }
                    }
                    is TimeMachineClientMessage.Hello -> println("TimeMachine: skipped a further hello on connection ${id.value}")
                    else -> store.post(TimeMachineEvent.MessageReceived(id, message, text.length, Clock.System.now()))
                }
            }
        } catch (e: FrameTooBigException) {
            // Ktor already closed the connection, telling the probe why.
            println("TimeMachine: closed connection ${id.value} on a ${e.frameSize}-byte frame, over the $maxFrameSize-byte cap")
        } finally {
            store.post(TimeMachineEvent.Disconnected(id, Clock.System.now()))
            connections.unregister(id)
        }
    }

    /**
     * The connection's first frame, when it is a hello. Otherwise `null`, and the connection is
     * closed with a reason saying what arrived instead, unless the probe already left. A probe that
     * does not answer that close in time has its connection cancelled, as [TimeMachineConnections.close]
     * does.
     *
     * @throws FrameTooBigException when the first frame is over [maxFrameSize].
     */
    private suspend fun DefaultWebSocketServerSession.handshake(id: ConnectionId): TimeMachineClientMessage.Hello? {
        // A closed channel is a probe that left: there is no one to tell. A frame over the cap closes it
        // too, and is reported as a later one would be.
        val frame = withTimeoutOrNull(handshakeTimeout) { incoming.receiveCatching() }?.let { result ->
            (result.exceptionOrNull() as? FrameTooBigException)?.let { throw it }
            result.getOrNull() ?: return null
        }
        val reason = when (frame) {
            null -> "Expected a hello within $handshakeTimeout"
            !is Frame.Text -> "Expected a hello, got a ${frame.frameType.name.lowercase()} frame"
            else -> {
                val text = frame.readText()
                when (val message = decode(text)) {
                    is TimeMachineClientMessage.Hello -> return message
                    null -> {
                        println("TimeMachine: closed connection ${id.value} on an undecodable ${text.length}-character first frame: ${text.excerpt()}")
                        "Expected a hello, got an undecodable frame"
                    }
                    else -> "Expected a hello, got a ${message::class.simpleName}"
                }
            }
        }
        connections.close(this, CloseReason(CloseReason.Codes.VIOLATED_POLICY, reason))
        return null
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

/**
 * The `sequence` of a frame that does not decode, read leniently: `null` unless it is a JSON
 * object holding a whole number there.
 */
internal fun sequenceIn(text: String): TimeMachineSequence? =
    try {
        ((TimeMachineJson.parseToJsonElement(text) as? JsonObject)?.get("sequence") as? JsonPrimitive)
            ?.takeUnless { it.isString }?.longOrNull?.let(::TimeMachineSequence)
    } catch (_: Exception) {
        null
    }
