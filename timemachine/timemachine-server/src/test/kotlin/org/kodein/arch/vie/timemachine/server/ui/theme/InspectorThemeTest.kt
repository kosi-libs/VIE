package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class InspectorThemeTest {
    @Test
    fun `the schemes' anchors are the canvas's`() {
        assertEquals(Color(0xFF0B0D10), DarkInspectorScheme.surfaceDim)
        assertEquals(Color(0xFF5ED3BF), DarkInspectorScheme.primary)
        assertEquals(Color(0xFFDFE3E8), LightInspectorScheme.surfaceDim)
        assertEquals(Color(0xFF00695B), LightInspectorScheme.primary)
    }

    @Test
    fun `the error status is the scheme's error, in both themes`() {
        assertEquals(DarkInspectorScheme.error, DarkInspectorColors.error)
        assertEquals(DarkInspectorScheme.errorContainer, DarkInspectorColors.errorContainer)
        assertEquals(LightInspectorScheme.error, LightInspectorColors.error)
        assertEquals(LightInspectorScheme.errorContainer, LightInspectorColors.errorContainer)
    }

    @Test
    fun `no text role leaves the bundled fonts`() {
        // Stand-ins that Material's own default, a sans serif, is none of.
        val fonts = InspectorFonts(plex = FontFamily.Serif, mono = FontFamily.Monospace, symbols = FontFamily.Cursive)
        val typography = inspectorTypography(fonts)
        val roles = with(typography) {
            mapOf(
                "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
                "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
                "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
                "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
                "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
            )
        }

        // A role the scale does not define is Material's, which must still be drawn in Plex.
        assertEquals(emptySet(), roles.filterValues { it.fontFamily != fonts.plex && it.fontFamily != fonts.mono }.keys)
        assertEquals(fonts.mono, typography.titleMedium.fontFamily, "the scale's own mono role keeps its family")
    }
}
