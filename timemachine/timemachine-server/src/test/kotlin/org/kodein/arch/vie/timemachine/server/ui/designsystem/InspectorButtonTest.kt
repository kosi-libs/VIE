@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.ui.theme.LightInspectorScheme


/**
 * What a button is: as high as its size, whatever its text; disabled, a button that takes no click and has
 * the same fill whatever its variant; and, as a text button, tinted under the pointer inside its pill only.
 * Sizes are in px: the harness's density is 1.
 */
class InspectorButtonTest {

    @Test
    fun `a button is as high as its size`() =
        contentTest(content = {
            Column {
                InspectorButton("Edit & intervene", onClick = {}, size = ButtonSize.Small)
                InspectorButton("Edit & replace", onClick = {}, size = ButtonSize.Medium)
                InspectorButton("Replace model", onClick = {}, size = ButtonSize.Large)
            }
        }) {
            assertEquals(
                listOf(22, 26, 34),
                listOf("Edit & intervene", "Edit & replace", "Replace model").map { onNodeWithText(it).fetchSemanticsNode().size.height },
            )
        }

    @Test
    fun `a disabled button takes no click, and every filled or tonal one has the same fill`() {
        var clicks = 0
        contentTest(content = {
            Column {
                InspectorButton("Filled", onClick = { clicks++ }, modifier = Modifier.testTag("filled"), enabled = false)
                InspectorButton("Tonal", onClick = { clicks++ }, modifier = Modifier.testTag("tonal"), variant = ButtonVariant.Tonal, enabled = false)
            }
        }) {
            onNodeWithText("Filled").assertIsNotEnabled().performClick()
            assertEquals(0, clicks)
            // In the end margin, inside the pill, where nothing is written.
            val fills = listOf("filled", "tonal").map { tag -> onNodeWithTag(tag).captureToImage().toPixelMap().let { it[it.width - 3, it.height / 2] } }
            assertEquals(listOf(LightInspectorScheme.surfaceContainerHighest, LightInspectorScheme.surfaceContainerHighest), fills)
        }
    }

    @Test
    fun `a hovered text button is tinted inside its pill only`() {
        val source = LatchedInteractionSource()
        // On the panes' surface, in a window just larger than the button.
        contentTest(
            width = 200, height = 60,
            content = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp)) {
                    InspectorButton("Clear filters", onClick = {}, modifier = Modifier.testTag("button"), variant = ButtonVariant.Text, interactionSource = source)
                }
            },
        ) {
            val rest = onNodeWithTag("button").captureToImage().toPixelMap()
            source.emit(HoverInteraction.Enter())
            waitForIdle()
            val hovered = onNodeWithTag("button").captureToImage().toPixelMap()
            val w = hovered.width
            val h = hovered.height
            // In the pill, in its end margin to the right of the text; and in the corners the pill cuts off.
            assertNotEquals(rest[w - 3, h / 2], hovered[w - 3, h / 2], "the inside is not tinted")
            assertEquals(rest[1, 1], hovered[1, 1], "the top left corner is tinted")
            assertEquals(rest[w - 2, 1], hovered[w - 2, 1], "the top right corner is tinted")
        }
    }
}
