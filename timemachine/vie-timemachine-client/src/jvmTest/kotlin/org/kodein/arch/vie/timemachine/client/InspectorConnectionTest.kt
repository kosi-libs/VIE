package org.kodein.arch.vie.timemachine.client

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import io.ktor.websocket.close
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind


/** Its serializer throws an `Error` when decoding, as a custom serializer left as `TODO()` does. */
@Serializable(with = UndecodableSerializer::class)
private class Undecodable

private object UndecodableSerializer : KSerializer<Undecodable> {
    override val descriptor = PrimitiveSerialDescriptor("Undecodable", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Undecodable) = error("not needed")
    override fun deserialize(decoder: Decoder): Undecodable = TODO()
}

/**
 * What an [InspectorConnection] knows a probe through, wired as [TimeMachineProbe] wires it, and the
 * connection itself, which [start] runs.
 */
private class Probed(
    val session: TimeMachineSession,
    val sink: TimeMachineSink,
    private val connection: InspectorConnection,
    private val scope: CoroutineScope,
) {
    private var running: Job? = null

    fun start() {
        running = scope.launch(Dispatchers.Default) { connection.run() }
    }

    fun stop() {
        running?.cancel()
        connection.close()
    }
}

private fun withConnection(
    inspector: FakeInspector,
    bufferSize: Int = 1024,
    window: Int = 256 * 1024,
    maxRetryDelay: Duration = 100.milliseconds,
    // Longer than any test: no connection holds unless the test asks for it.
    heldAfter: Duration = 10.seconds,
    notices: SendChannel<String>? = null,
    block: suspend (Probed) -> Unit,
) = runBlocking {
    val config = TimeMachineConfig(appName = "ConnectionTest", enabled = true, port = inspector.port)
    val session = TimeMachineSession(id = Uuid.random(), name = config.appName)
    val outbox = TimeMachineOutbox(bufferSize, window)
    val sink = TimeMachineSink(outbox::offer)
    val connection = InspectorConnection(
        config, session, sink, outbox,
        firstRetryDelay = 20.milliseconds,
        maxRetryDelay = maxRetryDelay,
        heldAfter = heldAfter,
        handshakeTimeout = 300.milliseconds,
        notice = { println(it); notices?.trySend(it) },
    )
    val probed = Probed(session, sink, connection, scope = this)
    try {
        withTimeout(10.seconds) { block(probed) }
    } finally {
        probed.stop()
        inspector.close()
    }
}

/** What a welcomed probe sends first: the description of [vm], live at that time. */
private suspend fun FakeInspector.receiveAnnounce(vm: Uuid) {
    assertEquals(TimeMachineClientMessage.ViewModelInfo(vm, info(vm)), received.receive())
}

class InspectorConnectionTest {

