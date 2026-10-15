package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.ui.graphics.Color
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
}
