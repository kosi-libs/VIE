package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession


class SessionTest {

    private val c1 = ConnectionId(1)
    private val c2 = ConnectionId(2)
    private val c3 = ConnectionId(3)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val otherSession = TimeMachineSession(Uuid.random(), "Other")
    private val alive = Uuid.random()
    private val gone = Uuid.random()

    private val populated = TimeMachineState().after(
        hello(c1, session),
        receive(c1, started(alive, 0)),
        receive(c1, started(gone, 1)),
        receive(c1, closed(gone, 2)),
    ).state

    @Test
    fun clearingAConnectedSessionRemovesOnlyItsClosedViewModels() {
        val (state) = populated.after(TimeMachineEvent.ClearSession(session.id))

        val record = state.session(session.id)
        assertEquals(listOf(alive), record.viewModels.keys.toList())
        assertEquals(setOf(gone), record.cleared)
    }

    @Test
    fun clearingADisconnectedSessionRemovesIt() {
        val (state) = populated.after(TimeMachineEvent.Disconnected(c1, t0), TimeMachineEvent.ClearSession(session.id))

        assertEquals(emptyMap(), state.sessions)
    }

    @Test
    fun removingASessionUnmapsItsConnectionsAndTheirEventsAreIgnored() {
        // c1 is evicted but not disconnected yet when c2 disconnects and the session is cleared.
        val cleared = populated.after(
            hello(c2, session),
            TimeMachineEvent.Disconnected(c2, t0),
            TimeMachineEvent.ClearSession(session.id),
        ).state
        assertEquals(emptyMap(), cleared.connections)

        val (state, commands) = cleared.after(receive(c1, started(alive, 3)), TimeMachineEvent.Disconnected(c1, t0))
        assertEquals(cleared, state)
        assertEquals(emptyList(), commands)
    }

    @Test
    fun aMessageAboutAClearedViewModelUpdatesTheSequencesAndIsOtherwiseIgnored() {
        val (state, commands) = populated.after(
            TimeMachineEvent.ClearSession(session.id),
            receive(c1, observed(gone, 4)),
            receive(c1, observed(gone, 3)),
            receive(c1, vmInfo(gone)),
        )

        val record = state.session(session.id)
        assertEquals(listOf(alive), record.viewModels.keys.toList())
        assertEquals(SequenceLedger(floor = seq(0), highest = seq(4)), record.sequences)
        assertEquals(emptyList(), requests(commands, c1))
    }

    @Test
    fun clearingTheDisconnectedSessionsKeepsTheConnectedOnes() {
        val (state) = populated.after(
            hello(c2, otherSession),
            hello(c3, TimeMachineSession(Uuid.random(), "Third")),
            TimeMachineEvent.Disconnected(c1, t0),
            TimeMachineEvent.Disconnected(c3, t0),
            TimeMachineEvent.ClearDisconnected,
        )

        assertEquals(listOf(otherSession.id), state.sessions.keys.toList())
        assertEquals(mapOf(c2 to otherSession.id), state.connections)
    }

    @Test
    fun sessionsAndViewModelsKeepTheirFirstSeenOrder() {
        val (state) = TimeMachineState().after(
            hello(c1, session),
            hello(c2, otherSession),
            receive(c1, observed(gone, 0)),
            receive(c1, started(alive, 1)),
            receive(c1, vmInfo(gone)),
            TimeMachineEvent.Disconnected(c1, t0),
            hello(c3, session),
        )

        assertEquals(listOf(session.id, otherSession.id), state.sessions.keys.toList())
        assertEquals(listOf(gone, alive), state.session(session.id).viewModels.keys.toList())
    }

    // Acknowledgements

    @Test
    fun aBatchAcknowledgesTheHighestTimelineMessageOfEachConnectionLast() {
        val (_, commands) = TimeMachineState().on(
            listOf(
                hello(c1, session),
                hello(c2, otherSession),
                receive(c1, started(alive, 0)),
                receive(c2, observed(gone, 7)),
                receive(c1, observed(alive, 2)),
                receive(c1, observed(alive, 1)),
                receive(c1, vmInfo(alive)),
            )
        )

        assertEquals(
            listOf(Command.Send(c1, TimeMachineServerMessage.Received(seq(2))), Command.Send(c2, TimeMachineServerMessage.Received(seq(7)))),
            commands.takeLast(2),
        )
        assertEquals(2, commands.count { it is Command.Send && it.message is TimeMachineServerMessage.Received })
    }

    @Test
    fun aDuplicateIsAcknowledged() {
        val (_, commands) = populated.on(listOf(receive(c1, started(alive, 0))))

        assertEquals(listOf(Command.Send(c1, TimeMachineServerMessage.Received(seq(0)))), commands)
    }

    @Test
    fun nothingIsAcknowledgedOnAConnectionWhoseHelloWasNotAccepted() {
        val (_, commands) = populated.on(listOf(receive(c2, observed(alive, 3)), receive(c1, vmInfo(alive))))

        assertEquals(emptyList(), commands)
    }
}
