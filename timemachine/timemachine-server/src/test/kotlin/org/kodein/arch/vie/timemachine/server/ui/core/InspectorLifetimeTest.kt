package org.kodein.arch.vie.timemachine.server.ui.core

import androidx.lifecycle.viewModelScope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.isActive
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
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
}
