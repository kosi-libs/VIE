package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `the scale's steps are the roles next to them at another size`() {
        val fonts = InspectorFonts(plex = FontFamily.Serif, mono = FontFamily.Monospace, symbols = FontFamily.Cursive)
        val typography = inspectorTypography(fonts)
        val text = inspectorTextStyles(fonts, typography)

        // Each Plex step with the role it is a size of, and its size.
        val plex = with(text) {
            listOf(
                Triple(note, typography.bodySmall, 11.5), Triple(body, typography.bodySmall, 12.0), Triple(bodyEmphasis, typography.bodySmall, 12.5),
                Triple(emptyTitle, typography.bodyMedium, 13.5),
                Triple(tag, typography.labelMedium, 10.5), Triple(actionSmall, typography.labelMedium, 11.0), Triple(action, typography.labelMedium, 11.5),
                Triple(label, typography.labelMedium, 12.0), Triple(actionLarge, typography.labelMedium, 13.0),
            )
        }
        for ((step, role, size) in plex) {
            assertEquals(size.sp, step.fontSize)
            assertEquals(role, step.copy(fontSize = role.fontSize), "a step changes its role's size, and nothing else")
        }
        assertEquals(listOf(18.sp, 24.sp), listOf(text.screenTitle.fontSize, text.screenTitle.lineHeight))
        assertEquals(listOf(20.sp, 26.sp), listOf(text.screenHeadline.fontSize, text.screenHeadline.lineHeight))
        for (title in listOf(text.screenTitle, text.screenHeadline)) {
            assertEquals(fonts.plex, title.fontFamily)
            assertEquals(FontWeight.Medium, title.fontWeight)
        }

        val mono = with(text) { listOf(monoMeta to 10.5, monoSecondary to 11.0, codeRow to 11.5, code to 12.0, monoTitle to 12.5, monoNumeral to 13.0) }
        for ((step, size) in mono) {
            assertEquals(TextStyle(fontFamily = fonts.mono, fontSize = size.sp, fontWeight = FontWeight.Normal), step)
        }
    }

    @Test
    fun `a section label is labelSmall with its own letter spacing`() {
        val fonts = InspectorFonts(plex = FontFamily.Serif, mono = FontFamily.Monospace, symbols = FontFamily.Cursive)
        val typography = inspectorTypography(fonts)
        val label = inspectorTextStyles(fonts, typography).sectionLabel
        assertEquals(0.06.sp, label.letterSpacing)
        assertEquals(typography.labelSmall, label.copy(letterSpacing = typography.labelSmall.letterSpacing), "a section label changes labelSmall's letter spacing, and nothing else")
    }

    @Test
    fun `a strong label is labelMedium in semi-bold`() {
        val fonts = InspectorFonts(plex = FontFamily.Serif, mono = FontFamily.Monospace, symbols = FontFamily.Cursive)
        val typography = inspectorTypography(fonts)
        val strong = inspectorTextStyles(fonts, typography).labelStrong
        assertEquals(FontWeight.SemiBold, strong.fontWeight)
        assertEquals(typography.labelMedium, strong.copy(fontWeight = typography.labelMedium.fontWeight), "a strong label changes labelMedium's weight, and nothing else")
    }

    // ---- Contrast (WCAG 2.2) -------------------------------------------------------------------------------

    private fun channel(value: Float): Double = if (value <= 0.04045f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    private fun luminance(color: Color): Double = 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sorted().reversed()
        return (light + 0.05) / (dark + 0.05)
    }

    /** The surfaces the panes, bars, fields and cards are drawn on, which text and the outline of a control lie over. */
    private fun ColorScheme.surfaces() = mapOf(
        "surface" to surface, "surfaceContainerLow" to surfaceContainerLow, "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh, "surfaceContainerHighest" to surfaceContainerHighest,
    )

    @Test
    fun `the outline reads as text, and as the boundary of a control, on every surface, in both themes`() {
        for ((theme, scheme) in mapOf("light" to LightInspectorScheme, "dark" to DarkInspectorScheme)) {
            for ((name, surface) in scheme.surfaces()) {
                val ratio = contrast(scheme.outline, surface)
                // 4.5:1 for small text (WCAG 1.4.3), which the outline is: section labels, counts, descriptions. It also
                // clears the 3:1 a control's boundary needs to be told from what it lies on (WCAG 1.4.11).
                assertTrue(ratio >= 4.5, "the outline on $name in the $theme theme is $ratio:1")
            }
        }
    }

    @Test
    fun `the focus ring tells itself from the surfaces and from a primary fill, by one line or the other`() {
        for ((theme, pair) in mapOf("light" to (LightInspectorScheme to LightInspectorColors), "dark" to (DarkInspectorScheme to DarkInspectorColors))) {
            val (scheme, colors) = pair
            // The two lines against each other: either shows where the other stops.
            assertTrue(contrast(colors.focusRing, colors.focusRingInner) >= 3.0, "the ring's lines in the $theme theme")
            for ((name, surface) in scheme.surfaces()) {
                assertTrue(contrast(colors.focusRing, surface) >= 3.0, "the ring's outer line on $name in the $theme theme")
            }
            // Over a primary button, the outer line is against what is outside the button and the inner one against its fill.
            assertTrue(contrast(colors.focusRingInner, scheme.primary) >= 3.0, "the ring's inner line on the primary fill in the $theme theme")
            assertTrue(contrast(colors.focusRingInner, scheme.primaryContainer) >= 3.0 || contrast(colors.focusRing, scheme.primaryContainer) >= 3.0, "the ring on a selected segment in the $theme theme")
        }
    }
}
