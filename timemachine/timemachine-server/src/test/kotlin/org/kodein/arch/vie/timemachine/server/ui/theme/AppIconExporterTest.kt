package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class AppIconExporterTest {
    private val pngs = AppIconSizes.associateWith(::renderAppIcon)

    private fun decodedSize(png: ByteArray) = ImageIO.read(png.inputStream()).let { it.width to it.height }

    @Test
    fun `every rendered size is a square PNG of that size`() {
        pngs.forEach { (size, png) -> assertEquals(size to size, decodedSize(png), "$size px") }
    }

    @Test
    fun `the icon is dark and its playhead is drawn`() {
        val image = ImageIO.read(pngs.getValue(256).inputStream())
        // The playhead bar is at x 45..48 of 64, from y 12 to 52.
        assertEquals(0xFF5ED3BF.toInt(), image.getRGB(46 * 4 + 2, 32 * 4))
        assertEquals(0xFF12161B.toInt(), image.getRGB(58 * 4, 32 * 4))
        // Outside the rounded corner, it is transparent.
        assertEquals(0, image.getRGB(0, 0) ushr 24)
    }

    @Test
    fun `the windows' painter draws the same icon, to the size it is asked`() {
        val bitmap = ImageBitmap(256, 256)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(256f, 256f)) {
            with(AppIconPainter) { draw(size) }
        }
        val image = bitmap.toAwtImage()
        assertEquals(0xFF5ED3BF.toInt(), image.getRGB(46 * 4 + 2, 32 * 4))
        assertEquals(0xFF12161B.toInt(), image.getRGB(58 * 4, 32 * 4))
        assertEquals(0, image.getRGB(0, 0) ushr 24)

        // And the same pixels as the composable the exported files are rendered from.
        val rendered = ImageIO.read(pngs.getValue(256).inputStream())
        for ((x, y) in listOf(46 * 4 + 2 to 32 * 4, 58 * 4 to 32 * 4, 20 * 4 to 20 * 4, 24 * 4 to 32 * 4, 20 * 4 to 43 * 4)) {
            assertEquals(rendered.getRGB(x, y), image.getRGB(x, y), "pixel $x, $y")
        }
    }

    @Test
    fun `the ico parses back with the expected entries, and each is a PNG of its size`() {
        val ico = ByteBuffer.wrap(icoOf(pngs.filterKeys { it in IcoSizes })).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, ico.getShort(0).toInt())
        assertEquals(1, ico.getShort(2).toInt())
        val count = ico.getShort(4).toInt()
        assertEquals(IcoSizes.size, count)
        repeat(count) { i ->
            val at = 6 + 16 * i
            val size = IcoSizes[i]
            assertEquals(size % 256, ico.get(at).toInt() and 0xFF)
            assertEquals(size % 256, ico.get(at + 1).toInt() and 0xFF)
            val png = ByteArray(ico.getInt(at + 8)).also { ico.get(ico.getInt(at + 12), it) }
            assertEquals(size to size, decodedSize(png))
        }
    }

    @Test
    fun `the icns parses back with the expected chunks, and each is a PNG of its size`() {
        val icns = ByteBuffer.wrap(icnsOf(pngs)).order(ByteOrder.BIG_ENDIAN)
        assertEquals("icns", String(ByteArray(4).also { icns.get(0, it) }))
        assertEquals(icns.capacity(), icns.getInt(4))
        val seen = mutableMapOf<String, Pair<Int, Int>>()
        var at = 8
        while (at < icns.capacity()) {
            val type = String(ByteArray(4).also { icns.get(at, it) })
            val length = icns.getInt(at + 4)
            val png = ByteArray(length - 8).also { icns.get(at + 8, it) }
            seen[type] = decodedSize(png)
            at += length
        }
        assertEquals(
            mapOf("ic07" to (128 to 128), "ic08" to (256 to 256), "ic09" to (512 to 512), "ic10" to (1024 to 1024)),
            seen,
        )
    }
}
