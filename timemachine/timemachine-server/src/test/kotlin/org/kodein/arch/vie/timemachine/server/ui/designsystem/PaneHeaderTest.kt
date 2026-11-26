@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/** What a pane header is: as high as its pane header, across its width, its actions at its end, and its count the first to give way. Sizes are in px: the harness's density is 1. */
class PaneHeaderTest {

    private val position = "#208 · 12:00:01.123"

    @Test
    fun `a header is as high as a pane header and as wide as its pane, and says its title and its count`() =
        contentTest(width = 600, content = { PaneHeader("Timeline", Modifier.testTag("header"), count = position) }) {
            val size = onNodeWithTag("header").fetchSemanticsNode().size
            assertEquals(40, size.height)
            assertEquals(600, size.width)
            assertTrue(shown("Timeline"))
            assertTrue(shown(position))
        }

    @Test
    fun `a header with no count says its title only`() =
        contentTest(content = { PaneHeader("Timeline") }) {
            assertTrue(shown("Timeline"))
            assertEquals(emptyList(), listOf(position).filter { shown(it) })
        }

    @Test
    fun `the actions are at the header's end, with a count and without`() {
        for (count in listOf(position, null)) {
            contentTest(width = 600, content = {
                PaneHeader("Timeline", count = count, glyph = Glyph.Info) { IconAction(Glyph.Close, "close", onClick = {}) }
            }) {
                // Its 26 px target, then the 8 px the header keeps after its last action.
                assertEquals(592f, onNodeWithContentDescription("close").fetchSemanticsNode().boundsInRoot.right, absoluteTolerance = 1f, message = "with count $count")
            }
        }
    }

    @Test
    fun `in a header without room, the count gives way and the title does not`() {
        data class Widths(val title: Int, val count: Int, val actionLeft: Float, val countRight: Float)

        fun widths(width: Int): Widths {
            lateinit var seen: Widths
            contentTest(width = width, content = {
                PaneHeader("Timeline", count = position) { IconAction(Glyph.Close, "close", onClick = {}) }
            }) {
                val count = onNodeWithText(position).fetchSemanticsNode()
                seen = Widths(
                    title = onNodeWithText("Timeline").fetchSemanticsNode().size.width,
                    count = count.size.width,
                    actionLeft = onNodeWithContentDescription("close").fetchSemanticsNode().boundsInRoot.left,
                    countRight = count.boundsInRoot.right,
                )
            }
            return seen
        }

        val roomy = widths(600)
        val tight = widths(200)
        assertEquals(roomy.title, tight.title, "the title is cut or squeezed")
        assertTrue(tight.count < roomy.count, "the count did not give way: ${tight.count} px in 200, ${roomy.count} in 600")
        assertTrue(tight.countRight <= tight.actionLeft, "the count runs under the action")
    }
}
