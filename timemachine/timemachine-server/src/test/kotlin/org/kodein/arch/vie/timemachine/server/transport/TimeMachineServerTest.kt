package org.kodein.arch.vie.timemachine.server.transport

import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.store.InterventionStatus
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.store.TimeMachineStore
import org.kodein.arch.vie.timemachine.server.store.TimelineEntry
import org.kodein.arch.vie.timemachine.server.store.dropped
import org.kodein.arch.vie.timemachine.server.store.observed
import org.kodein.arch.vie.timemachine.server.store.seq
import org.kodein.arch.vie.timemachine.server.store.seqs
import org.kodein.arch.vie.timemachine.server.store.started
import org.kodein.arch.vie.timemachine.server.store.vmInfo


private class Inspector(val store: TimeMachineStore, val server: TimeMachineServer, val probe: FakeProbe) {
    suspend fun until(predicate: (TimeMachineState) -> Boolean): TimeMachineState = store.state.first(predicate)
}

private fun inspector(port: Int, block: suspend (store: TimeMachineStore, server: TimeMachineServer) -> Unit) = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val connections = TimeMachineConnections(scope, closeTimeout = 1.seconds)
    val store = TimeMachineStore(scope, interventionTimeout = 200.milliseconds, execute = connections::execute)
    val server = TimeMachineServer(store, connections, port = port, handshakeTimeout = 500.milliseconds)
    try {
        withTimeout(10.seconds) { block(store, server) }
    } finally {
        server.stop()
        scope.cancel()
    }
}

private fun withInspector(block: suspend Inspector.() -> Unit) = inspector(port = 0) { store, server ->
    server.start()
    val listener = assertIs<ListenerState.Listening>(store.state.first { it.listener != ListenerState.Starting }.listener)
    FakeProbe(listener.port).use { Inspector(store, server, it).block() }
}

class TimeMachineServerTest {

    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()
    private val later = TimeMachineConnection.protocolVersion + 1
    private val rejected = TimeMachineServerMessage.Rejected(TimeMachineConnection.protocolVersion)

    @Test
    fun aCompatibleProbeIsWelcomed() = withInspector {
        val connection = probe.connect()
        connection.send(TimeMachineClientMessage.Hello(session, TimeMachineConnection.protocolVersion))

        assertEquals(TimeMachineServerMessage.Welcome, connection.receive())
        until { it.sessions[session.id]?.isConnected == true }
    }

    @Test
    fun anIncompatibleProbeIsRejectedAndRecordedOnceAcrossRetries() = withInspector {
        repeat(2) {
            val connection = probe.connect()
            connection.send(TimeMachineClientMessage.Hello(session, later))
            assertEquals(rejected, connection.receive())
            assertNull(connection.receive())
        }

        val state = until { it.rejections[session.id]?.attempts == 2 }
        assertEquals(emptyMap(), state.sessions)
    }

    @Test
    fun aLaterVersionsHelloWithKeysThisVersionDoesNotKnowIsStillRejectedAndRecorded() = withInspector {
        val connection = probe.connect()
        connection.sendText(
            """{"#type":"hello","protocolVersion":$later,"session":{"id":"${session.id}","name":"App","device":"Pixel"},"capabilities":["replay"]}"""
        )

        assertEquals(rejected, connection.receive())
        val rejection = until { session.id in it.rejections }.rejections.getValue(session.id)
        assertEquals(later, rejection.protocolVersion)
    }

    @Test
    fun aFirstFrameThatIsNotAHelloIsClosed() = withInspector {
        val connection = probe.connect()
        connection.send(started(vm, 0))

        assertNull(connection.receive())
        assertEquals(emptyMap(), store.state.value.sessions)
    }

    @Test
    fun aProbeThatSaysNothingIsClosedAfterTheHandshakeTimeout() = withInspector {
        assertNull(probe.connect().receive())
    }

