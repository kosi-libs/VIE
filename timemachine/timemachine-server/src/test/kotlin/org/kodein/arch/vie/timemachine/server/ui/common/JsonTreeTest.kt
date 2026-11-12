package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.skia.Bitmap
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.LightInspectorColors

@OptIn(ExperimentalComposeUiApi::class)
class JsonTreeTest {

    private val width = 200
    private val height = 80

    /** The open tree of `{"a": 1, "b": 2}`, 19 px a line at a density of 1, with `a` changed. */
    private fun scene(highlight: () -> Float) = ImageComposeScene(width, height, Density(1f)) {
        InspectorTheme(dark = false) {
            JsonTree(
                root = buildJsonObject { put("a", JsonPrimitive(1)); put("b", JsonPrimitive(2)) },
                expanded = setOf(emptyList()),
                changed = setOf(listOf("a")),
                highlight = highlight,
                onToggle = {},
            )
        }
    }

    /** The colour at the right end of line [line], where no text is drawn. */
    private fun ImageComposeScene.background(line: Int): Int {
        val bitmap = Bitmap.makeFromImage(render())
        return bitmap.getColor(width - 10, 19 * line + 9)
    }

    @Test
    fun `a changed node sits on the model tint, and the others on nothing`() {
        val scene = scene { 1f }
        assertEquals(LightInspectorColors.modelContainer.toArgb(), scene.background(line = 1))
        assertEquals(0, scene.background(line = 2) ushr 24)
        scene.close()
    }

    @Test
    fun `the tint fades with the highlight as it is drawn`() {
        val highlight = mutableFloatStateOf(1f)
        val scene = scene { highlight.floatValue }
        assertEquals(0xFF, scene.background(line = 1) ushr 24)

        highlight.floatValue = 0.5f
        assertTrue(scene.background(line = 1) ushr 24 in 0x7E..0x81)

        highlight.floatValue = 0f
        assertEquals(0, scene.background(line = 1) ushr 24)
        scene.close()
    }
}
