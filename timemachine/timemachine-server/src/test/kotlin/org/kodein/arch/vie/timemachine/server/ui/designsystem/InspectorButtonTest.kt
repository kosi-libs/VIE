@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
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
import org.kodein.arch.vie.timemachine.server.fixtures.DarkenedLevels
import org.kodein.arch.vie.timemachine.server.fixtures.VisibleLevels
import org.kodein.arch.vie.timemachine.server.fixtures.assertDarkened
import org.kodein.arch.vie.timemachine.server.fixtures.assertLightened
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.theme.LightInspectorScheme


/**
 * What a button is: as high as its size, whatever its text; disabled, a button that takes no click and has
 * the same fill whatever its variant; and, as a text button, tinted under the pointer inside its pill only,
 * darkening the light theme's surface and lightening the dark theme's by at least [VisibleLevels] levels.
 * A filled button is guarded for darkening its primary fill, hovered or pressed, in the dark theme, where the
 * theme's own content colour would lighten it by 1 to 10 levels only. Sizes are in px: the harness's density is 1.
 */
class InspectorButtonTest {

    /** A [variant] button at rest, then with [interaction] emitted on it, on the panes' surface of the [dark] or light theme, in a window just larger than it. */
    private fun restAnd(variant: ButtonVariant, dark: Boolean, interaction: Interaction): Pair<PixelMap, PixelMap> {
        val source = LatchedInteractionSource()
        lateinit var shots: Pair<PixelMap, PixelMap>
        contentTest(
            width = 200, height = 60, viewState = InspectorViewState().apply { this.dark = dark },
            content = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp)) {
                    InspectorButton("Clear filters", onClick = {}, modifier = Modifier.testTag("button"), variant = variant, interactionSource = source)
                }
            },
        ) {
            val rest = onNodeWithTag("button").captureToImage().toPixelMap()
            source.emit(interaction)
            waitForIdle()
            shots = rest to onNodeWithTag("button").captureToImage().toPixelMap()
        }
        return shots
    }

    /** A [variant] button at rest and under the pointer; see [restAnd]. */
    private fun restAndHovered(variant: ButtonVariant, dark: Boolean = false) = restAnd(variant, dark, HoverInteraction.Enter())

    /** A [variant] button at rest and pressed; see [restAnd]. */
    private fun restAndPressed(variant: ButtonVariant, dark: Boolean = false) = restAnd(variant, dark, PressInteraction.Press(Offset.Zero))

    /** The pixel in the pill's end margin, to the right of the label, where nothing is written. */
    private fun PixelMap.endMargin(): Color = this[width - 3, height / 2]

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
        val (rest, hovered) = restAndHovered(ButtonVariant.Text)
        val w = hovered.width
        val h = hovered.height
        // In the pill, in its end margin to the right of the text; and in the corners the pill cuts off.
        assertNotEquals(rest[w - 3, h / 2], hovered[w - 3, h / 2], "the inside is not tinted")
        assertEquals(rest[1, 1], hovered[1, 1], "the top left corner is tinted")
        assertEquals(rest[w - 2, 1], hovered[w - 2, 1], "the top right corner is tinted")
    }

    @Test
    fun `in the light theme, a hovered text button darkens the surface inside its pill`() {
        val (rest, hovered) = restAndHovered(ButtonVariant.Text)
        assertDarkened(rest.endMargin(), hovered.endMargin())
    }

    @Test
    fun `in the dark theme, a hovered text button lightens the surface inside its pill`() {
        val (rest, hovered) = restAndHovered(ButtonVariant.Text, dark = true)
        assertLightened(rest.endMargin(), hovered.endMargin())
    }

    @Test
    fun `in the dark theme, a hovered filled button still darkens its primary fill`() {
        val (rest, hovered) = restAndHovered(ButtonVariant.Filled, dark = true)
        assertDarkened(rest.endMargin(), hovered.endMargin(), DarkenedLevels)
    }

    @Test
    fun `in the dark theme, a pressed filled button still darkens its primary fill`() {
        val (rest, pressed) = restAndPressed(ButtonVariant.Filled, dark = true)
        assertDarkened(rest.endMargin(), pressed.endMargin(), DarkenedLevels)
    }
}
