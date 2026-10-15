package org.kodein.arch.vie.timemachine.client

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import io.ktor.websocket.close
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineProtocolVersion
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind


private fun withClient(
    inspector: FakeInspector,
    bufferSize: Int = 1024,
    window: Int = 256 * 1024,
    block: suspend (TimeMachineClient) -> Unit,
) = runBlocking {
    val client = TimeMachineClient(
        TimeMachineConfig(appName = "ClientTest", port = inspector.port, bufferSize = bufferSize),
        firstRetryDelay = 20.milliseconds,
        maxRetryDelay = 100.milliseconds,
        handshakeTimeout = 300.milliseconds,
        window = window,
    )
    try {
        withTimeout(10.seconds) { block(client) }
    } finally {
        client.stop()
        inspector.close()
    }
}

class TimeMachineClientTest {

    @Test
    fun aWelcomedProbeStreamsWhatItReportedBeforeConnecting() {
        val inspector = FakeInspector()
        withClient(inspector) { client ->
            val vm = Uuid.random()
            client.sink.started(info(vm), noIntervention)
            client.sink.observed(vm, VIEObjectKind.Model, role<String>(), "first")
            client.start()

            val hello = inspector.hellos.receive()
            assertEquals(client.session, hello.session)
            assertEquals(TimeMachineConnection.protocolVersion, hello.protocolVersion)

            val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())
            assertEquals(vm to 0L, started.info.id to started.sequence.value)
            val observed = assertIs<TimeMachineClientMessage.Observed>(inspector.received.receive())
            assertEquals("first" to 1L, observed.observation.asString to observed.sequence.value)
        }
    }

    @Test
    fun viewModelInfoIsAnsweredFromTheLiveViewModels() {
        val inspector = FakeInspector()
        withClient(inspector) { client ->
            val vm = Uuid.random()
            client.sink.started(info(vm), noIntervention)
            client.start()
            val connection = inspector.welcomed.receive()
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
        withClient(inspector) { client ->
            val alive = Uuid.random()
            val closed = Uuid.random()
            client.sink.started(info(alive), noIntervention)
            client.sink.started(info(closed), noIntervention)
            client.sink.closed(closed)
            client.start()

            assertEquals(client.session, inspector.hellos.receive().session)
            val first = inspector.welcomed.receive()
            repeat(3) { inspector.received.receive() } // started, started, closed: no re-announce yet
            first.close()

            assertEquals(client.session, inspector.hellos.receive().session)
            inspector.welcomed.receive()
            // A description, not a start: it takes no position in the timeline.
            assertEquals(TimeMachineClientMessage.ViewModelInfo(alive, info(alive)), inspector.received.receive())

            client.sink.observed(alive, VIEObjectKind.Model, role<String>(), "after")
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
        withClient(inspector) { client ->
            val alive = Uuid.random()
            val new = Uuid.random()
            client.sink.started(info(alive), noIntervention)
            client.start()

            inspector.hellos.receive()
            val first = inspector.welcomed.receive()
            inspector.received.receive() // started
            client.sink.observed(alive, VIEObjectKind.Model, role<String>(), "before")
            inspector.received.receive() // observed
            first.close()

            // Once the probe says hello again, the first stream is over: what follows is queued.
            inspector.hellos.receive()
            client.sink.started(info(new), noIntervention)
            client.sink.observed(alive, VIEObjectKind.Model, role<String>(), "during")
            client.sink.closed(new)
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
        withClient(inspector) { client ->
            val vm = Uuid.random()
            client.sink.started(info(vm), noIntervention)
            client.sink.observed(vm, VIEObjectKind.Model, role<String>(), "first")
            client.start()
            assertEquals(client.session, inspector.hellos.receive().session)
            val first = inspector.welcomed.receive()
            repeat(2) { inspector.received.receive() }

            // Cancelled rather than closed: the server side vanishes while the probe is streaming.
            first.cancel("the inspector went away")

            assertEquals(client.session, inspector.hellos.receive().session)
            inspector.welcomed.receive()
            assertEquals(TimeMachineClientMessage.ViewModelInfo(vm, info(vm)), inspector.received.receive())
            val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())
            assertEquals(0L, started.sequence.value)
            val observed = assertIs<TimeMachineClientMessage.Observed>(inspector.received.receive())
            assertEquals("first" to 1L, observed.observation.asString to observed.sequence.value)

            // The loop is streaming again, not only reconnected.
            client.sink.observed(vm, VIEObjectKind.Model, role<String>(), "after")
            val after = assertIs<TimeMachineClientMessage.Observed>(inspector.received.receive())
            assertEquals("after" to 2L, after.observation.asString to after.sequence.value)
        }
    }

    @Test
    fun anInspectorThatDoesNotAcknowledgeReceivesOneWindowThenDropReports() {
        val inspector = FakeInspector().apply { acknowledge = false }
        // A window of 0 holds one message in flight at a time.
        withClient(inspector, bufferSize = 2, window = 0) { client ->
            val vm = Uuid.random()
            client.sink.started(info(vm), noIntervention)
            client.start()
            val connection = inspector.welcomed.receive()
            assertEquals(0L, assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive()).sequence.value)

            listOf("1", "2", "3", "4").forEach { client.sink.observed(vm, VIEObjectKind.Model, role<String>(), it) }
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
        withClient(inspector) { client ->
            val vm = Uuid.random()
            client.sink.started(info(vm), noIntervention)
            client.sink.observed(vm, VIEObjectKind.Model, role<String>(), "first")
            client.start()
            val first = inspector.welcomed.receive()
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
                    TimeMachineServerMessage.Rejected.Reason.IncompatibleProtocol,
                    TimeMachineProtocolVersion(TimeMachineConnection.protocolVersion.value + 1),
                )
            } else {
                TimeMachineServerMessage.Welcome
            }
        }
        withClient(inspector) { client ->
            val vm = Uuid.random()
            client.sink.started(info(vm), noIntervention)
            client.start()

            inspector.hellos.receive()
            inspector.hellos.receive()
            inspector.welcomed.receive()
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
        withClient(inspector) { client ->
            val vm = Uuid.random()
            client.sink.started(info(vm), noIntervention)
            client.start()

            inspector.hellos.receive()
            inspector.hellos.receive()
            inspector.welcomed.receive()
            // The unanswered connection took nothing from the queue.
            val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())
            assertEquals(0L, started.sequence.value)
        }
    }

    @Test
    fun anInterventionIsAnsweredWithItsViewModelsResult() {
        val inspector = FakeInspector()
        withClient(inspector) { client ->
            val vm = Uuid.random()
            val requests = mutableListOf<TimeMachineServerMessage.Intervene>()
            client.sink.started(info(vm)) { requests += it; VIEInterventionResult.Success }
            client.start()
            val connection = inspector.welcomed.receive()
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
        withClient(inspector) { client ->
            val vm = Uuid.random()
            client.sink.started(info(vm)) { throw CancellationException("not the probe's cancellation") }
            client.start()
            val connection = inspector.welcomed.receive()
            assertIs<TimeMachineClientMessage.ViewModelStarted>(inspector.received.receive())

            val request = TimeMachineServerMessage.Intervene(Uuid.random(), vm, VIEObjectKind.Intent, "T", JsonNull)
            connection.send(TimeMachineServerMessage.serializer(), request)

            // Still streaming: the next request is answered.
            connection.send(TimeMachineServerMessage.serializer(), TimeMachineServerMessage.RequestViewModelInfo(vm))
            assertEquals(TimeMachineClientMessage.ViewModelInfo(vm, info(vm)), inspector.received.receive())
        }
    }

    @Test
    fun stoppingTheClientEndsTheConnectionLoop() {
        val inspector = FakeInspector()
        withClient(inspector) { client ->
            client.start()
            inspector.hellos.receive()
            val connection = inspector.welcomed.receive()
            client.stop()
            connection.close()
            assertNull(withTimeoutOrNull(500.milliseconds) { inspector.hellos.receive() })
        }
    }
}
