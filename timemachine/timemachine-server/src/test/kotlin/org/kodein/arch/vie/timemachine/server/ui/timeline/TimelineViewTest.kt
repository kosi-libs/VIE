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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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
import org.kodein.arch.vie.timemachine.server.fixtures.paneTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.viewModel
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


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
    private val modelTypeChip = hasText("Model") and hasText(Glyph.DataObject.ligature)

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
    fun `showing a selected row that the filters hide scrolls to it`() = showsTheHiddenRow(hiddenIndex = 0)

    @Test
    fun `showing a hidden row from the middle of the list scrolls to it`() = showsTheHiddenRow(hiddenIndex = 9)

    /**
     * Filters the TaskListVM's timeline down to its `DeleteTask` rows, selects the [hiddenIndex]th of the
     * others, which that hides, and shows it: it has to come whole into view.
     */
    private fun showsTheHiddenRow(hiddenIndex: Int) =
        paneTest(height = 300, content = { TimelineView(taskList) }) { inspector ->
            inspector.selection.selectScope(taskList)
            until { shown(taskListTitle) && topRow() != null }
            waitForIdle()
            val projection = { inspector.viewModel(inspector.timelinePane(taskList)).modelFlow.value.projection }
            val first = projection().items
                .filter { it.observation?.typeName?.contains("DeleteTask") == false }[hiddenIndex].sequence ?: fail("no observation")

            // A query that this row does not match: the timeline stays on its end, following.
            onNode(hasSetTextAction()).performTextInput("DeleteTask")
            until { projection().hidden > 0 }
            waitForIdle()
            assertEquals(FollowState.Following, inspector.follow(taskList))

            inspector.selection.selectRow(first)
            until { projection().selectedHidden }
            waitForIdle()
            assertTrue(observationRows().none { it.sequence == first }, "row #$first is in the list, which the filter hides")

            onNode(hasClickAction() and hasText("hidden by filters", substring = true)).performClick()
            until { !projection().selectedHidden }
            waitForIdle()

            val list = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            val row = observationRows().firstOrNull { it.sequence == first } ?: fail("row #$first is not in view: the list did not scroll to it (follow=${inspector.follow(taskList)}, top=${topRow()}, items=${projection().items.take(3).map { it.sequence }}..${projection().items.last().sequence})")
            assertTrue(row.top >= list.top && row.bottom <= list.bottom, "row #$first is cut: ${row.top}..${row.bottom} in ${list.top}..${list.bottom}")
        }

    @Test
    fun `revealing the last row of a paused timeline does not resume following`() =
        paneTest(height = 300, content = { TimelineView(session) }) { inspector ->
            inspector.selection.selectScope(session)
            until { shown(sessionTitle) && topRow() != null }
            waitForIdle()
            onNode(hasScrollAction()).performTouchInput { swipeDown() }
            until { inspector.follow(session) is FollowState.Paused }
            waitForIdle()

            // What stepping down onto the last row does: select it, and the list scrolls to reveal it, to its end.
            val projection = { inspector.viewModel(inspector.timelinePane(session)).modelFlow.value.projection }
            val last = projection().items.last { it.observation != null }.sequence ?: fail("no observation")
            inspector.selection.selectRow(last)
            until { projection().selectedRow == last }
            waitForIdle()

            val list = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            val row = observationRows().firstOrNull { it.sequence == last } ?: fail("row #$last is not in view: the list did not scroll to it")
            assertTrue(row.top >= list.top && row.bottom <= list.bottom, "row #$last is cut: ${row.top}..${row.bottom} in ${list.top}..${list.bottom}")
            assertIs<FollowState.Paused>(inspector.follow(session), "the scroll the code started resumed following")
        }

    @Test
    fun `the developer scrolling back to the end of a paused timeline resumes following`() =
        paneTest(height = 300, content = { TimelineView(session) }) { inspector ->
            until { shown(sessionTitle) && topRow() != null }
            waitForIdle()
            onNode(hasScrollAction()).performTouchInput { swipeDown() }
            until { inspector.follow(session) is FollowState.Paused }
            waitForIdle()

            // A swipe may not reach the end of a long list in one go.
            repeat(10) {
                if (inspector.follow(session) !is FollowState.Following) {
                    onNode(hasScrollAction()).performTouchInput { swipeUp() }
                    waitForIdle()
                }
            }

            assertEquals(FollowState.Following, inspector.follow(session))
        }

    /** A click on the row of [sequence], which is in view: the mouse gives it the focus. */
    private fun DesktopComposeUiTest.clickRow(sequence: TimeMachineSequence) {
        onNode(hasClickAction() and hasText("#$sequence")).performClick()
    }

    private fun DesktopComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun `the arrow keys keep working after the row that was clicked scrolled out of view`() =
        paneTest(height = 300, content = { TimelineView(session) }) { inspector ->
            inspector.selection.selectScope(session)
            until { shown(sessionTitle) && topRow() != null }
            waitForIdle()
            val projection = { inspector.viewModel(inspector.timelinePane(session)).modelFlow.value.projection }
            val rows = projection().items.filter { it.observation != null }.mapNotNull { it.sequence }
            val clicked = rows.last()
            clickRow(clicked)
            until { projection().selectedRow == clicked }

            // Each step reveals the row it selects, so the clicked one leaves the list's viewport.
            val steps = minOf(rows.lastIndex, 15)
            repeat(steps) { press(Key.DirectionUp) }

            // A list keeps composed the row that has the focus, wherever it is: it is its place that tells.
            val list = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            assertTrue(
                observationRows().none { it.sequence == clicked && it.bottom > list.top && it.top < list.bottom },
                "row #$clicked, which was clicked, is still in view",
            )
            assertEquals(rows[rows.lastIndex - steps], projection().selectedRow, "the keys stopped acting after the clicked row left the viewport")
        }

    @Test
    fun `the arrow keys keep working after the row that was clicked leaves the list`() =
        paneTest(height = 300, content = { TimelineView(taskList) }) { inspector ->
            inspector.selection.selectScope(taskList)
            until { shown(taskListTitle) && topRow() != null }
            waitForIdle()
            val timeline = inspector.viewModel(inspector.timelinePane(taskList))
            val projection = { timeline.modelFlow.value.projection }
            // A row in view that the filter below hides.
            val hiddenSoon = projection().items
                .filter { it.observation?.typeName?.contains("DeleteTask") == false }
                .mapNotNull { it.sequence }
                .first { sequence -> observationRows().any { it.sequence == sequence } }
            clickRow(hiddenSoon)
            until { projection().selectedRow == hiddenSoon }

            // Set by the code, not typed: the search field would take the focus.
            timeline.post(TimelineVIE.Intent.SetQuery("DeleteTask"))
            until { projection().selectedHidden }
            waitForIdle()
            val first = projection().items.first { it.observation != null }.sequence
            press(Key.DirectionDown)

            assertEquals(first, projection().selectedRow, "the keys stopped acting after the clicked row (#$hiddenSoon) left the list")
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
}
