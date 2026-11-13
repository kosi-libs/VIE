package org.kodein.arch.vie.timemachine.server.transport

import java.net.ServerSocket
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
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.store.InterventionStatus
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.store.SequenceLedger
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

    /**
     * The sessions the inspector knows once everything sent so far was handled: a later probe's
     * welcome comes after, and the store publishes the state before it executes the commands that
     * send it.
     */
    suspend fun sessionsSoFar(): Set<Uuid> {
        val barrier = TimeMachineSession(Uuid.random(), "Barrier")
        probe.welcomed(barrier)
        return store.state.value.sessions.keys - barrier.id
    }
}

/**
 * The ping period and timeout of a test's inspector: they never fire within a test's 10 s budget,
 * unless a test shortens them.
 */
private val testPingPeriod = 5.seconds
private val testPingTimeout = 10.seconds

private fun inspector(
    port: Int,
    maxFrameSize: Long = 32L * 1024 * 1024,
    pingPeriod: Duration = testPingPeriod,
    timeout: Duration = testPingTimeout,
    block: suspend (store: TimeMachineStore, server: TimeMachineServer) -> Unit,
) = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val connections = TimeMachineConnections(scope, closeTimeout = 1.seconds)
    val store = TimeMachineStore(scope, interventionTimeout = 200.milliseconds, execute = connections::execute)
    val server = TimeMachineServer(
        store, connections, port = port,
        pingPeriod = pingPeriod, timeout = timeout,
        handshakeTimeout = 500.milliseconds, maxFrameSize = maxFrameSize,
    )
    try {
        withTimeout(10.seconds) { block(store, server) }
    } finally {
        server.stop()
        scope.cancel()
    }
}

