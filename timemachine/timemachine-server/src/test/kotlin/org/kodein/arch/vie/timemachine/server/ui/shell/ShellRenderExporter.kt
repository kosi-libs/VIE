package org.kodein.arch.vie.timemachine.server.ui.shell

import java.io.File


/**
 * Renders every [ShellScene] in both themes into the directory given as first argument, as
 * `<name>-<theme>.png`: what a change to the views is looked at on. Nothing compares them.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: error("Usage: <output directory>")).apply { mkdirs() }
    for (scene in ShellScenes) for (dark in listOf(true, false)) {
        val file = out.resolve("${scene.name}-${if (dark) "dark" else "light"}.png")
        file.writeBytes(renderShellScene(scene, dark))
        println(file)
    }
}
