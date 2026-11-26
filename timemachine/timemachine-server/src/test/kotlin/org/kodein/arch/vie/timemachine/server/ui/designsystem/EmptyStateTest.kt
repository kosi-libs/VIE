@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/** What an empty state shows: its title always, the rest only when given, one under the other and centred. Sizes are in px: the harness's density is 1. */
class EmptyStateTest {

    @Test
    fun `an empty state is its title, and shows a description and an action only when it has them`() {
        contentTest(content = { EmptyState("No rows") }) {
            assertTrue(shown("No rows"))
            assertEquals(emptyList(), listOf("Click a row", "Clear filters").filter { shown(it) })
        }

        var clicks = 0
        contentTest(content = {
            EmptyState("No rows", description = "Click a row", action = { InspectorButton("Clear filters", onClick = { clicks++ }) })
        }) {
            assertEquals(listOf(true, true, true), listOf("No rows", "Click a row", "Clear filters").map { shown(it) })
            onNodeWithText("Clear filters").performClick()
            assertEquals(1, clicks)
        }
    }

    @Test
    fun `the title, the description and the action are one under the other, centred in the width`() {
        contentTest(
            width = 600,
            content = {
                EmptyState("No rows", Modifier.fillMaxWidth(), description = "Click a row", action = { InspectorButton("Clear filters", onClick = {}) })
            },
        ) {
            val bounds = listOf("No rows", "Click a row", "Clear filters").map { onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
            assertEquals(bounds, bounds.sortedBy { it.top }, "from the top: the title, the description, the action")
            bounds.forEachIndexed { index, it ->
                assertEquals(300f, (it.left + it.right) / 2, absoluteTolerance = 1f, message = "the centre of line ${index + 1}")
            }
            assertTrue(bounds[0].bottom <= bounds[1].top && bounds[1].bottom <= bounds[2].top, "the lines do not overlap")
        }
    }

    @Test
    fun `an empty state stacks at the top of what it is given, below its margin and its glyph if it has one`() {
        contentTest(content = { EmptyState("No rows") }) {
            assertEquals(40f, onNodeWithText("No rows").fetchSemanticsNode().boundsInRoot.top, absoluteTolerance = 0.5f, message = "the vertical margin")
        }
        contentTest(content = { EmptyState("No rows", glyph = Glyph.Info) }) {
            // The margin, the 28 px glyph and the gap of 8.
            assertEquals(76f, onNodeWithText("No rows").fetchSemanticsNode().boundsInRoot.top, absoluteTolerance = 0.5f, message = "under the glyph")
        }
    }
}
