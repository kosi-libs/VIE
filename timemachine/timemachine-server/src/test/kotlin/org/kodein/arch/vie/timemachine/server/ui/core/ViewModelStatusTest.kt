package org.kodein.arch.vie.timemachine.server.ui.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.server.store.ConnectionId
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.store.after
import org.kodein.arch.vie.timemachine.server.store.closed
import org.kodein.arch.vie.timemachine.server.store.hello
import org.kodein.arch.vie.timemachine.server.store.observed
import org.kodein.arch.vie.timemachine.server.store.receive
import org.kodein.arch.vie.timemachine.server.store.session
import org.kodein.arch.vie.timemachine.server.store.started
import org.kodein.arch.vie.timemachine.server.store.t0
import org.kodein.arch.vie.timemachine.server.store.vm
import org.kodein.arch.vie.timemachine.server.ui.common.clock


class ViewModelStatusTest {

    private val c1 = ConnectionId(1)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()

    private val described = TimeMachineState().after(hello(c1, session), receive(c1, started(vm, 0))).state
    private val undescribed = TimeMachineState().after(hello(c1, session), receive(c1, observed(vm, 0))).state
    private val disconnected = TimeMachineEvent.Disconnected(c1, t0)

    private fun TimeMachineState.status() = viewModelStatus(session(session.id), vm(session.id, vm))

    @Test
    fun `a described ViewModel of a connected session is alive`() {
        assertEquals(ViewModelStatus.Alive, described.status())
    }

    @Test
    fun `a ViewModel nothing described yet is being identified`() {
        assertEquals(ViewModelStatus.Identifying, undescribed.status())
    }

    @Test
    fun `a ViewModel not known to be closed is its disconnected session's, described or not`() {
        assertEquals(ViewModelStatus.SessionDisconnected, described.after(disconnected).state.status())
        assertEquals(ViewModelStatus.SessionDisconnected, undescribed.after(disconnected).state.status())
    }

    @Test
    fun `a closed ViewModel is closed, whatever its session`() {
        val closedVm = described.after(receive(c1, closed(vm, 1))).state
        assertEquals(ViewModelStatus.Closed, closedVm.status())
        assertEquals(ViewModelStatus.Closed, closedVm.after(disconnected).state.status())
    }

    @Test
    fun `only a closed ViewModel or one of a disconnected session is gone`() {
        assertEquals(listOf(false, false, true, true), listOf(described, undescribed, described.after(disconnected).state, described.after(receive(c1, closed(vm, 1))).state).map { it.status().gone })
    }

    @Test
    fun `the label of each status`() {
        assertEquals("alive", ViewModelStatus.Alive.label(null))
        assertEquals("identifying…", ViewModelStatus.Identifying.label(null))
        assertEquals("session disconnected", ViewModelStatus.SessionDisconnected.label(null))
        assertEquals("closed ${clock(t0)}", ViewModelStatus.Closed.label(t0))
        assertEquals("closed · time unknown", ViewModelStatus.Closed.label(null))
    }
}