private fun withInspector(
    maxFrameSize: Long = 32L * 1024 * 1024,
    pingPeriod: Duration = testPingPeriod,
    timeout: Duration = testPingTimeout,
    block: suspend Inspector.() -> Unit,
) = inspector(port = 0, maxFrameSize, pingPeriod, timeout) { store, server ->
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

        assertEquals("Expected a hello, got a ViewModelStarted", connection.closeReason())
        assertEquals(emptySet(), sessionsSoFar())
    }

    @Test
    fun aBinaryFirstFrameIsClosed() = withInspector {
        val connection = probe.connect()
        connection.sendBinary(byteArrayOf(1, 2, 3))

        assertEquals("Expected a hello, got a binary frame", connection.closeReason())
        assertEquals(emptySet(), sessionsSoFar())
    }

    @Test
    fun anUndecodableFirstFrameIsClosed() = withInspector {
        val connection = probe.connect()
        connection.sendText("not a message")

        assertEquals("Expected a hello, got an undecodable frame", connection.closeReason())
        assertEquals(emptySet(), sessionsSoFar())
    }

    @Test
    fun aProbeThatSaysNothingIsClosedAfterTheHandshakeTimeout() = withInspector {
        assertEquals("Expected a hello within 500ms", probe.connect().closeReason())
    }

    @Test
    fun aRefusedConnectionThatNeverAnswersTheCloseIsCancelledAfterTheCloseTimeout() = withInspector {
        val peer = probe.halfOpen()
        assertEquals("Expected a hello within 500ms", peer.closeReason())

        val refused = TimeSource.Monotonic.markNow()
        peer.awaitCut()

        assertTrue(refused.elapsedNow() >= 800.milliseconds, "cancelled after ${refused.elapsedNow()}, before the 1 s close timeout")
    }

    // Weak: no regression of that branch changes what the state shows, it only guards a connection nobody handles.
    @Test
    fun aProbeThatLeavesBeforeItsHelloLeavesNothingBehind() = withInspector {
        probe.connect().close()
        probe.welcomed(session)

        val state = until { it.sessions[session.id]?.isConnected == true }
        assertEquals(1, state.sessions.size)
        assertEquals(emptyMap(), state.rejections)
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

    // Defensive: the real probe sends only text frames.
    @Test
    fun aNonTextFrameAfterTheHelloIsIgnored() = withInspector {
        val connection = probe.welcomed(session)
        // The bytes of a valid message: read as text, it would be applied, so only the frame type tells it apart.
        connection.sendBinary(TimeMachineJson.encodeToString(TimeMachineClientMessage.serializer(), started(vm, 0)).encodeToByteArray())
        connection.send(observed(vm, 1))

        assertEquals(TimeMachineServerMessage.RequestViewModelInfo(vm), connection.receive())
        assertEquals(TimeMachineServerMessage.Received(seq(1)), connection.receive())
        val ledger = until { it.sessions[session.id]?.sequences?.highest == seq(1) }.sessions.getValue(session.id).sequences
        assertEquals(SequenceLedger(floor = seq(1), highest = seq(1)), ledger)
    }

    @Test
    fun aFrameOverTheCapClosesTheConnectionUnread() = withInspector(maxFrameSize = 1024) {
        val connection = probe.welcomed(session)
        connection.send(started(vm, 0))
        assertEquals(TimeMachineServerMessage.Received(seq(0)), connection.receive())
        connection.send(observed(vm, 1, json = JsonPrimitive("x".repeat(2048))))

        assertTrue(connection.closeReason().orEmpty().startsWith("Frame is too big"))
        val state = until { it.sessions[session.id]?.isConnected == false }
        assertEquals(listOf(0L), state.sessions.getValue(session.id).viewModels.getValue(vm).rows.map { it.sequence.value })
    }

    @Test
    fun aFirstFrameOverTheCapClosesTheConnection() = withInspector(maxFrameSize = 1024) {
        val connection = probe.connect()
        connection.sendText("x".repeat(2048))

        assertTrue(connection.closeReason().orEmpty().startsWith("Frame is too big"))
        assertEquals(emptySet(), sessionsSoFar())
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
        first.send(observed(vm, 1))
        var received = first.receive()
        while (received != TimeMachineServerMessage.Received(seq(1))) received = first.receive()

        val second = probe.welcomed(session)
        assertNull(first.receive()) // closed by the inspector
        second.send(vmInfo(vm)) // the probe's re-announce: nothing is asked of a ViewModel that is not awaited
        second.send(observed(vm, 1)) // resent: the probe never saw the acknowledgement
        second.send(observed(vm, 2))
        // Anything else than an acknowledgement before the last one would be a request nobody should make.
        var last: TimeMachineServerMessage
        do last = assertIs<TimeMachineServerMessage.Received>(second.receive()) while (last.sequence != seq(2))

        val state = until { it.connections.size == 1 }
        assertEquals(1, state.sessions.size)
        val record = state.sessions.getValue(session.id).viewModels
        assertEquals(listOf(vm), record.keys.toList())
        assertEquals(listOf(0L, 1L, 2L), record.getValue(vm).rows.map { it.sequence.value })
        assertEquals(3, record.getValue(vm).rowCount)
    }

    @Test
    fun anEvictedConnectionThatNeverAnswersTheCloseIsCancelledAfterTheCloseTimeout() = withInspector {
        probe.welcomedHalfOpen(session)
        val first = until { it.connections.size == 1 }.connections.keys.single()

        val evicted = TimeSource.Monotonic.markNow()
        probe.welcomed(session)
        until { first !in it.connections }

        assertTrue(evicted.elapsedNow() >= 800.milliseconds, "cancelled after ${evicted.elapsedNow()}, before the 1 s close timeout")
    }

    @Test
    fun aCurrentConnectionThatStopsAnsweringPingsIsCutAndItsSessionDisconnected() = withInspector(pingPeriod = 200.milliseconds, timeout = 300.milliseconds) {
        val peer = probe.welcomedHalfOpen(session)

        until { it.sessions[session.id]?.isConnected == false }
        peer.awaitCut()
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
    fun anUndecodableFrameIsAcknowledgedWhenItCarriesASequence() = withInspector {
        val connection = probe.welcomed(session)
        connection.send(started(vm, 0))
        assertEquals(TimeMachineServerMessage.Received(seq(0)), connection.receive())

        connection.sendText("""{"#type":"from-a-later-version","sequence":1}""")
        assertEquals(TimeMachineServerMessage.Received(seq(1)), connection.receive())

        // A sequence that is not a number is not acknowledged: the next answer is the one for 2, and a
        // wrongly acknowledged 5 would come out before it or win over it in the same batch.
        connection.sendText("""{"#type":"from-a-later-version","sequence":"5"}""")
        connection.send(observed(vm, 2))
        assertEquals(TimeMachineServerMessage.Received(seq(2)), connection.receive())
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
