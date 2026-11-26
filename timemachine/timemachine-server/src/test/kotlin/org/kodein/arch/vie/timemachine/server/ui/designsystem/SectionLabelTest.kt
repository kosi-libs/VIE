@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest


/** What a section label is: on one line, cut with an ellipsis when it has not the room, and aligned as it is told. Sizes are in px: the harness's density is 1. */
class SectionLabelTest {

    /** The layout of the text of [this]: where its line starts and ends, whether it was cut. */
    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    @Test
    fun `a label that has not the room stays on one line, and is cut with an ellipsis`() =
        contentTest(content = {
            Column {
                SectionLabel("SEQ", Modifier.testTag("short"))
                SectionLabel("RECENT INTERVENTIONS", Modifier.testTag("long").width(40.dp))
            }
        }) {
            val short = onNodeWithTag("short")
            val long = onNodeWithTag("long")
            val cut = long.layout()
            assertEquals(1, cut.lineCount, "a label wrapped")
            assertTrue(cut.getLineEnd(0, visibleEnd = true) < "RECENT INTERVENTIONS".length, "the label was not cut")
            // Skia does not say which line it ellipsized (`isLineEllipsized` is always false): the layout is asked to.
            assertEquals(TextOverflow.Ellipsis, cut.layoutInput.overflow)
            assertEquals(1, cut.layoutInput.maxLines)
            assertEquals(short.fetchSemanticsNode().size.height, long.fetchSemanticsNode().size.height, "as high as a label on one line")
            assertEquals(40, long.fetchSemanticsNode().size.width)
        }

    @Test
    fun `a label that has the room is whole`() =
        contentTest(content = { SectionLabel("RECENT INTERVENTIONS", Modifier.testTag("label")) }) {
            val whole = onNodeWithTag("label").layout()
            assertEquals("RECENT INTERVENTIONS".length, whole.getLineEnd(0, visibleEnd = true))
        }

    @Test
    fun `a label sits where it is told in a width it is given`() =
        contentTest(content = {
            Column {
                SectionLabel("SEQ", Modifier.testTag("start").width(200.dp))
                SectionLabel("SEQ", Modifier.testTag("end").width(200.dp), textAlign = TextAlign.End)
                SectionLabel("SEQ", Modifier.testTag("centre").width(200.dp), textAlign = TextAlign.Center)
            }
        }) {
            val start = onNodeWithTag("start").layout()
            val end = onNodeWithTag("end").layout()
            val centre = onNodeWithTag("centre").layout()
            assertEquals(0f, start.getLineLeft(0), absoluteTolerance = 0.5f, message = "by default, at the start")
            assertEquals(200f, end.getLineRight(0), absoluteTolerance = 0.5f, message = "at the end")
            assertEquals(100f, (centre.getLineLeft(0) + centre.getLineRight(0)) / 2, absoluteTolerance = 0.5f, message = "in the centre")
        }
}
