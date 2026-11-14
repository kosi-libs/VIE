@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/**
 * How a serial name gives way when it has not the room: its prefix first, cut behind an ellipsis, then
 * its last segment. What it laid out is read from its semantics, which name it whole.
 */
class SerialNameTest {

    // 58 characters of JetBrains Mono at 12 px: about 420 px on one line.
    private val name = "org.kodein.arch.vie.demo.vie.TaskListVIE.Intent.DeleteTask"

    /** What [SerialName] lays out of [name] in [width] px and [maxLines] lines. */
    private fun laidOut(width: Int, maxLines: Int = 1, block: (TextLayoutResult) -> Unit) = contentTest(
        width = 700, height = 200,
        content = { Box(Modifier.width(width.dp)) { SerialName(name, InspectorTheme.text.code, maxLines = maxLines) } },
    ) {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(name).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        block(results.single())
    }

    @Test
    fun `a name that fits is laid out whole`() = laidOut(width = 640) { result ->
        assertEquals(name, result.layoutInput.text.text)
        assertEquals(1, result.lineCount)
    }

    @Test
    fun `a name too long for its line gives up the end of its prefix first`() = laidOut(width = 300) { result ->
        val text = result.layoutInput.text.text
        assertTrue(text.endsWith("…DeleteTask"), text)
        assertTrue(name.startsWith(text.substringBefore('…')), text)
        assertTrue(text.length < name.length, text)
        assertFalse(result.hasVisualOverflow, "what is kept fits")
    }

    @Test
    fun `a last segment too long for the line alone is cut at its end, with nothing of the prefix`() = laidOut(width = 50) { result ->
        assertEquals("DeleteTask", result.layoutInput.text.text)
        assertTrue(result.hasVisualOverflow, "the last segment is cut")
    }

    @Test
    fun `on more lines, the prefix still gives way first`() = laidOut(width = 150, maxLines = 2) { result ->
        val text = result.layoutInput.text.text
        assertTrue(text.endsWith("…DeleteTask"), text)
        assertEquals(2, result.lineCount)
        assertFalse(result.hasVisualOverflow, "what is kept fits in two lines")
    }
}
