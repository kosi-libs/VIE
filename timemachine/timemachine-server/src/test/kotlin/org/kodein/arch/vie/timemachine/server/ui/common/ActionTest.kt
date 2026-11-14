@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.kodein.arch.vie.timemachine.server.fixtures.VisibleLevels
import org.kodein.arch.vie.timemachine.server.fixtures.assertDarkened
import org.kodein.arch.vie.timemachine.server.fixtures.assertLightened
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.designsystem.LatchedInteractionSource
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape


/**
 * What an action shows under the pointer keeps to its shape: hovered or pressed, its inside is tinted, and
 * the corners its shape cuts off stay as they were. The tint is visible in both themes, hovered, pressed or
 * focused: it darkens the light theme's surface and lightens the dark theme's by at least [VisibleLevels]
 * levels of 255 (measured hovered and pressed: 20 and 26 darker in the light theme, 16 and 20 lighter in the
 * dark one, where Material's black gave 2 to 3), and it follows the theme it is switched to. The states are
 * emitted on the element's interaction source: no pointer moves.
 */
class ActionTest {
    private val tag = "action"
    private val width = 120
    private val height = 26

    /** [element] on the panes' surface, in a window just larger than it, in the theme [viewState] says. */
    private fun surfaceTest(
        element: @Composable () -> Unit,
        viewState: InspectorViewState = InspectorViewState(),
        block: suspend DesktopComposeUiTest.() -> Unit,
    ) = contentTest(
        width = 200, height = 60, viewState = viewState,
        content = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp)) { element() } },
        block = block,
    )

    /** An element of [width] by [height] px with [action], on the panes' surface, in the theme [viewState] says. */
    private fun actionTest(
        action: Modifier,
        viewState: InspectorViewState = InspectorViewState(),
        block: suspend DesktopComposeUiTest.() -> Unit,
    ) = surfaceTest({ Box(Modifier.testTag(tag).size(width.dp, height.dp).then(action)) }, viewState, block)

    private fun DesktopComposeUiTest.pixels(): PixelMap = onNodeWithTag(tag).captureToImage().toPixelMap()

    private fun darkTheme() = InspectorViewState().apply { dark = true }

    private fun DesktopComposeUiTest.centre(): Color = pixels()[width / 2, height / 2]

    /** A pixel of the scene's margin, above and to the left of the element, where only the panes' surface is painted. */
    private fun DesktopComposeUiTest.margin(): Color = onRoot().captureToImage().toPixelMap()[2, 2]

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

    @Test
    fun `in the light theme, a hovered action darkens the surface under it`() {
        val source = LatchedInteractionSource()
        actionTest(Modifier.action(interactionSource = source) {}) {
            val rest = centre()
            source.emit(HoverInteraction.Enter())
            waitForIdle()
            assertDarkened(rest, centre())
        }
    }

    @Test
    fun `in the light theme, a pressed action darkens the surface under it`() {
        val source = LatchedInteractionSource()
        actionTest(Modifier.action(interactionSource = source) {}) {
            val rest = centre()
            source.emit(PressInteraction.Press(Offset(width / 2f, height / 2f)))
            waitForIdle()
            assertDarkened(rest, centre())
        }
    }

    @Test
    fun `in the light theme, a focused action darkens the surface under it`() {
        val source = LatchedInteractionSource()
        actionTest(Modifier.action(interactionSource = source) {}) {
            val rest = centre()
            source.emit(FocusInteraction.Focus())
            waitForIdle()
            assertDarkened(rest, centre())
        }
    }

    @Test
    fun `in the dark theme, a hovered action lightens the surface under it`() {
        val source = LatchedInteractionSource()
        actionTest(Modifier.action(interactionSource = source) {}, darkTheme()) {
            val rest = centre()
            source.emit(HoverInteraction.Enter())
            waitForIdle()
            assertLightened(rest, centre())
        }
    }

    @Test
    fun `in the dark theme, a pressed action lightens the surface under it`() {
        val source = LatchedInteractionSource()
        actionTest(Modifier.action(interactionSource = source) {}, darkTheme()) {
            val rest = centre()
            source.emit(PressInteraction.Press(Offset(width / 2f, height / 2f)))
            waitForIdle()
            assertLightened(rest, centre())
        }
    }

    @Test
    fun `in the dark theme, a focused action lightens the surface under it`() {
        val source = LatchedInteractionSource()
        actionTest(Modifier.action(interactionSource = source) {}, darkTheme()) {
            val rest = centre()
            source.emit(FocusInteraction.Focus())
            waitForIdle()
            assertLightened(rest, centre())
        }
    }

    @Test
    fun `an action under the pointer as the theme turns dark shows it in the dark theme's colour`() {
        // A source with no replay: were the switch to recreate the action's indication, the hover would be lost.
        val source = MutableInteractionSource()
        val viewState = InspectorViewState()
        actionTest(Modifier.action(interactionSource = source) {}, viewState) {
            source.emit(HoverInteraction.Enter())
            waitForIdle()
            runOnIdle { viewState.dark = true }
            waitForIdle()
            // Against the surface the scene paints now, not against the dark scheme's value: the margin moves with the
            // theme, so the hover is told from the switch itself. A tint, not a repaint: under three times what shows.
            assertLightened(margin(), centre(), most = 3 * VisibleLevels - 1)
        }
    }

    /** [shown] differs from [rest] at the centre of the element, and at neither of its top corners. */
    private fun assertTintedInsideNotInCorners(rest: PixelMap, shown: PixelMap) {
        assertNotEquals(rest[width / 2, height / 2], shown[width / 2, height / 2], "the inside is not tinted")
        assertEquals(rest[1, 1], shown[1, 1], "the top left corner is tinted")
        assertEquals(rest[width - 2, 1], shown[width - 2, 1], "the top right corner is tinted")
    }
}
