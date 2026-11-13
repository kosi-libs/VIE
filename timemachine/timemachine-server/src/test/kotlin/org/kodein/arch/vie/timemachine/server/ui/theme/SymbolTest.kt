package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.shaper.Shaper

/** Where the build puts the bundled symbol font on the classpath: `composeResources/<resources package>/font/`. */
private const val SymbolFont = "composeResources/org.kodein.arch.vie.timemachine.server.resources/font/MaterialSymbolsRounded.ttf"

@OptIn(ExperimentalComposeUiApi::class)
class SymbolTest {
    /** The bounding box of the opaque pixels of [glyph] drawn in a 48 dp box by the bundled font. */
    private fun inkWidth(glyph: Glyph): Int {
        val scene = ImageComposeScene(120, 48, Density(1f)) {
            InspectorTheme(dark = true) { Symbol(glyph, size = 48.dp, tint = Color.White) }
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

    /** How many glyphs the bundled font shapes [text] into: one for a ligature it has, one per letter otherwise. */
    private fun shapedGlyphs(text: String): Int {
        val bytes = checkNotNull(javaClass.classLoader.getResourceAsStream(SymbolFont)) { "$SymbolFont is not on the test classpath" }
            .use { it.readBytes() }
        val typeface = checkNotNull(FontMgr.default.makeFromData(Data.makeFromBytes(bytes))) { "$SymbolFont is not a font" }
        return Shaper.make().shape(text, Font(typeface, 24f))?.glyphs?.size ?: 0
    }

    @Test
    fun `the bundled font has a ligature for every glyph`() {
        // The control: a name the font has no ligature for is shaped letter by letter.
        assertTrue(shapedGlyphs("not_a_symbol_name") > 1)
        assertEquals(emptyList(), Glyph.entries.filter { shapedGlyphs(it.ligature) != 1 })
    }

    @Test
    fun `a glyph is drawn by its ligature`() {
        // inventory_2 is a box 42 px wide.
        assertEquals(42, inkWidth(Glyph.Inventory2))
    }
}
