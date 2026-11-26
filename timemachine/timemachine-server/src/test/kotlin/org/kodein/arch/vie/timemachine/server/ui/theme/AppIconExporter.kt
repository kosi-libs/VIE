package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jetbrains.skia.EncodedImageFormat

/** The sizes `AppIcon` is rendered at, in pixels. */
val AppIconSizes = listOf(16, 32, 48, 64, 128, 256, 512, 1024)

/** The sizes an `.ico` carries: the format stops at 256. */
val IcoSizes = listOf(16, 32, 48, 64, 128, 256)

/** The `.icns` chunk type of each size it carries (`ic10` is 512 at 2x). */
val IcnsTypes = mapOf(128 to "ic07", 256 to "ic08", 512 to "ic09", 1024 to "ic10")

/** `AppIcon` as a square PNG of [size] pixels. */
@OptIn(ExperimentalComposeUiApi::class)
fun renderAppIcon(size: Int): ByteArray {
    val scene = ImageComposeScene(size, size, Density(1f)) { AppIcon(Modifier.fillMaxSize()) }
    try {
        return checkNotNull(scene.render().encodeToData(EncodedImageFormat.PNG)).bytes
    } finally {
        scene.close()
    }
}

/** An `.ico` of PNG entries, one per size in [pngs], smallest first. */
fun icoOf(pngs: Map<Int, ByteArray>): ByteArray {
    val entries = pngs.toSortedMap()
    val header = 6 + 16 * entries.size
    val out = ByteBuffer.allocate(header + entries.values.sumOf { it.size }).order(ByteOrder.LITTLE_ENDIAN)
    out.putShort(0).putShort(1).putShort(entries.size.toShort())
    var offset = header
    for ((size, png) in entries) {
        require(size in 1..256) { "An ICO entry is at most 256 px, not $size" }
        // 256 is stored as 0.
        out.put((size % 256).toByte()).put((size % 256).toByte())
        out.put(0).put(0).putShort(1).putShort(32).putInt(png.size).putInt(offset)
        offset += png.size
    }
    entries.values.forEach { out.put(it) }
    return out.array()
}

/** An `.icns` of PNG chunks, one per size in [pngs] that [IcnsTypes] knows. */
fun icnsOf(pngs: Map<Int, ByteArray>): ByteArray {
    val chunks = IcnsTypes.filterKeys { it in pngs }.toSortedMap()
    check(chunks.isNotEmpty()) { "No size an ICNS carries" }
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out ->
        out.writeBytes("icns")
        out.writeInt(8 + chunks.keys.sumOf { 8 + pngs.getValue(it).size })
        for ((size, type) in chunks) {
            val png = pngs.getValue(size)
            out.writeBytes(type)
            out.writeInt(8 + png.size)
            out.write(png)
        }
    }
    return bytes.toByteArray()
}

/** Writes `AppIcon.png` (512 px), `AppIcon.ico` and `AppIcon.icns` into the directory given as the only argument. */
fun main(args: Array<String>) {
    val dir = File(args.single()).apply { mkdirs() }
    val pngs = AppIconSizes.associateWith(::renderAppIcon)
    File(dir, "AppIcon.png").writeBytes(pngs.getValue(512))
    File(dir, "AppIcon.ico").writeBytes(icoOf(pngs.filterKeys { it in IcoSizes }))
    File(dir, "AppIcon.icns").writeBytes(icnsOf(pngs))
}
