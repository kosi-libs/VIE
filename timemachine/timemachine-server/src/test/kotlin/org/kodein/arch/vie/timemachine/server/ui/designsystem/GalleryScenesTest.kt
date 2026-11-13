@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


/**
 * The gallery's scenes, each in both themes: every specimen draws something, and a scene renders as an
 * image of its width. What the specimens look like is for the renders of `exportGalleryRenders`.
 */
class GalleryScenesTest {

    @Test
    fun `every scene has a name of its own, fit for a file`() {
        val names = GalleryScenes.map { it.name }
        assertEquals(names.distinct(), names, "two scenes share a name")
        assertEquals(emptyList(), names.filterNot { it.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*")) })
    }

    @Test
    fun `every specimen of every scene draws something, in both themes`() {
        for (scene in GalleryScenes) for (dark in listOf(true, false)) gallerySceneTest(scene, dark) {
            val theme = if (dark) "dark" else "light"
            val height = onNodeWithTag(GalleryTag).fetchSemanticsNode().size.height
            assertTrue(height <= GalleryHeight, "${scene.name} is $height px high, more than the $GalleryHeight px a render holds: split it")
            scene.rows.forEachIndexed { r, row ->
                row.cells.indices.forEach { c ->
                    val size = onNodeWithTag(galleryCellTag(r, c)).fetchSemanticsNode().size
                    assertTrue(size.width > 0 && size.height > 0, "${scene.name}, $theme: ${row.label}, cell ${c + 1}, draws nothing")
                }
            }
        }
    }

    @Test
    fun `a scene renders as an image of its width, which the theme changes`() {
        val scene = GalleryScenes.first()
        val light = ImageIO.read(renderGalleryScene(scene, dark = false).inputStream())
        val dark = ImageIO.read(renderGalleryScene(scene, dark = true).inputStream())
        assertEquals(scene.width, light.width)
        // The gallery's background, in its margin: the two themes do not paint it alike.
        assertNotEquals(light.getRGB(2, 2), dark.getRGB(2, 2))
    }
}
