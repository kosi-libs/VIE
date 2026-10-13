package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery


class ViewModelTest {

    private val c1 = ConnectionId(1)
    private val c2 = ConnectionId(2)
    private val c3 = ConnectionId(3)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()

    private val connected = TimeMachineState().on(hello(c1, session)).state

    @Test
    fun aSignalTypesDeliverySurvivesEvictionOfItsRows() {
        val (state) = TimeMachineState(limits = Limits(maxRows = 1)).on(hello(c1, session)).state.after(
            receive(c1, observed(vm, 0, VIEObjectKind.Signal, "GoBack", delivery = VIESignalDelivery.Effect)),
            receive(c1, observed(vm, 1, VIEObjectKind.Signal, "Saved", delivery = VIESignalDelivery.Event)),
            receive(c1, observed(vm, 2, VIEObjectKind.Signal, "Untyped")),
            receive(c1, observed(vm, 3, VIEObjectKind.Model, "M")),
        )

        val record = state.vm(session.id, vm)
        assertEquals(listOf(3L), record.rows.map { it.sequence.value })
        assertEquals(mapOf("GoBack" to VIESignalDelivery.Effect, "Saved" to VIESignalDelivery.Event), record.signalDelivery)
    }

    @Test
    fun aStartRegistersTheViewModelAndPlacesItsBookendOncePerId() {
        val (state) = connected.after(
            receive(c1, started(vm, 0), now = at(1)),
            receive(c1, started(vm, 3)),
        )

        val record = state.vm(session.id, vm)
        assertEquals(info(vm), record.info)
        assertEquals(at(1), record.firstSeenAt)
        assertEquals(seq(0), record.startSequence)
        assertEquals(listOf(0L), state.sequencesOf(session.id, vm))
    }

    @Test
    fun aDescriptionRegistersTheViewModelWithoutARow() {
        val (state) = connected.after(receive(c1, vmInfo(vm), now = at(2)))

        val record = state.vm(session.id, vm)
        assertEquals(info(vm), record.info)
        assertEquals(at(2), record.firstSeenAt)
        assertEquals(emptyList(), record.rows)
    }

    @Test
    fun anUnknownAnswerClosesTheViewModelKeepsItsInfoAndInfersItsEnd() {
        val other = Uuid.random()
        val (state) = connected.after(
            receive(c1, started(vm, 0)),
            receive(c1, observed(other, 1)),
            receive(c1, vmInfo(vm, known = false)),
        )

        val record = state.vm(session.id, vm)
        assertTrue(record.closed)
        assertEquals(info(vm), record.info)
        assertNull(record.closedAt)
        assertEquals(seq(1), record.inferredEnd)
        assertEquals(listOf(0L), state.sequencesOf(session.id, vm))
    }

    @Test
    fun anUnknownAnswerAboutAViewModelNeverSeenChangesNothing() {
        val (state) = connected.after(receive(c1, vmInfo(vm, known = false)))

        assertEquals(connected, state)
    }

    @Test
    fun aCloseIsPlacedAndRecordsItsTime() {
        val (state) = connected.after(
            receive(c1, started(vm, 0)),
            receive(c1, closed(vm, 1, at = at(7))),
        )

        val record = state.vm(session.id, vm)
        assertTrue(record.closed)
        assertEquals(at(7), record.closedAt)
        assertEquals(seq(1), record.closeSequence)
        assertEquals(listOf(0L, 1L), state.sequencesOf(session.id, vm))
    }

    @Test
    fun nothingReopensAViewModelClosedByItsClose() {
        val (state) = connected.after(
            receive(c1, closed(vm, 1)),
            receive(c1, started(vm, 0)), // late
            receive(c1, vmInfo(vm)),
        )

        val record = state.vm(session.id, vm)
        assertTrue(record.closed)
        assertEquals(info(vm), record.info)
        assertEquals(listOf(0L, 1L), state.sequencesOf(session.id, vm))
    }

    @Test
    fun nothingReopensAViewModelClosedByAnUnknownAnswer() {
        val (state) = connected.after(
            receive(c1, observed(vm, 0)),
            receive(c1, vmInfo(vm, known = false)),
            receive(c1, vmInfo(vm)),
            receive(c1, started(vm, 1)),
        )

        val record = state.vm(session.id, vm)
        assertTrue(record.closed)
        assertEquals(info(vm), record.info)
        assertEquals(listOf(0L, 1L), state.sequencesOf(session.id, vm))
    }

    @Test
    fun aTimelineMessageForAnUnknownIdIsPlacedAtOnceAndItsInfoRequestedOnce() {
        val (state, commands) = connected.after(
            receive(c1, observed(vm, 0)),
            receive(c1, observed(vm, 1)),
        )

        val record = state.vm(session.id, vm)
        assertTrue(record.isUnknown)
        assertEquals(listOf(0L, 1L), state.sequencesOf(session.id, vm))
        assertEquals(listOf(vm), requests(commands, c1))
    }

    @Test
    fun anUnknownIdFirstSeenClosedIsNotRequested() {
        val (_, commands) = connected.after(receive(c1, closed(vm, 0)))

        assertEquals(emptyList(), requests(commands, c1))
    }

    @Test
    fun everyDropReportRequestsInfoUnlessTheViewModelIsClosed() {
        val (_, commands) = connected.after(
            receive(c1, started(vm, 0)),
            receive(c1, dropped(vm, 1)),
            receive(c1, dropped(vm, 2)),
            receive(c1, closed(vm, 3)),
            receive(c1, dropped(vm, 4)),
        )

        assertEquals(listOf(vm, vm), requests(commands, c1))
    }

    @Test
    fun oneMessageTriggersAtMostOneRequest() {
        val (_, commands) = connected.after(receive(c1, dropped(vm, 0)))

        assertEquals(listOf(vm), requests(commands, c1))
    }

    @Test
    fun requestsGoToTheCurrentConnectionNotTheOneThatCarriedTheTrigger() {
        val (_, commands) = connected.after(receive(c1, started(vm, 0)), hello(c2, session))
            .state.after(receive(c1, dropped(vm, 1)))

        assertEquals(emptyList(), requests(commands, c1))
        assertEquals(listOf(vm), requests(commands, c2))
    }

    @Test
    fun nothingIsRequestedWhileTheSessionIsDisconnected() {
        val disconnected = connected.after(receive(c1, started(vm, 0)), hello(c2, session), TimeMachineEvent.Disconnected(c2, t0)).state
        val (state, commands) = disconnected.after(receive(c1, dropped(vm, 1)))

        assertFalse(state.session(session.id).isConnected)
        assertEquals(emptyList(), commands)
    }

    @Test
    fun aDropReportReceivedWhileDisconnectedIsAskedOnRevival() {
        val disconnected = connected.after(receive(c1, started(vm, 0)), hello(c2, session), TimeMachineEvent.Disconnected(c2, t0)).state
        val (_, commands) = disconnected.after(receive(c1, dropped(vm, 1)), hello(c3, session))

        assertEquals(listOf(vm), requests(commands, c3))
    }
}
