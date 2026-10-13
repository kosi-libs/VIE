package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.skia.Bitmap

@OptIn(ExperimentalComposeUiApi::class)
class SymbolTest {
    /** The bounding box of the opaque pixels of [name] drawn in a 48 dp box by the bundled font. */
    private fun inkWidth(name: String): Int {
        val scene = ImageComposeScene(120, 48, Density(1f)) {
            InspectorTheme(dark = true) { Symbol(name, 48.dp, Color.White) }
        }
        // Compose resources load asynchronously: render until the font is in.
        val bitmap = Bitmap()
        repeat(20) {
            scene.render().let { image -> bitmap.allocN32Pixels(120, 48); image.readPixels(bitmap) }
            Thread.sleep(100)
        }
        scene.close()
        val xs = (0 until 120).filter { x -> (0 until 48).any { y -> bitmap.getColor(x, y) ushr 24 != 0 } }
        return if (xs.isEmpty()) 0 else xs.last() - xs.first() + 1
    }

    @Test
    fun `a ligature name is drawn as its glyph, and an unknown name as plain text`() {
        // inventory_2 is a box 42 px wide; an unknown name is clipped plain text, which starts with an "N".
        assertEquals(42, inkWidth("inventory_2"))
        assertNotEquals(42, inkWidth("not_a_symbol_name"))
    }
}
