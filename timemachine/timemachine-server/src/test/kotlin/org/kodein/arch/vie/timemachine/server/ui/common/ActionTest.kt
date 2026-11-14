@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.ui.designsystem.LatchedInteractionSource
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape


/**
 * What an action shows under the pointer keeps to its shape: hovered or pressed, its inside is tinted, and
 * the corners its shape cuts off stay as they were. The states are emitted on the element's interaction
 * source: no pointer moves.
 */
class ActionTest {
    private val tag = "action"
    private val width = 120
    private val height = 26

    /** [element] on the panes' surface, in a window just larger than it. */
    private fun surfaceTest(element: @Composable () -> Unit, block: suspend DesktopComposeUiTest.() -> Unit) = contentTest(
        width = 200, height = 60,
        content = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp)) { element() } },
        block = block,
    )

    /** An element of [width] by [height] px with [action], on the panes' surface. */
    private fun actionTest(action: Modifier, block: suspend DesktopComposeUiTest.() -> Unit) =
        surfaceTest({ Box(Modifier.testTag(tag).size(width.dp, height.dp).then(action)) }, block)

    private fun DesktopComposeUiTest.pixels(): PixelMap = onNodeWithTag(tag).captureToImage().toPixelMap()

    @Test
    fun `a hovered action with no shape is tinted to its corners`() {
        val source = LatchedInteractionSource()
        actionTest(Modifier.action(interactionSource = source) {}) {
            val rest = pixels()
            source.emit(HoverInteraction.Enter())
            waitForIdle()
            val hovered = pixels()
            assertNotEquals(rest[width / 2, height / 2], hovered[width / 2, height / 2], "the inside is not tinted")
            assertNotEquals(rest[1, 1], hovered[1, 1], "the corner is not tinted")
        }
    }

    @Test
    fun `a hovered action is tinted inside its shape, not in the corners it cuts off`() {
        val source = LatchedInteractionSource()
        actionTest(Modifier.action(shape = InspectorPillShape, interactionSource = source) {}) {
            val rest = pixels()
            source.emit(HoverInteraction.Enter())
            waitForIdle()
            assertTintedInsideNotInCorners(rest, pixels())
        }
    }

    @Test
    fun `a pressed action is tinted inside its shape, not in the corners it cuts off`() {
        val source = LatchedInteractionSource()
        actionTest(Modifier.action(shape = InspectorPillShape, interactionSource = source) {}) {
            val rest = pixels()
            source.emit(PressInteraction.Press(Offset(width / 2f, height / 2f)))
            waitForIdle()
            assertTintedInsideNotInCorners(rest, pixels())
        }
    }

    /** [shown] differs from [rest] at the centre of the element, and at neither of its top corners. */
    private fun assertTintedInsideNotInCorners(rest: PixelMap, shown: PixelMap) {
        assertNotEquals(rest[width / 2, height / 2], shown[width / 2, height / 2], "the inside is not tinted")
        assertEquals(rest[1, 1], shown[1, 1], "the top left corner is tinted")
        assertEquals(rest[width - 2, 1], shown[width - 2, 1], "the top right corner is tinted")
    }
}
