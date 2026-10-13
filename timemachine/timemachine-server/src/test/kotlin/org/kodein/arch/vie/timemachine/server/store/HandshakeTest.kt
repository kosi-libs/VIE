package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineProtocolVersion
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession


class HandshakeTest {

    private val c1 = ConnectionId(1)
    private val c2 = ConnectionId(2)
    private val c3 = ConnectionId(3)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()
    private val incompatible = TimeMachineProtocolVersion(TimeMachineConnection.protocolVersion.value + 1)

    @Test
    fun anIncompatibleHelloIsRejectedWithOurVersionAndClosed() {
        val (state, commands) = TimeMachineState().on(hello(c1, session, version = incompatible))

        assertEquals(
            listOf(
                Command.Send(c1, TimeMachineServerMessage.Rejected(TimeMachineServerMessage.Rejected.Reason.IncompatibleProtocol, TimeMachineConnection.protocolVersion)),
                Command.Close(c1),
            ),
            commands,
        )
        assertEquals(emptyMap(), state.sessions)
        assertEquals(emptyMap(), state.connections)
    }

    @Test
    fun aRejectedProbeIsRecordedOnceAcrossRetries() {
        val (state) = TimeMachineState().after(
            hello(c1, session, now = at(1), version = incompatible),
            hello(c2, session, now = at(2), version = incompatible),
        )

        assertEquals(Rejection(session.id, "App", incompatible, attempts = 2, lastAttemptAt = at(2)), state.rejections.values.single())
    }

    @Test
    fun aDismissalHoldsForTheSessionAcrossLaterAttempts() {
        val (state) = TimeMachineState().after(
            hello(c1, session, version = incompatible),
            TimeMachineEvent.DismissRejection(session.id),
            hello(c2, session, version = incompatible),
        )

        val rejection = state.rejections.getValue(session.id)
        assertTrue(rejection.dismissed)
        assertEquals(2, rejection.attempts)
    }

    @Test
    fun aCompatibleHelloIsWelcomedBeforeAnythingElse() {
        val (state, commands) = TimeMachineState().on(hello(c1, session, now = at(1)))

        assertSends(c1, listOf(TimeMachineServerMessage.Welcome), commands)
        val record = state.session(session.id)
        assertEquals(c1, record.connection)
        assertEquals(at(1), record.connectedAt)
        assertEquals(session.id, state.connections[c1])
    }

    @Test
    fun messagesFromAConnectionWhoseHelloWasNotAcceptedAreIgnored() {
        val initial = TimeMachineState().after(hello(c1, session, version = incompatible)).state
        val (state, commands) = initial.after(
            receive(c1, started(vm, 0)),
            receive(c2, started(vm, 0)), // never said hello
        )

        assertEquals(initial, state)
        assertEquals(emptyList(), commands)
    }

    @Test
    fun aSecondHelloOnAWelcomedConnectionChangesNothing() {
        val initial = TimeMachineState().after(hello(c1, session)).state
        val transition = initial.on(hello(c1, TimeMachineSession(Uuid.random(), "Other"), now = at(5)))

        assertEquals(Transition(initial), transition)
    }

    @Test
    fun aKnownSessionIsRevivedInPlace() {
        val (state) = TimeMachineState().after(
            hello(c1, session, now = at(1)),
            receive(c1, started(vm, 0)),
            TimeMachineEvent.Disconnected(c1, at(2)),
            hello(c2, session, now = at(3)),
        )

        val record = state.sessions.values.single()
        assertEquals(c2, record.connection)
        assertEquals(at(3), record.connectedAt)
        assertNull(record.disconnectedAt)
        assertEquals(listOf(vm), record.viewModels.keys.toList())
    }

