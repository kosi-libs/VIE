@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.theme.DarkInspectorColors
import org.kodein.arch.vie.timemachine.server.ui.theme.DarkInspectorScheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.LightInspectorColors
import org.kodein.arch.vie.timemachine.server.ui.theme.LightInspectorScheme


/**
 * What a callout says by its tone: the colours each tone maps to, in both themes, that it is painted with its
 * tint, and that its action is clickable; and that a banner spans its pane, square. Sizes are in px: the harness's density is 1.
 */
class CalloutTest {

    /** A tone's tint, accent and title colour. */
    private data class ToneColors(val container: Color, val accent: Color, val title: Color)

    /** What every tone resolves to in the [dark] or light theme, read from a composition in it. */
    private fun tones(dark: Boolean): Map<CalloutTone, ToneColors> {
        val seen = mutableMapOf<CalloutTone, ToneColors>()
        contentTest(
            viewState = InspectorViewState().apply { this.dark = dark },
            content = { for (tone in CalloutTone.entries) seen[tone] = ToneColors(tone.container, tone.accent, tone.title) },
        ) {}
        return seen
    }

    @Test
    fun `each tone maps to its colours, in the light and in the dark theme`() {
        for (dark in listOf(false, true)) {
            val scheme = if (dark) DarkInspectorScheme else LightInspectorScheme
            val colors = if (dark) DarkInspectorColors else LightInspectorColors
            assertEquals(
                mapOf(
                    CalloutTone.Neutral to ToneColors(scheme.surfaceContainerHigh, scheme.outline, scheme.onSurface),
                    CalloutTone.Information to ToneColors(scheme.surfaceContainerHigh, scheme.primary, scheme.onSurface),
                    CalloutTone.Success to ToneColors(colors.successContainer, colors.success, colors.success),
                    CalloutTone.Error to ToneColors(colors.errorContainer, colors.error, colors.error),
                    CalloutTone.Warning to ToneColors(colors.timeoutContainer, colors.timeout, colors.timeout),
                    CalloutTone.Intervened to ToneColors(colors.intervenedContainer, colors.intervened, colors.intervened),
                ),
                tones(dark),
                "the tones' colours in the ${if (dark) "dark" else "light"} theme",
            )
        }
    }

    @Test
    fun `the tones that name an outcome tell each other apart, by their tint and by their accent`() {
        val outcomes = tones(dark = false).filterKeys { it in listOf(CalloutTone.Success, CalloutTone.Error, CalloutTone.Warning, CalloutTone.Intervened) }.values
        assertEquals(outcomes.size, outcomes.map { it.container }.distinct().size, "two outcomes have one tint")
        assertEquals(outcomes.size, outcomes.map { it.accent }.distinct().size, "two outcomes have one accent")
        // A tone that names none keeps the panes' text colour for its title, and tells itself from an outcome's.
        val neutral = tones(dark = false).getValue(CalloutTone.Neutral)
        assertNotEquals(neutral.accent, neutral.title)
    }

    @Test
    fun `what a callout or a banner says is announced when it appears, an error at once and the rest when the reader is free`() {
        contentTest(content = {
            Column {
                for (tone in CalloutTone.entries) Callout(tone, Glyph.Info) { Text("note $tone") }
                Banner(CalloutTone.Error, Glyph.Info) { Text("banner error") }
                Banner(CalloutTone.Neutral, Glyph.Info) { Text("banner neutral") }
            }
        }) {
            fun live(mode: LiveRegionMode) = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode)
            for (tone in CalloutTone.entries) {
                onNodeWithText("note $tone").assert(live(if (tone == CalloutTone.Error) LiveRegionMode.Assertive else LiveRegionMode.Polite))
            }
            onNodeWithText("banner error").assert(live(LiveRegionMode.Assertive))
            onNodeWithText("banner neutral").assert(live(LiveRegionMode.Polite))
        }
    }

    @Test
    fun `a callout is painted with its tone's tint behind its text`() {
        for (tone in listOf(CalloutTone.Success, CalloutTone.Error)) {
            val expected = tones(dark = false).getValue(tone).container
            contentTest(content = { Callout(tone, Glyph.Info, Modifier.testTag("callout")) { Text("Sent") } }) {
                val image = onNodeWithTag("callout").captureToImage().toPixelMap()
                // In its left margin, inside the box: nothing is drawn there but its tint.
                assertEquals(expected, image[6, image.height / 2], "the tint of $tone")
            }
        }
    }

    @Test
    fun `a callout's action is shown at its end, and does what it says`() {
        var clicks = 0
        contentTest(content = {
            Callout(CalloutTone.Error, Glyph.Error, action = { InspectorButton("Retry", onClick = { clicks++ }) }) { Text("Refused") }
        }) {
            onNodeWithText("Refused").assertExists()
            onNodeWithText("Retry").performClick()
            assertEquals(1, clicks)
        }
    }

    @Test
    fun `a banner spans its pane, square, in its tone's tint`() {
        val expected = tones(dark = false).getValue(CalloutTone.Warning).container
        contentTest(width = 500, content = { Banner(CalloutTone.Warning, Glyph.Info, Modifier.testTag("banner")) { Text("Timed out") } }) {
            val node = onNodeWithTag("banner")
            assertEquals(500, node.fetchSemanticsNode().size.width)
            val image = node.captureToImage().toPixelMap()
            // Its corners are not rounded: the tint reaches them.
            assertEquals(listOf(expected, expected), listOf(image[0, 0], image[image.width - 1, 0]))
        }
    }
}
