package org.kodein.arch.vie.timemachine.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult


/**
 * The probe's connection to the inspector: the WebSocket to the inspector, retried for as long as
 * [run] runs.
 *
 * The connection loop connects, sends [TimeMachineClientMessage.Hello] and waits for the inspector's
 * answer. Once welcomed, it streams the outbound queue and answers the inspector's requests until
 * the connection breaks, then retries. A rejection is retried too: the inspector may be restarted
 * with a compatible version. So is an inspector that does not answer the handshake within
 * `handshakeTimeout`.
 *
 * It knows the probe through three things only: the `session` it says hello with, the `sink` whose
 * live ViewModels it describes and intervenes in, and the `outbox` it drains.
 */
internal class InspectorConnection(
    private val config: TimeMachineConfig,
    private val session: TimeMachineSession,
    private val sink: TimeMachineSink,
    private val outbox: TimeMachineOutbox,
    private val firstRetryDelay: Duration = 500.milliseconds,
    private val maxRetryDelay: Duration = 5.seconds,
    private val quietAfter: Duration = 1.minutes,
    private val quietRetryDelay: Duration = 30.seconds,
    private val heldAfter: Duration = 5.seconds,
    private val pingInterval: Duration = 5.seconds,
    private val handshakeTimeout: Duration = 10.seconds,
    private val notice: (String) -> Unit = ::println,
) : AutoCloseable {

    // The connection is pinged every `pingInterval`, and closed when a pong is late, so that a
    // half-open connection — a phone whose wifi dropped — is not streamed into for minutes. Browsers
    // expose no WebSocket pings: on the web, the probe relies on the browser to notice.
    // Ktor closes the session when a pong takes longer than twice the interval.
    private val http = HttpClient(timeMachineEngine) { install(WebSockets) { pingInterval = this@InspectorConnection.pingInterval } }

    private val address = config.inspectorAddress

    /**
     * Runs the connection loop, until cancelled.
     *
     * A connection holds once it has lasted `heldAfter`. It is announced then, not on its welcome, and
     * only then does it end the [Outage], which decides how long to wait before each retry and what is
     * worth printing: an inspector that fails on what the probe sends first drops every connection
     * right after welcoming it, since the probe sends that first again each time.
     *
     * An `Exception` never leaves it: it is a failed [Attempt]. An `Error` does, and is not retried:
     * see [TimeMachineProbe], which it retires.
     */
    suspend fun run(): Nothing {
        var outage = outage()
        while (true) {
            val end = attempt()
            // A connection that held ends the outage: its own end is the first failure of the next one.
            if (end.held) outage = outage()
            delay(outage.failed(end))
        }
    }

    /** Closes the HTTP client, and with it a connection [run] would still hold. */
    override fun close() {
        http.close()
    }

    private fun outage() = Outage(TimeSource.Monotonic.markNow(), address, firstRetryDelay, maxRetryDelay, quietAfter, quietRetryDelay, notice)

    /** One connection to the inspector, from its opening to its end. Prints nothing but the
     *  connection itself, once it holds: what its end is worth printing is the [Outage]'s to tell. */
    private suspend fun attempt(): Attempt {
        var welcomed = false
        var held = false
        var refusal: String? = null
        try {
            http.webSocket(host = config.host, port = config.port, path = TimeMachineConnection.path) {
                refusal = handshake()
                if (refusal != null) return@webSocket
                welcomed = true
                describeLiveViewModels()
                // Set on the stream's own coroutine, and read once the stream has joined it.
                stream(onHeld = {
                    held = true
                    notice("TimeMachine: connected to the inspector at $address")
                })
            }
        } catch (e: Exception) {
            // Only the probe's own cancellation ends the loop: any other one is a lost connection.
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            return if (welcomed) Attempt.Dropped(held, e) else Attempt.Unreachable(e)
        }
        return refusal?.let { Attempt.Refused(it) } ?: Attempt.Dropped(held, cause = null)
    }

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
        val bump = if (theirs >= ours) "the vie-timemachine-client dependency of this app" else "the inspector"
        return "the inspector at $address rejected this probe: it speaks protocol $theirs, this probe speaks protocol $ours. Update $bump"
    }

    /**
     * Each time it is welcomed, the probe describes every live ViewModel with a
     * [TimeMachineClientMessage.ViewModelInfo], since the inspector may have restarted and lost them.
     * Not with a `ViewModelStarted`: a re-announce is not a timeline event, and must not take a
     * position ahead of what the queue still holds — a start reported during an outage included. On
     * the first connection, where every start is still in the queue, it is redundant and harmless.
     */
    private suspend fun DefaultClientWebSocketSession.describeLiveViewModels() {
        sink.liveViewModels.values.forEach { sendMessage(TimeMachineClientMessage.ViewModelInfo(it.info.id, it.info)) }
    }

    /**
     * Streams the outbound queue and answers the inspector, until the connection breaks. Calls
     * [onHeld], from a coroutine of its own, once the connection has lasted `heldAfter`.
     *
     * The inspector acknowledges the timeline messages it applied: the outbound queue keeps a bounded
     * window of them in flight, and sends the unacknowledged ones again on the next connection.
     */
    private suspend fun DefaultClientWebSocketSession.stream(onHeld: () -> Unit) {
        coroutineScope {
            val drain = launch { outbox.drain { send(Frame.Text(it)) } }
            val holding = launch {
                delay(heldAfter)
                onHeld()
            }
            while (true) {
                val message = receiveMessage() ?: break
                guardedSuspending({ "answer $message" }) { answer(message) }
            }
            drain.cancel()
            holding.cancel()
        }
    }

    /**
     * Answers one request of the inspector.
     *
     * An intervention request is handed to its ViewModel's [TimeMachineIntervener], and answered with
     * its result right away: the object itself is reported later, as an observation tagged with the
     * request's identifier, once it actually flows through the ViewModel. A ViewModel no longer alive
     * — closed, or never known — is answered with `UnknownViewModel`.
     */
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
}
