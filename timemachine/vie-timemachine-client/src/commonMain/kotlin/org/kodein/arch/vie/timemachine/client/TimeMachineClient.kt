package org.kodein.arch.vie.timemachine.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.update
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult


/**
 * The probe's connection to the inspector: the session of this application process, its sink and
 * outbound queue, and the WebSocket to the inspector, retried for as long as the probe runs.
 *
 * The connection loop connects, sends [TimeMachineClientMessage.Hello] and waits for the inspector's
 * answer. Once welcomed, it streams the outbound queue and answers the inspector's requests until
 * the connection breaks, then retries with a capped exponential backoff. A rejection is printed —
 * once, not on every retry — and retried too: the inspector may be restarted with a compatible
 * version. So is an inspector that does not answer the handshake within `handshakeTimeout`.
 *
 * The connection is pinged every `pingInterval`, and closed when a pong is late, so that a
 * half-open connection — a phone whose wifi dropped — is not streamed into for minutes. Browsers
 * expose no WebSocket pings: on the web, the probe relies on the browser to notice.
 *
 * The inspector acknowledges the timeline messages it applied: the outbound queue keeps a bounded
 * window of them in flight, and sends the unacknowledged ones again on the next connection.
 *
 * An intervention request is handed to its ViewModel's [TimeMachineIntervener], and answered with
 * its result right away: the object itself is reported later, as an observation tagged with the
 * request's identifier, once it actually flows through the ViewModel. A ViewModel no longer alive
 * — closed, or never known — is answered with `UnknownViewModel`.
 *
 * After a reconnect, every live ViewModel is described again with a
 * [TimeMachineClientMessage.ViewModelInfo], since the inspector may have restarted and lost them.
 * Not with a `ViewModelStarted`: a re-announce is not a timeline event, and must not take a
 * position ahead of what the queue still holds — a start reported during the outage included. Nor
 * on the first connection: everything since the start is still in the queue.
 *
 * Never throws into the host app: every failure is printed and swallowed.
 */
