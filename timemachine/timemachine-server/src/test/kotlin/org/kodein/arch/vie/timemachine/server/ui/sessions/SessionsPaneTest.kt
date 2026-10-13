@file:OptIn(ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.sessions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.demoSession
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.ViewModelStatus
import org.kodein.arch.vie.timemachine.server.ui.timeline.apply


class SessionsPaneTest {

    private fun sessions(
        scenario: String = "main",
        data: ScenarioData = ScenarioData.of(scenario),
        block: suspend TestScope.(SessionsVM, Inspector) -> Unit,
    ): TestResult {
        lateinit var inspector: Inspector
        return runVMTest(
            newVM = {
                inspector = inspector(data.id, data)
                inspector.visibility.show("sessions")
                SessionsVM(inspector)
            },
            block = { vm, _ -> runCurrent(); block(vm, inspector) },
        )
    }

    private val SessionsVM.model get() = modelFlow.value

    @Test
    fun `it lists the sessions with their ViewModels, their activity and what clearing does`() = sessions { vm, _ ->
        val live = vm.model.sessions.first { it.connected }
        val gone = vm.model.sessions.first { !it.connected }
        assertEquals(listOf("Demo", "Demo"), vm.model.sessions.map { it.name })
        assertEquals(4, live.viewModels.size)
        assertEquals("Clear 1 closed ViewModel", live.clearLabel)
        assertTrue(live.clearEnabled)
        assertEquals("Clear session", gone.clearLabel)
        assertEquals(10, live.sparkline.size)
        assertEquals(1, vm.model.disconnectedSessions)
        val closed = live.viewModels.first { it.id == Demo.editTaskClosed }
        assertEquals(ViewModelStatus.Closed, closed.status)
        assertTrue(closed.startSeen)
        assertEquals(false, live.viewModels.first { it.id == Demo.taskList }.startSeen, "first seen, not started")
    }

    @Test
    fun `a connected session with nothing closed has its clear action greyed`() = sessions("main") { vm, inspector ->
        inspector.store.post(TimeMachineEvent.ClearSession(Demo.session.id))
        runCurrent()
        val live = vm.model.sessions.first { it.connected }
        assertEquals("Clear closed ViewModels", live.clearLabel)
        assertTrue(!live.clearEnabled)
    }

    @Test
    fun `selecting a scope writes the holder and shows as selected`() = sessions { vm, inspector ->
        vm.post(SessionsVIE.Intent.SelectScope(taskListScope))
        runCurrent()

        assertEquals(taskListScope, inspector.selection.state.value.scope)
        assertEquals(taskListScope, vm.model.selected)

        // The store drops the session: the holder keeps its value, the pane reads none.
        inspector.store.post(TimeMachineEvent.Disconnected(Demo.connection, Demo.now))
        inspector.store.post(TimeMachineEvent.ClearSession(Demo.session.id))
        runCurrent()
        assertEquals(null, vm.model.selected)
    }

    @Test
    fun `clearing posts to the store`() = sessions { vm, inspector ->
        vm.post(SessionsVIE.Intent.ClearDisconnected)
        runCurrent()
        assertEquals(1, inspector.store.state.value.sessions.size)
        assertEquals(1, vm.model.sessions.size)

        vm.post(SessionsVIE.Intent.ClearSession(Demo.session.id))
        runCurrent()
        // A connected session only loses its closed ViewModels.
        assertEquals(3, vm.model.sessions.single().viewModels.size)
    }

    // The sparkline: a window ending at now, re-read at each 3 s bucket boundary.

    /** A clock 1 s into a bucket, and one row 0.5 s before it, so the next boundary is 2 s away. */
    private val tickNow: Instant = Instant.fromEpochMilliseconds(3_000L * 600_000 + 1_000)

    private val tickData = ScenarioData(
        id = "tick",
        now = tickNow,
        events = Demo.Builder(Demo.connection).apply {
            hello(Demo.session, tickNow - 5_000.milliseconds)
            observed(Demo.taskList, 0, tickNow - 500.milliseconds, VIEObjectKind.Model, Demo.TaskListModel, Demo.taskListModel(null))
        }.events,
    )

    private val SessionsVM.sparkline get() = model.sessions.single().sparkline

    @Test
    fun `a shown Sessions pane's sparkline shifts at a bucket boundary and not between two`() = sessions(data = tickData) { vm, _ ->
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1), vm.sparkline)

        advanceTimeBy(1_900)
        runCurrent()
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1), vm.sparkline, "1.9 s on: still the same bucket")

        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 0), vm.sparkline, "past the boundary: every bar shifted")

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 1, 0, 0), vm.sparkline)
    }

    @Test
    fun `a hidden Sessions pane does not tick`() = sessions(data = tickData) { vm, inspector ->
        inspector.visibility.hide("sessions")
        runCurrent()
        val before = vm.model

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(before, vm.model)

        inspector.visibility.show("sessions")
        runCurrent()
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1, 0, 0, 0), vm.sparkline, "caught up to the clock")
    }

    @Test
    fun `selecting the session scope is a scope too`() = sessions { vm, inspector ->
        vm.post(SessionsVIE.Intent.SelectScope(demoSession))
        runCurrent()
        assertEquals(demoSession, vm.model.selected)
        assertEquals(TimeMachineSequence(0), TimeMachineSequence.First)
        assertEquals(null, inspector.selection.state.value.row)
    }
}
