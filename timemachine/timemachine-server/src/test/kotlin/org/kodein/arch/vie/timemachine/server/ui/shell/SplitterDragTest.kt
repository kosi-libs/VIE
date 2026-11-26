@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.countingRecompositions
import org.kodein.arch.vie.timemachine.server.fixtures.described
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.fixtures.windowTest
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.sessions.RailMenuDescription
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens


/**
 * The splitters of the real [org.kodein.arch.vie.timemachine.server.InspectorWindow]: a drag is several
 * movements, and those that arrive between two frames all count. The harness's density is 1, so a dp is a px.
 *
 * The window tests do not tell the fix from the code before it: the harness composes between two movements,
 * which a real pointer, faster than the frames, does not leave time for. What a callback holds from the last
 * composition is what the last tests, on the functions the callbacks call, pin.
 */
class SplitterDragTest {

    /** Presses at [x] over the splitter, moves by each of [steps] in turn, all before the next frame, and releases. */
    private fun DesktopComposeUiTest.drag(x: Dp, vararg steps: Float) {
        onRoot().performMouseInput {
            moveTo(Offset(x.value + InspectorDimens.dividerHitArea.value / 2, 450f))
            press()
            steps.forEach { moveBy(Offset(it, 0f)) }
            release()
        }
        waitForIdle()
    }

    @Test
    fun `movements that arrive between two frames all move the sessions splitter`() = windowTest(width = 1440.dp) { _, viewState, _ ->
        until { shown("Clear disconnected") }
        val start = viewState.sessionsWidth

        drag(start, 40f)
        val once = viewState.sessionsWidth
        assertTrue(once > start, "the splitter did not move: $start, $once")

        viewState.sessionsWidth = start
        waitForIdle()
        drag(start, 10f, 10f, 10f, 10f)

        assertEquals(once, viewState.sessionsWidth, "four movements of 10 gave another width than one of 40")
    }

    @Test
    fun `movements that arrive between two frames all move the right splitter`() = windowTest(width = 1440.dp) { _, viewState, _ ->
        until { shown("Clear disconnected") }
        val start = viewState.rightWidth
        val splitterX = 1440.dp - start - InspectorDimens.dividerHitArea

        drag(splitterX, -40f)
        val once = viewState.rightWidth
        assertTrue(once > start, "the splitter did not move: $start, $once")

        viewState.rightWidth = start
        waitForIdle()
        drag(splitterX, -10f, -10f, -10f, -10f)

        assertEquals(once, viewState.rightWidth, "four movements of 10 gave another width than one of 40")
    }

    @Test
    fun `movements that arrive between two frames all move the right splitter of the compact layout`() = windowTest(width = 1000.dp) { _, viewState, _ ->
        until { described(RailMenuDescription) }
        waitForIdle()
        val start = viewState.compactRightWidth
        val splitterX = 1000.dp - start - InspectorDimens.dividerHitArea

        drag(splitterX, -40f)
        val once = viewState.compactRightWidth
        assertTrue(once > start, "the splitter did not move: $start, $once")

        viewState.compactRightWidth = start
        waitForIdle()
        drag(splitterX, -10f, -10f, -10f, -10f)

        assertEquals(once, viewState.compactRightWidth, "four movements of 10 gave another width than one of 40")
    }

    // ---- What a drag recomposes ----------------------------------------------------------------------

    @Test
    fun `dragging a splitter lays the panes out again and composes none of the layout`() = countingRecompositions(PanesLayout, CompactPanes) { recompositions ->
        windowTest(width = 1440.dp) { _, viewState, _ ->
            until { shown("Clear disconnected") }
            waitForIdle()
            val start = viewState.sessionsWidth
            recompositions.reset()

            drag(start, 10f, 10f, 10f, 10f)

            assertTrue(viewState.sessionsWidth > start, "the splitter did not move")
            assertEquals(0, recompositions[PanesLayout], "the width is read in composition: each movement composes the three panes' roots")
        }
        windowTest(width = 1000.dp) { _, viewState, _ ->
            until { described(RailMenuDescription) }
            waitForIdle()
            val start = viewState.compactRightWidth
            recompositions.reset()

            drag(1000.dp - start - InspectorDimens.dividerHitArea, -10f, -10f, -10f, -10f)

            assertTrue(viewState.compactRightWidth > start, "the splitter did not move")
            assertEquals(0, recompositions[CompactPanes], "the width is read in composition: each movement composes the panes' roots")
        }
    }

    // ---- What a movement starts from ---------------------------------------------------------------

    private val total = 1440.dp

    @Test
    fun `a splitter starts each movement from the width the last one gave, with no composition between`() {
        val viewState = InspectorViewState()
        val sessions = viewState.sessionsWidth
        repeat(4) { viewState.dragSessions(10.dp, total) }
        assertEquals(sessions + 40.dp, viewState.sessionsWidth)

        val right = viewState.rightWidth
        repeat(4) { viewState.dragRight(-10.dp, total) }
        assertEquals(right + 40.dp, viewState.rightWidth)

        val compact = viewState.compactRightWidth
        repeat(4) { viewState.dragCompactRight(-10.dp, total) }
        assertEquals(compact + 40.dp, viewState.compactRightWidth)
    }

    @Test
    fun `a splitter drags from the width shown, not from one that a narrow window clamped`() {
        val viewState = InspectorViewState()
        viewState.sessionsWidth = 600.dp
        // The right pane and the centre pane's 300 dp leave 380 dp.
        val narrow = 1100.dp
        val shown = viewState.shownSessionsWidth(narrow)
        assertTrue(shown < 600.dp, "the window leaves less than the width asked for: $shown")

        viewState.dragSessions((-10).dp, narrow)

        assertEquals(shown - 10.dp, viewState.sessionsWidth, "the first movement back moves the splitter")
    }

    @Test
    fun `a drag stops at the least and at what the others leave`() {
        val viewState = InspectorViewState()
        viewState.dragSessions((-1000).dp, total)
        assertEquals(InspectorDimens.minSessionsPane, viewState.sessionsWidth)

        viewState.dragSessions(1000.dp, total)
        assertEquals(total - viewState.shownRightWidth(total) - 300.dp, viewState.sessionsWidth, "the centre pane keeps its 300 dp")
    }

    private companion object {
        /** As the compiler names them for the tracer. */
        const val PanesLayout = "org.kodein.arch.vie.timemachine.server.ui.shell.PanesLayout"
        const val CompactPanes = "org.kodein.arch.vie.timemachine.server.ui.shell.CompactPanes"
    }
}