internal class TimeMachineClient(
    private val config: TimeMachineConfig,
    private val firstRetryDelay: Duration = 500.milliseconds,
    private val maxRetryDelay: Duration = 5.seconds,
    private val pingInterval: Duration = 5.seconds,
    private val handshakeTimeout: Duration = 10.seconds,
    window: Int = 256 * 1024,
) {

    val session: TimeMachineSession = TimeMachineSession(id = Uuid.random(), name = config.appName)

    private val outbox = TimeMachineOutbox(config.bufferSize, window)

    val sink: TimeMachineSink = TimeMachineSink(outbox::offer)

    // The handler is the backstop behind "never throws into the host app": without it, a failure the
    // connection loop does not catch would reach the thread's uncaught-exception handler, which on
    // Android kills the app.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
        println("TimeMachine: the connection to the inspector stopped for good: $e")
    })

    // Ktor closes the session when a pong takes longer than twice the interval.
    private val http = HttpClient(timeMachineEngine) { install(WebSockets) { pingInterval = this@TimeMachineClient.pingInterval } }

    private val address = "ws://${config.host}:${config.port}${TimeMachineConnection.path}"

    // Roles depend only on their type, and building one is a reflective lookup on the thread that builds
    // the ViewModel: it is done once per type. Copy-on-write, like the sink's registry.
    private val roles = AtomicReference<Map<KType, VIERole<*>>>(emptyMap())

    @Suppress("UNCHECKED_CAST")
    fun <T> role(type: KType): VIERole<T> {
        roles.load()[type]?.let { return it as VIERole<T> }
        // Two threads may both build it; the roles are equivalent, and the last one stored wins.
        val role = VIERole.of<T>(type)
        roles.update { it + (type to role) }
        return role
    }

    fun <Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> middleware(
        model: KType,
        intent: KType,
        signal: KType,
    ): TimeMachineMiddleware<Model, Intent, Signal> =
        TimeMachineMiddleware(role(model), role(intent), role(signal), sink)

    fun start() {
        printBanner()
        scope.launch { connectForever() }
    }

    fun stop() {
        scope.cancel()
        outbox.close()
        http.close()
    }

    private suspend fun connectForever() {
        var welcomedBefore = false
        var failures = 0
        var lastProblem: String? = null
        while (true) {
            var connected = false
            val problem: String = try {
                var rejection: String? = null
                http.webSocket(host = config.host, port = config.port, path = TimeMachineConnection.path) {
                    rejection = handshake()
                    if (rejection != null) return@webSocket
                    failures = 0
                    lastProblem = null
                    connected = true
                    println("TimeMachine: connected to the inspector at $address")
                    if (welcomedBefore) {
                        sink.liveViewModels.values.forEach { sendMessage(TimeMachineClientMessage.ViewModelInfo(it.info.id, it.info)) }
                    }
                    welcomedBefore = true
                    stream()
                }
                rejection ?: "the inspector at $address closed the connection"
            } catch (e: Exception) {
                // Only the probe's own cancellation ends the loop: any other one is a lost connection.
                if (e is CancellationException) currentCoroutineContext().ensureActive()
                if (connected) "lost the connection to the inspector at $address ($e)"
                else "cannot reach the inspector at $address ($e)"
            }
            if (problem != lastProblem) {
                println("TimeMachine: $problem; retrying.")
                lastProblem = problem
            }
            delay(retryDelay(failures++))
        }
    }

    private fun retryDelay(failures: Int): Duration =
        if (failures >= 30) maxRetryDelay
        else (firstRetryDelay * (1 shl failures).toDouble()).coerceAtMost(maxRetryDelay)

    /** Sends [TimeMachineClientMessage.Hello] and waits for the answer: `null` when welcomed, the
     *  rejection to print otherwise. */
    private suspend fun DefaultClientWebSocketSession.handshake(): String? {
        sendMessage(TimeMachineClientMessage.Hello(session = session, protocolVersion = TimeMachineConnection.protocolVersion))
        return try {
            withTimeout(handshakeTimeout) { awaitAnswer() }
        } catch (_: TimeoutCancellationException) {
            // Caught here for its message: the loop would otherwise report it as a lost connection.
            "the inspector at $address did not answer the handshake within $handshakeTimeout"
        }
    }

    private suspend fun DefaultClientWebSocketSession.awaitAnswer(): String? {
        while (true) {
            return when (val answer = receiveMessage() ?: return "the inspector at $address closed the connection during the handshake") {
                TimeMachineServerMessage.Welcome -> null
                is TimeMachineServerMessage.Rejected -> rejectionNotice(answer)
                else -> continue
            }
        }
    }

    private fun rejectionNotice(rejected: TimeMachineServerMessage.Rejected): String {
        val ours = TimeMachineConnection.protocolVersion
        val theirs = rejected.protocolVersion
        return when (rejected.reason) {
            TimeMachineServerMessage.Rejected.Reason.IncompatibleProtocol -> {
                val bump = if (theirs.value > ours.value) "the vie-timemachine-client dependency of this app" else "the inspector"
                "the inspector at $address rejected this probe: it speaks protocol $theirs, this probe speaks protocol $ours. Update $bump"
            }
            TimeMachineServerMessage.Rejected.Reason.DuplicateSession ->
                "the inspector at $address rejected this probe: session ${session.id} is already connected"
            TimeMachineServerMessage.Rejected.Reason.Unknown ->
                "the inspector at $address rejected this probe for a reason this version does not know: it speaks protocol $theirs, this probe speaks protocol $ours. Update the vie-timemachine-client dependency of this app"
        }
    }

    /** Streams the outbound queue and answers the inspector, until the connection breaks. */
    private suspend fun DefaultClientWebSocketSession.stream() {
        coroutineScope {
            val drain = launch { outbox.drain { send(Frame.Text(it)) } }
            while (true) {
                val message = receiveMessage() ?: break
                guardedSuspending({ "answer $message" }) { answer(message) }
            }
            drain.cancel()
        }
    }

    private suspend fun DefaultClientWebSocketSession.answer(message: TimeMachineServerMessage) {
        when (message) {
            is TimeMachineServerMessage.RequestViewModelInfo ->
                sendMessage(TimeMachineClientMessage.ViewModelInfo(message.viewModelId, sink.liveViewModels[message.viewModelId]?.info))
            is TimeMachineServerMessage.Intervene -> {
                val live = sink.liveViewModels[message.viewModelId]
                val result = live?.intervener?.intervene(message)
                    ?: VIEInterventionResult.Failure(VIEInterventionResult.Reason.UnknownViewModel, "No live ViewModel ${message.viewModelId}")
                sendMessage(TimeMachineClientMessage.InterventionResult(message.requestId, result))
            }
            is TimeMachineServerMessage.Received -> outbox.acknowledge(message.sequence)
            TimeMachineServerMessage.Welcome, is TimeMachineServerMessage.Rejected -> {}
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendMessage(message: TimeMachineClientMessage) {
        send(Frame.Text(TimeMachineJson.encodeToString(TimeMachineClientMessage.serializer(), message)))
    }

    /** The next message from the inspector, skipping frames that do not decode; `null` once the
     *  connection is closed. */
    private suspend fun DefaultClientWebSocketSession.receiveMessage(): TimeMachineServerMessage? {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val text = frame.readText()
            return guarded({ "decode a ${text.length}-character frame: ${text.excerpt()}" }) { TimeMachineJson.decodeFromString(TimeMachineServerMessage.serializer(), text) } ?: continue
        }
        return null
    }

    /** The start of a frame: an `Intervene` frame carries a whole object, of any size. */
    private fun String.excerpt(): String = if (length <= 100) this else "${take(100)}…"

    private fun printBanner() {
        val lines = listOf(
            "TimeMachine probe started for \"${config.appName}\"",
            "Inspector: $address",
            "Keeps up to ${config.bufferSize} messages the inspector has not taken yet.",
            "",
            "This probe streams every Model, Intent and Signal of this app",
            "in cleartext, and lets the inspector drive its ViewModels.",
            "Never enable it in a release build.",
        )
        val width = lines.maxOf { it.length }
        val border = "─".repeat(width + 2)
        println(buildString {
            appendLine("┌$border┐")
            lines.forEach { appendLine("│ ${it.padEnd(width)} │") }
            append("└$border┘")
        })
    }
}