    @Test
    fun anUndecodableFrameAndASecondHelloAreSkipped() = withInspector {
        val connection = probe.welcomed(session)
        connection.sendText("not a message")
        connection.send(TimeMachineClientMessage.Hello(TimeMachineSession(Uuid.random(), "Other"), TimeMachineConnection.protocolVersion))
        connection.send(started(vm, 0))

        val state = until { it.sessions[session.id]?.viewModels?.containsKey(vm) == true }
        assertEquals(listOf(session.id), state.sessions.keys.toList())
    }

    @Test
    fun aClosedConnectionMarksTheSessionDisconnected() = withInspector {
        probe.welcomed(session).close()

        until { it.sessions[session.id]?.isConnected == false }
    }

    @Test
    fun aReconnectEvictsThePriorConnectionAndRevivesTheSessionWithoutDuplicates() = withInspector {
        val first = probe.welcomed(session)
        first.send(started(vm, 0))
        assertEquals(TimeMachineServerMessage.Received(seq(0)), first.receive())

        val second = probe.welcomed(session)
        assertNull(first.receive()) // closed by the inspector
        second.send(vmInfo(vm)) // the probe's re-announce: nothing is asked of a ViewModel that is not awaited

        val state = until { it.connections.size == 1 && it.sessions[session.id]?.viewModels?.get(vm)?.info != null }
        assertEquals(1, state.sessions.size)
        assertEquals(listOf(vm), state.sessions.getValue(session.id).viewModels.keys.toList())
    }

    @Test
    fun theInspectorAsksAboutAnUnknownViewModelAndAfterADropReport() = withInspector {
        val connection = probe.welcomed(session)
        connection.send(observed(vm, 0))
        assertEquals(TimeMachineServerMessage.RequestViewModelInfo(vm), connection.receiveRequest())

        val other = Uuid.random()
        connection.send(started(other, 1))
        connection.send(dropped(other, 2))
        assertEquals(TimeMachineServerMessage.RequestViewModelInfo(other), connection.receiveRequest())
    }

    @Test
    fun aCloseLostWithTheOldConnectionLeavesTheViewModelClosedAfterTheReconnect() = withInspector {
        val first = probe.welcomed(session)
        first.send(observed(vm, 0))
        assertEquals(TimeMachineServerMessage.RequestViewModelInfo(vm), first.receiveRequest()) // never answered
        first.abort()
        until { it.sessions[session.id]?.isConnected == false }

        val second = probe.welcomed(session)
        assertEquals(TimeMachineServerMessage.RequestViewModelInfo(vm), second.receive())
        second.send(vmInfo(vm, known = false))

        until { it.sessions[session.id]?.viewModels?.get(vm)?.closed == true }
    }

    @Test
    fun theInspectorAcknowledgesWhatItAppliedDuplicatesIncluded() = withInspector {
        val connection = probe.welcomed(session)
        connection.send(started(vm, 0))
        connection.send(observed(vm, 1))
        connection.send(observed(vm, 2))
        var highest = -1L
        while (highest < 2) {
            // One acknowledgement per batch: how the three were batched does not matter.
            val received = assertIs<TimeMachineServerMessage.Received>(connection.receive())
            assertTrue(received.sequence.value > highest)
            highest = received.sequence.value
        }

        connection.send(observed(vm, 1)) // a resend
        assertEquals(TimeMachineServerMessage.Received(seq(1)), connection.receive())
        assertEquals(listOf(0L, 1L, 2L), store.state.value.sessions.getValue(session.id).viewModels.getValue(vm).rows.map { it.sequence.value })
    }

    @Test
    fun aMidStreamStartAndAGapAreMarked() = withInspector {
        val connection = probe.welcomed(session)
        connection.send(started(Uuid.random(), 5))
        connection.send(observed(vm, 6))
        connection.send(observed(vm, 9))

        val state = until { it.sessions[session.id]?.viewModels?.get(vm)?.rows?.size == 2 }
        val timeline = state.timeline(session.id, vm)
        assertEquals(TimelineEntry.HistoryUnavailable, timeline[0])
        assertEquals(TimelineEntry.PossiblyIncomplete(seqs(7, 8)), timeline[2])
    }

