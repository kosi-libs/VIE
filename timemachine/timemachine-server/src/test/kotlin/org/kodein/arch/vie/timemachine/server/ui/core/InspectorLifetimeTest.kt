package org.kodein.arch.vie.timemachine.server.ui.core

import androidx.lifecycle.viewModelScope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.SampleWorld
import org.kodein.arch.vie.timemachine.server.fixtures.demoSession
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.ui.rightpane.composerPane
import org.kodein.arch.vie.timemachine.server.ui.rightpane.currentModelPane
import org.kodein.arch.vie.timemachine.server.ui.sessions.SessionsVM
import org.kodein.arch.vie.timemachine.server.ui.sessions.sessionsPane
import org.kodein.arch.vie.timemachine.server.ui.timeline.timelinePane


class InspectorLifetimeTest {

    @Test
    fun `closing the inspector clears every ViewModel of its store`() = runVMTest(
        newVM = {
            val inspector = inspector("main")
            inspector.viewModel(inspector.sessionsPane())
            SessionsVM(inspector)
        },
        block = {
            val inspector = testScope.inspector("main")
            val sessions = inspector.viewModel(inspector.sessionsPane())
            val timeline = inspector.viewModel(inspector.timelinePane(taskListScope))
            assertSame(sessions, inspector.viewModel(inspector.sessionsPane()), "one ViewModel per key")
            assertTrue(sessions.viewModelScope.isActive)

            inspector.close()

            assertFalse(sessions.viewModelScope.isActive)
            assertFalse(timeline.viewModelScope.isActive)
        },
    )

    @Test
    fun `clearing a session clears the ViewModels of its scopes, and keeps the window's`() = runVMTest(
        newVM = { SessionsVM(inspector("disconnected")) },
        block = {
            val inspector = testScope.inspector("disconnected")
            val sessions = inspector.viewModel(inspector.sessionsPane())
            val ofSession = listOf(
                inspector.viewModel(inspector.timelinePane(demoSession)),
                inspector.viewModel(inspector.timelinePane(taskListScope)),
                inspector.viewModel(inspector.currentModelPane(Demo.session.id, Demo.taskList)),
                inspector.viewModel(inspector.composerPane(Demo.session.id, Demo.taskList)),
            )
            val earlier = inspector.viewModel(inspector.timelinePane(SampleWorld.earlierScope))
            backgroundScope.launch { inspector.releaseDroppedScopes() }
            runCurrent()
            ofSession.forEach { assertTrue(it.viewModelScope.isActive, "a scope the store holds keeps its ViewModels") }

            inspector.store.post(TimeMachineEvent.ClearSession(Demo.session.id))
            runCurrent()

            ofSession.forEach { assertFalse(it.viewModelScope.isActive) }
            assertTrue(earlier.viewModelScope.isActive, "another session's")
            assertTrue(sessions.viewModelScope.isActive, "the window's")
            // A scope that comes back, as a probe that reconnects brings its session back, starts anew.
            assertNotSame(ofSession.first(), inspector.viewModel(inspector.timelinePane(demoSession)))
        },
    )

    @Test
    fun `clearing the closed ViewModels of a connected session clears their ViewModels only`() = runVMTest(
        newVM = { SessionsVM(inspector("main")) },
        block = {
            val inspector = testScope.inspector("main")
            val closed = inspector.viewModel(inspector.timelinePane(SampleWorld.editTaskClosedScope))
            val closedModel = inspector.viewModel(inspector.currentModelPane(Demo.session.id, Demo.editTaskClosed))
            val alive = inspector.viewModel(inspector.timelinePane(taskListScope))
            val session = inspector.viewModel(inspector.timelinePane(demoSession))
            backgroundScope.launch { inspector.releaseDroppedScopes() }
            runCurrent()

            inspector.store.post(TimeMachineEvent.ClearSession(Demo.session.id))
            runCurrent()

            assertFalse(closed.viewModelScope.isActive)
            assertFalse(closedModel.viewModelScope.isActive)
            assertTrue(alive.viewModelScope.isActive)
            assertTrue(session.viewModelScope.isActive)
        },
    )
}
