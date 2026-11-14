@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.ui.test.ExperimentalTestApi
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


/**
 * The real shell over a fixture, screen by screen and in both themes: the wiring from the store to the
 * views composes what each screen is of. [shellSceneTest] fails with what a screen never showed.
 */
class ShellScenesTest {

    private fun showsItsLandmarks(scenes: List<ShellScene>) {
        for (scene in scenes) for (dark in listOf(true, false)) shellSceneTest(scene, dark)
    }

    @Test
    fun `the shell shows its screens in both themes`() = showsItsLandmarks(ShellScreens)

    @Test
    fun `the shell shows a selected ViewModel in both themes`() = showsItsLandmarks(ShellViewModelScenes)

    @Test
    fun `the compact layout shows its rail, its drawer and its folded right pane in both themes`() = showsItsLandmarks(ShellCompactScenes)

    @Test
    fun `a screen renders as an image of its size, which the theme changes`() {
        val scene = ShellCompactScenes.first()
        val light = renderShellScene(scene, dark = false)
        val dark = renderShellScene(scene, dark = true)

        val image = ImageIO.read(light.inputStream())
        assertEquals(scene.width to scene.height, image.width to image.height)
        // The window's background, away from any text: the two themes do not paint it alike.
        assertNotEquals(image.getRGB(5, scene.height / 2), ImageIO.read(dark.inputStream()).getRGB(5, scene.height / 2))
    }
}
