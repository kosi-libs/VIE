@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.InspectorWindow
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.newInspector
import org.kodein.arch.vie.timemachine.server.ui.common.clock
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.viewModel
import org.kodein.arch.vie.timemachine.server.ui.rightpane.HideRightPaneDescription
import org.kodein.arch.vie.timemachine.server.ui.sessions.RailMenuDescription
import org.kodein.arch.vie.timemachine.server.ui.sessions.sessionsPane
import org.kodein.arch.vie.timemachine.server.ui.timeline.timelinePane


/**
 * The window's layout under the breakpoint: the real [InspectorWindow] over a live [Inspector] of the
 * main scenario, composed at 1000 dp and at 1440 dp, driven through what it shows.
 */
class CompactLayoutTest {

    private val taskList = InspectorScope.ViewModel(Demo.session.id, Demo.taskList)
    private val settings = InspectorScope.ViewModel(Demo.session.id, Demo.settings)

    /** The window, [width] wide, which [block] can change, over the main scenario's inspector. */
    private fun window(
        width: Dp = 1000.dp,
        block: suspend DesktopComposeUiTest.(inspector: Inspector, viewState: InspectorViewState, resize: (Dp) -> Unit) -> Unit,
    ) = runDesktopComposeUiTest(1440, 900) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val inspector = ScenarioData.of("main").newInspector(scope, Dispatchers.Default)
        val viewState = InspectorViewState()
        var current by mutableStateOf(width)
        try {
            setContent { Box(Modifier.width(current).fillMaxHeight()) { InspectorWindow(inspector, viewState) } }
            block(inspector, viewState) { current = it }
        } finally {
            inspector.close()
            scope.cancel()
        }
    }

    /** Waits for [condition], while the Compose clock runs, and the panes' projections land off the UI thread. */
    private fun DesktopComposeUiTest.until(condition: () -> Boolean) = waitUntil(timeoutMillis = 10_000, condition = condition)

    private fun DesktopComposeUiTest.shown(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun DesktopComposeUiTest.described(description: String) = onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `under 1200 dp the sessions are a rail, and from 1200 dp a pane`() {
        window(1000.dp) { _, _, _ ->
            until { described("TaskListVM a1f4") }
            assertTrue(described(RailMenuDescription))
            assertFalse(shown("Clear disconnected"), "the pane's header is not there")
        }
        window(1440.dp) { _, _, _ ->
            until { shown("Clear disconnected") }
            assertFalse(described(RailMenuDescription))
            assertFalse(described("TaskListVM a1f4"))
        }
    }

    @Test
    fun `the menu opens the drawer, picking a ViewModel selects it and closes the drawer`() = window { inspector, viewState, _ ->
        until { described(RailMenuDescription) }
        assertFalse(shown("Clear disconnected"))

        onNodeWithContentDescription(RailMenuDescription).performClick()
        until { shown("Clear disconnected") }
        assertTrue(viewState.drawerOpen)

        until { shown("SettingsVM") }
        onNodeWithText("SettingsVM").performClick()
        until { inspector.selection.state.value.scope == settings }
        assertFalse(viewState.drawerOpen)
        until { !shown("Clear disconnected") }
    }

    @Test
    fun `a click on a tile of the rail selects its scope, and the session's tile its whole timeline`() = window { inspector, _, _ ->
        until { described("SettingsVM c3d7") }
        onNodeWithContentDescription("SettingsVM c3d7").performClick()
        until { inspector.selection.state.value.scope == settings }

        onNodeWithContentDescription("Demo 3f2a…c91e, connected").performClick()
        until { inspector.selection.state.value.scope is InspectorScope.Session }
        assertEquals(Demo.session.id, inspector.selection.state.value.scope?.sessionId)
    }

    @Test
    fun `Escape closes the drawer`() = window { _, viewState, _ ->
        until { described(RailMenuDescription) }
        onNodeWithContentDescription(RailMenuDescription).performClick()
        until { shown("Clear disconnected") }

        onRoot().performKeyInput { pressKey(Key.Escape) }
        until { !viewState.drawerOpen }
        until { !shown("Clear disconnected") }
    }

    @Test
    fun `after the drawer closes, the arrow keys step through the timeline again`() = window { inspector, _, _ ->
        until { described(RailMenuDescription) }
        inspector.selection.selectScope(taskList)
        until { shown("TaskListVM") }

        onNodeWithContentDescription(RailMenuDescription).performClick()
        until { shown("Clear disconnected") }
        onRoot().performKeyInput { pressKey(Key.Escape) }
        until { !shown("Clear disconnected") }
        assertEquals(null, inspector.selection.state.value.row)

        // The sheet had the keyboard; the centre pane was given it back.
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        until { inspector.selection.state.value.row != null }
    }

    @Test
    fun `a click on the scrim closes the drawer`() = window { _, viewState, _ ->
        until { described(RailMenuDescription) }
        onNodeWithContentDescription(RailMenuDescription).performClick()
        until { shown("Clear disconnected") }

        // The sheet is 260 dp wide, from the left edge: a click to its right is on the scrim.
        onRoot().performTouchInput { click(Offset(700f, 400f)) }
        until { !viewState.drawerOpen }
    }

    @Test
    fun `the rail and the drawer are one SessionsVM, and the pane stays shown while the rail is`() = window { inspector, viewState, _ ->
        until { described(RailMenuDescription) }
        val vm = inspector.viewModel(inspector.sessionsPane())
        assertTrue(runBlocking { inspector.visibility.shown("sessions").first() })

        onNodeWithContentDescription(RailMenuDescription).performClick()
        until { shown("Clear disconnected") }
        assertSame(vm, inspector.viewModel(inspector.sessionsPane()), "the drawer reads the rail's ViewModel")
        assertEquals(1, inspector.viewModels.viewModelStore.keys().count { it == inspector.sessionsPane().storeKey })

        viewState.drawerOpen = false
        until { !shown("Clear disconnected") }
        assertTrue(runBlocking { inspector.visibility.shown("sessions").first() }, "the rail is still there")
    }

    @Test
    fun `dragging the window across the breakpoint keeps the timeline composed, and leaving the compact layout closes the drawer`() =
        window { inspector, viewState, resize ->
            until { described(RailMenuDescription) }
            inspector.selection.selectScope(taskList)
            inspector.selection.selectRow(TimeMachineSequence(208))
            val key = inspector.timelinePane(taskList).key
            until { runBlocking { inspector.visibility.shown(key).first() } }

            // From here, the timeline's pane must never be hidden: that is what a recomposed pane would do.
            val seen = Collections.synchronizedList(mutableListOf<Boolean>())
            val watcher = CoroutineScope(Dispatchers.Default).launch { inspector.visibility.shown(key).collect { seen += it } }
            until { seen.isNotEmpty() }

            viewState.drawerOpen = true
            resize(1440.dp)
            until { shown("Clear disconnected") }
            assertFalse(viewState.drawerOpen, "the full layout has no drawer to leave open")

            resize(1000.dp)
            until { described(RailMenuDescription) }
            waitForIdle()
            watcher.cancel()
            assertEquals(listOf(true), seen.toList(), "the timeline was hidden and shown again while the layout changed")
        }

    @Test
    fun `the right pane folds away, leaves a strip, and unfolds`() = window { inspector, viewState, _ ->
        until { described(RailMenuDescription) }
        inspector.selection.selectScope(taskList)
        inspector.selection.selectRow(TimeMachineSequence(208))
        until { described(HideRightPaneDescription) }
        assertTrue(shown("Current model"))

        onNodeWithContentDescription(HideRightPaneDescription).performClick()
        until { described(ShowRightPaneDescription) }
        assertTrue(viewState.rightCollapsed)
        assertFalse(shown("Current model"), "a folded pane is not composed")
        assertFalse(described(HideRightPaneDescription))

        onNodeWithContentDescription(ShowRightPaneDescription).performClick()
        until { shown("Current model") }
        assertFalse(viewState.rightCollapsed)
    }

    @Test
    fun `Edit and intervene unfolds a folded right pane to show the composer`() = window { inspector, viewState, _ ->
        until { described(RailMenuDescription) }
        inspector.selection.selectScope(taskList)
        inspector.selection.selectRow(TimeMachineSequence(208))
        until { described(HideRightPaneDescription) }
        onNodeWithContentDescription(HideRightPaneDescription).performClick()
        until { viewState.rightCollapsed && described(ShowRightPaneDescription) }

        inspector.selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, "{}")
        until { !viewState.rightCollapsed }
        until { described(HideRightPaneDescription) }
    }

    @Test
    fun `a folded right pane stays folded while the full layout shows it, and folds again when the window narrows`() =
        window { inspector, viewState, resize ->
            until { described(RailMenuDescription) }
            inspector.selection.selectScope(taskList)
            inspector.selection.selectRow(TimeMachineSequence(208))
            until { described(HideRightPaneDescription) }
            onNodeWithContentDescription(HideRightPaneDescription).performClick()
            until { described(ShowRightPaneDescription) }

            resize(1440.dp)
            until { shown("Current model") }
            assertTrue(viewState.rightCollapsed, "the choice is remembered")
            assertFalse(described(ShowRightPaneDescription))

            resize(1000.dp)
            until { described(ShowRightPaneDescription) }
            assertFalse(shown("Current model"))
        }

    @Test
    fun `the compact layout has no row actions, a short reminder and an Edit button`() = window { inspector, _, resize ->
        inspector.selection.selectScope(taskList)
        inspector.selection.selectRow(TimeMachineSequence(208))
        until { shown("Edit & intervene") }
        // Only the Detail tab's: the selected row has none of its own.
        assertEquals(1, onAllNodesWithText("Edit & intervene").fetchSemanticsNodes().size)
        assertTrue(shown(CompactSecurityReminder))
        assertFalse(shown(SecurityReminder))
        assertTrue(shown("Edit"))
        assertFalse(shown("Edit & replace"))

        resize(1440.dp)
        until { shown(SecurityReminder) }
        until { onAllNodesWithText("Edit & intervene").fetchSemanticsNodes().size == 2 }
        assertFalse(shown(CompactSecurityReminder))
        assertTrue(shown("Edit & replace"))
        assertFalse(shown("Edit"))
    }
}