    @Test
    fun aRevivalKeepsEveryConnectionEachPlacedBySequence() {
        val (state) = TimeMachineState().after(
            hello(c1, session, now = at(1)),
            receive(c1, started(vm, 0)),
            receive(c1, observed(vm, 1)),
            TimeMachineEvent.Disconnected(c1, at(2)),
            hello(c2, session, now = at(3)),
            receive(c2, observed(vm, 2)),
        )

        assertEquals(
            listOf(
                ConnectionSpan(ConnectionSpan.Edge(at(1), null), ConnectionSpan.Edge(at(2), seq(1))),
                ConnectionSpan(ConnectionSpan.Edge(at(3), seq(1))),
            ),
            state.session(session.id).connections,
        )
    }

    @Test
    fun anEvictedConnectionsSpanHasNoDisconnect() {
        val (state) = TimeMachineState().after(
            hello(c1, session, now = at(1)),
            receive(c1, started(vm, 0)),
            hello(c2, session, now = at(5)),
            receive(c1, observed(vm, 1)), // late, on the evicted connection
            TimeMachineEvent.Disconnected(c1, at(9)),
        )

        assertEquals(
            listOf(
                ConnectionSpan(ConnectionSpan.Edge(at(1), null)),
                ConnectionSpan(ConnectionSpan.Edge(at(5), seq(0))),
            ),
            state.session(session.id).connections,
        )
    }

    @Test
    fun aRevivalClosesThePriorConnectionAndReAsksOnlyTheUnansweredRequests() {
        val unknown = Uuid.random()
        val answered = Uuid.random()
        val dropReported = Uuid.random()
        val gone = Uuid.random()
        val (_, commands) = TimeMachineState().after(
            hello(c1, session),
            receive(c1, started(vm, 0)),
            receive(c1, observed(unknown, 1)),
            receive(c1, observed(answered, 2)),
            receive(c1, vmInfo(answered)),
            receive(c1, started(dropReported, 3)),
            receive(c1, dropped(dropReported, 4)),
            receive(c1, started(gone, 5)),
            receive(c1, closed(gone, 6)),
        ).state.after(hello(c2, session))

        assertEquals(
            listOf(
                Command.Send(c2, TimeMachineServerMessage.Welcome),
                Command.Close(c1),
                Command.Send(c2, TimeMachineServerMessage.RequestViewModelInfo(unknown)),
                Command.Send(c2, TimeMachineServerMessage.RequestViewModelInfo(dropReported)),
            ),
            commands,
        )
    }

    @Test
    fun theEvictedConnectionStaysMappedUntilItsOwnDisconnectAndItsFramesArePlaced() {
        val (state) = TimeMachineState().after(
            hello(c1, session),
            receive(c1, started(vm, 0)),
            hello(c2, session),
            receive(c2, observed(vm, 2)),
            receive(c1, observed(vm, 1)), // late, on the evicted connection
            TimeMachineEvent.Disconnected(c1, at(9)),
        )

        assertEquals(listOf(0L, 1L, 2L), state.sequencesOf(session.id, vm))
        assertEquals(emptyList(), state.session(session.id).sequences.missing)
        // Not the current connection: the session stays connected.
        assertTrue(state.session(session.id).isConnected)
        assertEquals(mapOf(c2 to session.id), state.connections)
    }

    @Test
    fun aDisconnectFromTheCurrentConnectionMarksTheSessionDisconnected() {
        val (state) = TimeMachineState().after(
            hello(c1, session),
            TimeMachineEvent.Disconnected(c1, at(4)),
        )

        val record = state.session(session.id)
        assertFalse(record.isConnected)
        assertEquals(at(4), record.disconnectedAt)
        assertEquals(emptyMap(), state.connections)
    }

    @Test
    fun duplicateSessionIsNeverSent() {
        val (_, commands) = TimeMachineState().after(hello(c1, session), hello(c2, session), hello(c3, session))

        assertTrue(commands.none { it is Command.Send && it.message is TimeMachineServerMessage.Rejected })
    }

    @Test
    fun theListenerStateIsRecorded() {
        val (state) = TimeMachineState().after(TimeMachineEvent.ListenerChanged(ListenerState.Listening(1234)))

        assertEquals(ListenerState.Listening(1234), state.listener)
    }
}
