package org.kodein.arch.vie.timemachine.server.ui.designsystem

import java.io.File


/**
 * Renders every [GalleryScene] in both themes into the directory given as first argument, as
 * `<name>-<theme>.png`: what a change to a component is looked at on. Nothing compares them.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: error("Usage: <output directory>")).apply { mkdirs() }
    for (scene in GalleryScenes) for (dark in listOf(true, false)) {
        val file = out.resolve("${scene.name}-${if (dark) "dark" else "light"}.png")
        file.writeBytes(renderGalleryScene(scene, dark))
        println(file)
    }
}