    // Intervention

    private suspend fun Inspector.status(request: Uuid, predicate: (InterventionStatus) -> Boolean): InterventionStatus =
        until { state -> state.sessions[session.id]?.viewModels?.get(vm)?.interventions?.get(request)?.status?.let(predicate) == true }
            .sessions.getValue(session.id).viewModels.getValue(vm).interventions.getValue(request).status

    private suspend fun Inspector.startedViewModel(): FakeProbe.Connection = probe.welcomed(session).apply {
        send(started(vm, 0))
        until { it.sessions[session.id]?.viewModels?.containsKey(vm) == true }
    }

    @Test
    fun anInterventionRoundTrips() = withInspector {
        val connection = startedViewModel()

        val request = store.intervene(session.id, vm, VIEObjectKind.Intent, "I", JsonPrimitive(1))
        assertEquals(TimeMachineServerMessage.Intervene(request, vm, VIEObjectKind.Intent, "I", JsonPrimitive(1)), connection.receiveRequest())
        connection.send(TimeMachineClientMessage.InterventionResult(request, VIEInterventionResult.Success))
        connection.send(observed(vm, 1, VIEObjectKind.Intent, "I").let { it.copy(observation = it.observation.copy(interventionId = request)) })

        assertEquals(InterventionStatus.Succeeded(seq(1)), status(request) { it is InterventionStatus.Succeeded })
    }

    @Test
    fun aResultArrivingAfterTheDeadlineStillCounts() = withInspector {
        val connection = startedViewModel()

        val request = store.intervene(session.id, vm, VIEObjectKind.Intent, "I", JsonPrimitive(1))
        assertIs<TimeMachineServerMessage.Intervene>(connection.receiveRequest())
        status(request) { it == InterventionStatus.TimedOut }
        connection.send(TimeMachineClientMessage.InterventionResult(request, VIEInterventionResult.Success))

        status(request) { it == InterventionStatus.AcceptedNotObserved }
    }

    @Test
    fun anInterventionOnADisconnectedSessionIsNotSent() = withInspector {
        startedViewModel().close()
        until { it.sessions[session.id]?.isConnected == false }

        val request = store.intervene(session.id, vm, VIEObjectKind.Intent, "I", JsonPrimitive(1))

        status(request) { it == InterventionStatus.NotSent }
    }

    @Test
    fun aPortAlreadyTakenIsReportedAsAFailure() {
        ServerSocket(0).use { taken ->
            inspector(port = taken.localPort) { store, server ->
                server.start()
                assertIs<ListenerState.Failed>(store.state.first { it.listener != ListenerState.Starting }.listener)
            }
        }
    }

    @Test
    fun aFailedListenerListensOnRetryOnceThePortIsFree() {
        ServerSocket(0).use { taken ->
            inspector(port = taken.localPort) { store, server ->
                server.start()
                assertIs<ListenerState.Failed>(store.state.first { it.listener != ListenerState.Starting }.listener)
                taken.close()

                server.retry()

                assertEquals(ListenerState.Listening(taken.localPort), store.state.first { it.listener is ListenerState.Listening }.listener)
            }
        }
    }

    @Test
    fun aRetryWhileListeningChangesNothing() = withInspector {
        val listener = store.state.value.listener
        val connection = probe.welcomed(session)

        server.retry()

        connection.send(started(vm, 0))
        assertEquals(TimeMachineServerMessage.Received(seq(0)), connection.receive())
        assertEquals(listener, store.state.value.listener)
    }

    @Test
    fun aCancelledStartRethrowsTheCancellation() = inspector(port = 0) { _, server ->
        var outcome: Result<Unit>? = null
        runCatching {
            coroutineScope {
                cancel()
                outcome = runCatching { server.start() }
            }
        }
        assertIs<CancellationException>(outcome?.exceptionOrNull())
    }
}
