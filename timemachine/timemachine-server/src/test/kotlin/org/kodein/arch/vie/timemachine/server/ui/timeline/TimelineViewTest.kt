@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.SampleWorld
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.paneTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.viewModel


/**
 * The timeline's view over a live inspector of the main scenario, in a scene 300 px high, which its
 * rows overflow: what only the composition does, the scroll and what follows from it.
 */
class TimelineViewTest {

    private val taskList = InspectorScope.ViewModel(Demo.session.id, Demo.taskList)
    private val settings = InspectorScope.ViewModel(Demo.session.id, Demo.settings)
    private val session = InspectorScope.Session(Demo.session.id)

    /** What only the header of each says: which timeline is composed. A session's rows name TaskListVM with its identifier. */
    private val taskListTitle = "TaskListVM"
    private val sessionTitle = "Session · 4 ViewModels"

    /** The header's chip of the Model's type, which the glyph of a serializable type tells from the filter's chip. */
    private val modelTypeChip = hasText("Model") and hasText("data_object")

    private fun Inspector.follow(scope: InspectorScope): FollowState = viewModel(timelinePane(scope)).modelFlow.value.controls.follow

    /** The sequence the topmost row in view shows, as `#270`. */
    private fun DesktopComposeUiTest.topRow(): String? {
        val list = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        return onAllNodes(hasText("#", substring = true)).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= list.top && it.boundsInRoot.bottom <= list.bottom }
            .minByOrNull { it.boundsInRoot.top }
            ?.config?.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.let(AnnotatedString::text)
    }

    /** An observation's row as the list lays it out: its bounds are the whole row's, even where the list cuts it. */
    private class RowBounds(val sequence: TimeMachineSequence, val top: Float, val bottom: Float)

    private fun DesktopComposeUiTest.observationRows(): List<RowBounds> =
        onAllNodes(hasClickAction() and hasText("#", substring = true)).fetchSemanticsNodes().mapNotNull { node ->
            val sequence = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text?.removePrefix("#")?.toLongOrNull()
            sequence?.let { RowBounds(TimeMachineSequence(it), node.positionInRoot.y, node.positionInRoot.y + node.size.height) }
        }

    /** The rows that an edge of the list cuts. */
    private fun DesktopComposeUiTest.cutRows(): List<RowBounds> {
        val list = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        return observationRows().filter { (it.top < list.top && it.bottom > list.top) || (it.top < list.bottom && it.bottom > list.bottom) }
    }

    /** Fails unless the last row of the timeline of [scope] is whole in the list. */
    private fun DesktopComposeUiTest.assertEndsOnLastRow(inspector: Inspector, scope: InspectorScope) {
        val last = inspector.viewModel(inspector.timelinePane(scope)).modelFlow.value.projection.items.last().sequence
        val list = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val row = observationRows().firstOrNull { it.sequence == last } ?: fail("row #$last, the last one, is not in the list")
        assertTrue(row.top >= list.top && row.bottom <= list.bottom, "row #$last, the last one, is cut: ${row.top}..${row.bottom} in ${list.top}..${list.bottom}")
    }

    @Test
    fun `a timeline shown for the first time ends on its last row`() =
        paneTest(height = 300, content = { TimelineView(session) }) { inspector ->
            until { shown(sessionTitle) && topRow() != null }
            waitForIdle()

            assertEquals(FollowState.Following, inspector.follow(session))
            assertEndsOnLastRow(inspector, session)
        }

    @Test
    fun `a following timeline whose list gets shorter stays on its last row`() {
        var height by mutableStateOf(300)
        paneTest(height = 300, content = { Box(Modifier.height(height.dp)) { TimelineView(session) } }) { inspector ->
            until { shown(sessionTitle) && topRow() != null }
            waitForIdle()

            height = 240
            waitForIdle()

            assertEquals(FollowState.Following, inspector.follow(session))
            assertEndsOnLastRow(inspector, session)
        }
    }

    private fun Inspector.timeline(scope: InspectorScope): TimelineVIE.Model = viewModel(timelinePane(scope)).modelFlow.value

    /** The observations the timeline of [scope] shows, which are the rows a click or an arrow key selects. */
    private fun Inspector.observations(scope: InspectorScope): List<TimeMachineSequence> =
        timeline(scope).projection.items.filter { it.observation != null }.mapNotNull { it.sequence }

    @Test
    fun `after a click on a row, Enter expands the selected row`() =
        paneTest(height = 300, content = { TimelineView(taskList) }) { inspector ->
            inspector.selection.selectScope(taskList)
            until { shown(taskListTitle) && topRow() != null }
            waitForIdle()
            val list = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            val clicked = observationRows().filter { it.top >= list.top && it.bottom <= list.bottom }.minByOrNull { it.top }?.sequence
                ?: fail("no row is whole in the list")
            val observations = inspector.observations(taskList)
            val target = observations[observations.indexOf(clicked) + 2]

            // A click with the mouse, which gives the row the focus.
            onNode(hasClickAction() and hasText("#$clicked")).performClick()
            until { inspector.selection.state.value.row == clicked }
            repeat(2) { onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
            until { inspector.selection.state.value.row == target }

            onRoot().performKeyInput { pressKey(Key.Enter) }
            until { inspector.timeline(taskList).controls.expanded.isNotEmpty() || inspector.selection.state.value.row != target }

            assertEquals(target, inspector.selection.state.value.row, "Enter selected another row")
            assertEquals(setOf(target), inspector.timeline(taskList).controls.expanded)
        }

    @Test
    fun `a row's chevron says what a click on it does, and does it`() =
        paneTest(height = 300, content = { TimelineView(taskList) }) { inspector ->
            inspector.selection.selectScope(taskList)
            until { shown(taskListTitle) && topRow() != null }
            waitForIdle()
            assertTrue(onAllNodesWithContentDescription(CollapseRowDescription).fetchSemanticsNodes().isEmpty())

            // The last row: the timeline ends on it, whole.
            onAllNodesWithContentDescription(ExpandRowDescription).onLast().performClick()

            until { inspector.timeline(taskList).controls.expanded.size == 1 }
            until { onAllNodesWithContentDescription(CollapseRowDescription).fetchSemanticsNodes().size == 1 }
        }

    @Test
    fun `while the search field has the keyboard, the timeline's keys are not taken`() =
        paneTest(height = 300, content = { TimelineView(taskList) }) { inspector ->
            inspector.selection.selectScope(taskList)
            until { shown(taskListTitle) && topRow() != null }
            val selected = inspector.observations(taskList).last()
            inspector.selection.selectRow(selected)
            until { inspector.timeline(taskList).projection.selectedRow == selected }

            onNode(hasSetTextAction()).performClick()
            waitForIdle()
            onRoot().performKeyInput { pressKey(Key.Enter); pressKey(Key.F) }
            // What the keys would have posted comes before this query: once it is there, they have been handled.
            onNode(hasSetTextAction()).performTextInput("x")
            until { inspector.timeline(taskList).controls.filter.query.isNotEmpty() }

            assertEquals(emptySet(), inspector.timeline(taskList).controls.expanded, "Enter expanded the selected row")
            assertEquals(FollowState.Following, inspector.follow(taskList), "F paused the timeline")
        }

    @Test
    fun `a selected row that an edge of the list cuts comes whole into view`() =
        paneTest(height = 300, content = { TimelineView(session) }) { inspector ->
            // A row is only selected in the scope the window has selected.
            inspector.selection.selectScope(session)
            until { shown(sessionTitle) && topRow() != null }
            waitForIdle()
            val list = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot

            // Once at each edge: revealing the first row leaves another one cut.
            repeat(2) {
                val cut = cutRows().maxByOrNull { it.top } ?: fail("no row is cut by an edge of the list")
                inspector.selection.selectRow(cut.sequence)
                until { inspector.viewModel(inspector.timelinePane(session)).modelFlow.value.projection.selectedRow == cut.sequence }
                waitForIdle()

                val row = observationRows().first { it.sequence == cut.sequence }
                assertTrue(row.top >= list.top && row.bottom <= list.bottom, "row #${cut.sequence} is cut: ${row.top}..${row.bottom} in ${list.top}..${list.bottom}")
            }
        }

    @Test
    fun `scrolling the timeline shown second pauses that one, not the first`() {
        var scope by mutableStateOf<InspectorScope>(taskList)
        paneTest(height = 300, content = { TimelineView(scope) }) { inspector ->
            until { shown(taskListTitle) && topRow() != null }
            scope = session
            until { shown(sessionTitle) && topRow() != null }

            onNode(hasScrollAction()).performTouchInput { swipeDown() }
            until { inspector.follow(session) is FollowState.Paused || inspector.follow(taskList) is FollowState.Paused }

            assertEquals(FollowState.Following, inspector.follow(taskList), "the scroll paused the timeline that is not shown")
            assertIs<FollowState.Paused>(inspector.follow(session))
        }
    }

    @Test
    fun `a paused timeline is shown again where it was left`() {
        var scope by mutableStateOf<InspectorScope>(session)
        paneTest(height = 300, content = { TimelineView(scope) }) { inspector ->
            until { shown(sessionTitle) && topRow() != null }
            val followed = topRow()
            onNode(hasScrollAction()).performTouchInput { swipeDown() }
            until { inspector.follow(session) is FollowState.Paused }
            waitForIdle()
            val left = topRow()
            assertNotEquals(null, left)
            assertNotEquals(followed, left, "the swipe moved the list")

            scope = taskList
            until { shown(taskListTitle) && topRow() != null }
            scope = session
            until { shown(sessionTitle) && topRow() != null }
            waitForIdle()

            assertEquals(left, topRow())
            assertIs<FollowState.Paused>(inspector.follow(session))
        }
    }

    @Test
    fun `the type info opens over the timeline, which stays where it is, and Escape or a click outside closes it`() =
        paneTest(height = 500, content = { TimelineView(taskList) }) {
            until { shown(taskListTitle) }
            val columnHeads = onNodeWithText("SEQ").fetchSemanticsNode().boundsInRoot

            onNode(modelTypeChip).performClick()
            until { shown("Type info") }
            assertEquals(columnHeads, onNodeWithText("SEQ").fetchSemanticsNode().boundsInRoot, "opening the type info moved the timeline")
            val chip = onNode(modelTypeChip).fetchSemanticsNode().boundsInWindow
            val title = onNodeWithText("Type info").fetchSemanticsNode().boundsInWindow
            assertTrue(title.top > chip.bottom && title.left < chip.left, "the type info is not under the chips: $title, $chip")

            onNodeWithText("Type info").performKeyInput { pressKey(Key.Escape) }
            until { !shown("Type info") }

            onNode(modelTypeChip).performClick()
            until { shown("Type info") }
            onNodeWithText("SEQ").performClick()
            until { !shown("Type info") }
        }

    @Test
    fun `the type info open on one ViewModel is not on the next`() {
        var scope by mutableStateOf<InspectorScope>(taskList)
        paneTest(height = 500, content = { TimelineView(scope) }) {
            until { shown(taskListTitle) }
            onNode(modelTypeChip).performClick()
            until { shown("Type info") }

            scope = settings
            until { shown("SettingsVM") }
            waitForIdle()

            assertFalse(shown("Type info"), "the type info of the first ViewModel is still open")
        }
    }

    /** What the row of a missing range says of whose entries they may be, which depends on the scope. */
    private val lostInViewModel = "entries were lost in this session here; some may belong to this ViewModel"
    private val lostInSession = "entries were lost in this session here; they may belong to any of its ViewModels"

    /** Hides every observation of [scope]: its markers always pass, and a tall scene then composes them all. */
    private fun DesktopComposeUiTest.showOnlyMarkers(inspector: Inspector, scope: InspectorScope) {
        inspector.viewModel(inspector.timelinePane(scope)).post(TimelineVIE.Intent.SetQuery("no row has this"))
        until { inspector.timeline(scope).projection.let { it.filterActive && it.items.none { item -> item.observation != null } } }
        waitForIdle()
    }

    @Test
    fun `a missing range names this ViewModel in a ViewModel's timeline, and the session's ViewModels in a session's`() {
        paneTest(height = 1200, content = { TimelineView(taskList) }) { inspector ->
            until { shown(taskListTitle) }
            showOnlyMarkers(inspector, taskList)

            until { shown(lostInViewModel) }
        }
        paneTest(height = 1200, content = { TimelineView(session) }) { inspector ->
            until { shown(sessionTitle) }
            showOnlyMarkers(inspector, session)

            until { shown("Possibly incomplete") }
            assertFalse(shown(lostInViewModel), "a session's timeline has no \"this ViewModel\"")
            assertTrue(shown(lostInSession))
        }
    }

    @Test
    fun `one evicted entry is counted in the singular`() {
        // The earlier session's TaskListVM had three rows: a bound of two evicts one.
        val earlierTaskList = InspectorScope.ViewModel(Demo.oldSession.id, Demo.oldTaskList)
        paneTest(data = ScenarioData("main", SampleWorld.events(), maxRows = 2), content = { TimelineView(earlierTaskList) }) { inspector ->
            until { inspector.timeline(earlierTaskList).projection.items.isNotEmpty() }
            waitForIdle()

            assertFalse(shown("1 older entries evicted · timeline size limit"))
            assertTrue(shown("1 older entry evicted · timeline size limit"))
        }
    }
}
