@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.lifecycle.viewModelScope
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.isActive
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.SampleWorld
import org.kodein.arch.vie.timemachine.server.fixtures.paneTest
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.ui.core.holds
import org.kodein.arch.vie.timemachine.server.ui.core.viewModel
import org.kodein.arch.vie.timemachine.server.ui.sessions.sessionsPane
import org.kodein.arch.vie.timemachine.server.ui.timeline.timelinePane


/** The shell over a live inspector: what it does for as long as it is composed, beside laying the panes out. */
class InspectorShellTest {

    @Test
    fun `the pane ViewModels of a ViewModel the store drops are cleared, and the window's are kept`() =
        paneTest(width = 1440, height = 900, content = { InspectorShell() }) { inspector ->
            val closed = SampleWorld.editTaskClosedScope
            inspector.selection.selectScope(closed)
            // The store applies the scenario off the UI thread, and a scope it does not hold has no ViewModel to keep.
            until { inspector.store.state.value.holds(closed) }
            val timeline = inspector.viewModel(inspector.timelinePane(closed))
            val sessions = inspector.viewModel(inspector.sessionsPane())
            // Its header comes with its first projection, and it only projects once the shell shows it.
            until { timeline.modelFlow.value.projection.header != null }

            // The session is connected: clearing it drops its closed ViewModels.
            inspector.store.post(TimeMachineEvent.ClearSession(Demo.session.id))

            until { !timeline.viewModelScope.isActive }
            assertTrue(sessions.viewModelScope.isActive)
        }
}
