package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
}