    @Test
    fun aWelcomedProbeStreamsWhatItReportedBeforeConnecting() {
        val inspector = FakeInspector()
        withConnection(inspector) { probed ->
            val vm = Uuid.random()
            probed.sink.started(info(vm), noIntervention)
            probed.sink.observed(vm, VIEObjectKind.Model, role<String>(), "first")
            probed.start()

            val hello = inspector.hellos.receive()
            assertEquals(probed.session, hello.session)
            assertEquals(TimeMachineConnection.protocolVersion, hello.protocolVersion)

            inspector.receiveAnnounce(vm)
            val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())
            assertEquals(vm to 0L, started.info.id to started.sequence.value)
            val observed = assertIs<TimeMachineClientMessage.Observed>(inspector.received.receive())
            assertEquals("first" to 1L, observed.observation.asString to observed.sequence.value)
        }
    }

    @Test
    fun viewModelInfoIsAnsweredFromTheLiveViewModels() {
        val inspector = FakeInspector()
        withConnection(inspector) { probed ->
            val vm = Uuid.random()
            probed.sink.started(info(vm), noIntervention)
            probed.start()
            val connection = inspector.welcomed.receive()
            inspector.receiveAnnounce(vm)
            assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())

            connection.send(TimeMachineServerMessage.serializer(), TimeMachineServerMessage.RequestViewModelInfo(vm))
            assertEquals(TimeMachineClientMessage.ViewModelInfo(vm, info(vm)), inspector.received.receive())

            val unknown = Uuid.random()
            connection.send(TimeMachineServerMessage.serializer(), TimeMachineServerMessage.RequestViewModelInfo(unknown))
            val answer = assertIs<TimeMachineClientMessage.ViewModelInfo>(inspector.received.receive())
            assertEquals(unknown, answer.viewModelId)
            assertNull(answer.info)
        }
    }

    @Test
    fun aReconnectingProbeAnnouncesItsLiveViewModelsAgainUnderTheSameSession() {
        val inspector = FakeInspector()
        withConnection(inspector) { probed ->
            val alive = Uuid.random()
            val closed = Uuid.random()
            probed.sink.started(info(alive), noIntervention)
            probed.sink.started(info(closed), noIntervention)
            probed.sink.closed(closed)
            probed.start()

            assertEquals(probed.session, inspector.hellos.receive().session)
            val first = inspector.welcomed.receive()
            inspector.receiveAnnounce(alive)
            repeat(3) { inspector.received.receive() } // started, started, closed
            first.close()

            assertEquals(probed.session, inspector.hellos.receive().session)
            inspector.welcomed.receive()
            // A description, not a start: it takes no position in the timeline.
            assertEquals(TimeMachineClientMessage.ViewModelInfo(alive, info(alive)), inspector.received.receive())

            probed.sink.observed(alive, VIEObjectKind.Model, role<String>(), "after")
            val observed = assertIs<TimeMachineClientMessage.Observed>(inspector.received.receive())
            assertEquals("after" to 3L, observed.observation.asString to observed.sequence.value)
        }
    }

    @Test
    fun whatWasQueuedDuringAnOutageKeepsItsOrderAfterTheReAnnounce() {
        val secondWelcome = CompletableDeferred<Unit>()
        val inspector = FakeInspector { connection ->
            if (connection == 1) secondWelcome.await()
            TimeMachineServerMessage.Welcome
        }
        withConnection(inspector) { probed ->
            val alive = Uuid.random()
            val new = Uuid.random()
            probed.sink.started(info(alive), noIntervention)
            probed.start()

            inspector.hellos.receive()
            val first = inspector.welcomed.receive()
            inspector.receiveAnnounce(alive)
            inspector.received.receive() // started
            probed.sink.observed(alive, VIEObjectKind.Model, role<String>(), "before")
            inspector.received.receive() // observed
            first.close()

            // Once the probe says hello again, the first stream is over: what follows is queued.
            inspector.hellos.receive()
            probed.sink.started(info(new), noIntervention)
            probed.sink.observed(alive, VIEObjectKind.Model, role<String>(), "during")
            probed.sink.closed(new)
            secondWelcome.complete(Unit)

            inspector.welcomed.receive()
            assertEquals(TimeMachineClientMessage.ViewModelInfo(alive, info(alive)), inspector.received.receive())
            val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())
            assertEquals(new to 2L, started.info.id to started.sequence.value)
            val observed = assertIs<TimeMachineClientMessage.Observed>(inspector.received.receive())
            assertEquals("during" to 3L, observed.observation.asString to observed.sequence.value)
            val closed = assertIs<TimeMachineClientMessage.ViewModelClosed>(inspector.received.receive())
            assertEquals(new to 4L, closed.viewModelId to closed.sequence.value)
        }
    }

    @Test
    fun aConnectionDroppedMidStreamIsReconnectedToAndWhatWasInFlightIsSentAgain() {
        val inspector = FakeInspector().apply { acknowledge = false }
        withConnection(inspector) { probed ->
            val vm = Uuid.random()
            probed.sink.started(info(vm), noIntervention)
            probed.sink.observed(vm, VIEObjectKind.Model, role<String>(), "first")
            probed.start()
            assertEquals(probed.session, inspector.hellos.receive().session)
            val first = inspector.welcomed.receive()
            inspector.receiveAnnounce(vm)
            repeat(2) { inspector.received.receive() }

            // Cancelled rather than closed: the server side vanishes while the probe is streaming.
            first.cancel("the inspector went away")

            assertEquals(probed.session, inspector.hellos.receive().session)
            inspector.welcomed.receive()
            assertEquals(TimeMachineClientMessage.ViewModelInfo(vm, info(vm)), inspector.received.receive())
            val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())
            assertEquals(0L, started.sequence.value)
            val observed = assertIs<TimeMachineClientMessage.Observed>(inspector.received.receive())
            assertEquals("first" to 1L, observed.observation.asString to observed.sequence.value)

            // The loop is streaming again, not only reconnected.
            probed.sink.observed(vm, VIEObjectKind.Model, role<String>(), "after")
            val after = assertIs<TimeMachineClientMessage.Observed>(inspector.received.receive())
            assertEquals("after" to 2L, after.observation.asString to after.sequence.value)
        }
    }

    @Test
    fun anInspectorThatDoesNotAcknowledgeReceivesOneWindowThenDropReports() {
        val inspector = FakeInspector().apply { acknowledge = false }
        // A window of 0 holds one message in flight at a time.
        withConnection(inspector, bufferSize = 2, window = 0) { probed ->
            val vm = Uuid.random()
            probed.sink.started(info(vm), noIntervention)
            probed.start()
            val connection = inspector.welcomed.receive()
            inspector.receiveAnnounce(vm)
            assertEquals(0L, assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive()).sequence.value)

            listOf("1", "2", "3", "4").forEach { probed.sink.observed(vm, VIEObjectKind.Model, role<String>(), it) }
            assertNull(withTimeoutOrNull(200.milliseconds) { inspector.received.receive() })

            inspector.acknowledge = true
            connection.send(TimeMachineServerMessage.serializer(), TimeMachineServerMessage.Received(TimeMachineSequence(0)))
            val dropped = assertIs<TimeMachineClientMessage.ObservationsDropped>(inspector.received.receive())
            assertEquals(Triple(vm, 2, 1L), Triple(dropped.viewModelId, dropped.count, dropped.sequence.value))
            val observed = List(2) { assertIs<TimeMachineClientMessage.Observed>(inspector.received.receive()) }
            assertEquals(listOf("3" to 2L, "4" to 3L), observed.map { it.observation.asString to it.sequence.value })
        }
    }

    @Test
    fun whatTheInspectorDidNotAcknowledgeIsSentAgainOnTheNextConnection() {
        val inspector = FakeInspector().apply { acknowledge = false }
        withConnection(inspector) { probed ->
            val vm = Uuid.random()
            probed.sink.started(info(vm), noIntervention)
            probed.sink.observed(vm, VIEObjectKind.Model, role<String>(), "first")
            probed.start()
            val first = inspector.welcomed.receive()
            inspector.receiveAnnounce(vm)
            repeat(2) { inspector.received.receive() }
            inspector.acknowledge = true
            first.close()

            inspector.welcomed.receive()
            assertEquals(TimeMachineClientMessage.ViewModelInfo(vm, info(vm)), inspector.received.receive())
            val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())
            assertEquals(0L, started.sequence.value)
            val observed = assertIs<TimeMachineClientMessage.Observed>(inspector.received.receive())
            assertEquals("first" to 1L, observed.observation.asString to observed.sequence.value)
        }
    }

    @Test
    fun aRejectedProbeStreamsNothingAndRetries() {
        val inspector = FakeInspector { connection ->
            if (connection == 0) {
                TimeMachineServerMessage.Rejected(
                    TimeMachineConnection.protocolVersion + 1,
                )
            } else {
                TimeMachineServerMessage.Welcome
            }
        }
        withConnection(inspector) { probed ->
            val vm = Uuid.random()
            probed.sink.started(info(vm), noIntervention)
            probed.start()

            inspector.hellos.receive()
            inspector.hellos.receive()
            inspector.welcomed.receive()
            inspector.receiveAnnounce(vm)
            // The rejected connection took nothing from the queue.
            val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())
            assertEquals(0L, started.sequence.value)
        }
    }

    @Test
    fun anInspectorThatNeverAnswersTheHandshakeIsGivenUpOnAndRetried() {
        val inspector = FakeInspector { connection ->
            if (connection == 0) awaitCancellation()
            TimeMachineServerMessage.Welcome
        }
        withConnection(inspector) { probed ->
            val vm = Uuid.random()
            probed.sink.started(info(vm), noIntervention)
            probed.start()

            inspector.hellos.receive()
            inspector.hellos.receive()
            inspector.welcomed.receive()
            inspector.receiveAnnounce(vm)
            // The unanswered connection took nothing from the queue.
            val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())
            assertEquals(0L, started.sequence.value)
        }
    }

    @Test
    fun aConnectionIsAnnouncedOnceItHoldsAndEndsTheOutage() {
        val notices = Channel<String>(Channel.UNLIMITED)
        val inspector = FakeInspector()
        withConnection(inspector, maxRetryDelay = 10.seconds, heldAfter = 1.seconds, notices = notices) { probed ->
            probed.start()
            val printed = mutableListOf<String>()
            // Waits for the next notice that contains the fragment, recording every notice on the way.
            suspend fun awaitNotice(fragment: String) {
                do {
                    printed += notices.receive()
                } while (fragment !in printed.last())
            }

            // Dropped as soon as welcomed, as by an inspector that fails on what the probe sends first:
            // the probe sends it first again on every connection. Retried after 20, 40, 80, 160, 320
            // and 640 ms: the next retry would wait 1280 ms.
            repeat(6) {
                inspector.hellos.receive()
                inspector.welcomed.receive().close()
            }

            inspector.hellos.receive()
            val holding = inspector.welcomed.receive()
            awaitNotice("connected to the inspector")
            // None of the connections that dropped early was announced: their drop, printed once, came
            // before the first announce.
            assertEquals(1, printed.count { "connected to the inspector" in it }, printed.toString())
            assertEquals(1, printed.count { "closed the connection" in it }, printed.toString())

            holding.close()
            val closedAt = TimeSource.Monotonic.markNow()

            // A new outage: its drop is news, and the next retry waits the first delay again.
            awaitNotice("closed the connection")
            inspector.hellos.receive()
            val gap = closedAt.elapsedNow()
            assertTrue(gap < 1.seconds, "Retried after $gap")
        }
    }

    @Test
    fun anInterventionIsAnsweredWithItsViewModelsResult() {
        val inspector = FakeInspector()
        withConnection(inspector) { probed ->
            val vm = Uuid.random()
            val requests = mutableListOf<TimeMachineServerMessage.Intervene>()
            probed.sink.started(info(vm)) { requests += it; VIEInterventionResult.Success }
            probed.start()
            val connection = inspector.welcomed.receive()
            inspector.receiveAnnounce(vm)
            assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())

            val request = TimeMachineServerMessage.Intervene(Uuid.random(), vm, VIEObjectKind.Intent, "T", JsonNull)
            connection.send(TimeMachineServerMessage.serializer(), request)
            assertEquals(TimeMachineClientMessage.InterventionResult(request.requestId, VIEInterventionResult.Success), inspector.received.receive())
            assertEquals(listOf(request), requests)

            val unknown = request.copy(requestId = Uuid.random(), viewModelId = Uuid.random())
            connection.send(TimeMachineServerMessage.serializer(), unknown)
            val answer = assertIs<TimeMachineClientMessage.InterventionResult>(inspector.received.receive())
            assertEquals(unknown.requestId, answer.requestId)
            assertEquals(VIEInterventionResult.Reason.UnknownViewModel, assertIs<VIEInterventionResult.Failure>(answer.result).reason)
        }
    }

    @Test
    fun aCancellationThatIsNotTheProbesOwnDoesNotEndTheConnectionLoop() {
        val inspector = FakeInspector()
        withConnection(inspector) { probed ->
            val vm = Uuid.random()
            probed.sink.started(info(vm)) { throw CancellationException("not the probe's cancellation") }
            probed.start()
            val connection = inspector.welcomed.receive()
            inspector.receiveAnnounce(vm)
            assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())

            val request = TimeMachineServerMessage.Intervene(Uuid.random(), vm, VIEObjectKind.Intent, "T", JsonNull)
            connection.send(TimeMachineServerMessage.serializer(), request)

            // Still streaming: the next request is answered. That its answer is the next message also
            // shows the throwing intervener got none: the inspector times such a request out.
            connection.send(TimeMachineServerMessage.serializer(), TimeMachineServerMessage.RequestViewModelInfo(vm))
            assertEquals(TimeMachineClientMessage.ViewModelInfo(vm, info(vm)), inspector.received.receive())
        }
    }

    @Test
    fun anErrorWhileDecodingAnInterventionIsRefusedAndTheConnectionLoopGoesOn() {
        val inspector = FakeInspector()
        withConnection(inspector) { probed ->
            val vm = Uuid.random()
            val role = role<Undecodable>()
            probed.sink.started(info(vm)) { request ->
                when (val decoded = role.decodeForIntervention(request.typeName, request.asJson)) {
                    is VIERole.Decoded.Value -> VIEInterventionResult.Success
                    is VIERole.Decoded.Refused -> decoded.failure
                }
            }
            probed.start()
            val connection = inspector.welcomed.receive()
            inspector.receiveAnnounce(vm)
            assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())

            val request = TimeMachineServerMessage.Intervene(Uuid.random(), vm, VIEObjectKind.Model, role.typeName, JsonPrimitive("x"))
            connection.send(TimeMachineServerMessage.serializer(), request)
            val answer = assertIs<TimeMachineClientMessage.InterventionResult>(inspector.received.receive())
            assertEquals(request.requestId, answer.requestId)
            assertEquals(VIEInterventionResult.Reason.BadJson, assertIs<VIEInterventionResult.Failure>(answer.result).reason)

            // Still streaming: the next request is answered.
            connection.send(TimeMachineServerMessage.serializer(), TimeMachineServerMessage.RequestViewModelInfo(vm))
            assertEquals(TimeMachineClientMessage.ViewModelInfo(vm, info(vm)), inspector.received.receive())
        }
    }
}
