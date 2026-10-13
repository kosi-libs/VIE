@file:OptIn(ExperimentalComposeUiApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.server.InspectorWindow
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.newInspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorSelection
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab


/**
 * The real shell rendered headless over a fixture, each to `build/shell-render/<name>-<theme>.png`:
 * a guard that the wiring from the ViewModels to the views composes and paints something.
 */
class ShellRenderTest {

    private val taskList = InspectorScope.ViewModel(Demo.session.id, Demo.taskList)

    private fun render(
        name: String, id: String, dark: Boolean,
        width: Int = 1440, height: Int = 900, view: InspectorViewState.() -> Unit = {},
        select: InspectorSelection.() -> Unit = {},
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val inspector = ScenarioData.of(id).newInspector(scope, Dispatchers.Default)
        val viewState = InspectorViewState().also { it.dark = dark; it.view() }
        try {
            inspector.selection.select()
            val scene = ImageComposeScene(width, height, Density(1f)) { InspectorWindow(inspector, viewState) }
            try {
                // The store applies its events and the panes project off the UI thread: give them a few frames to land.
                var nanos = 0L
                repeat(30) { scene.render(nanos); nanos += 50_000_000; Thread.sleep(50) }
                val bytes = checkNotNull(scene.render(nanos).encodeToData(EncodedImageFormat.PNG)).bytes
                File("build/shell-render").apply { mkdirs() }.resolve("$name-${if (dark) "dark" else "light"}.png").writeBytes(bytes)
                assertTrue(bytes.size > 5_000, "$name rendered a blank window")
            } finally {
                scene.close()
            }
        } finally {
            inspector.close()
            scope.cancel()
        }
    }

    @Test
    fun `the shell renders its screens in both themes`() {
        for (dark in listOf(true, false)) {
            render("empty", "empty", dark)
            render("cannot-listen", "cannotListen", dark)
        }
    }

    @Test
    fun `the shell renders a selected ViewModel in both themes`() {
        for (dark in listOf(true, false)) {
            render("detail", "main", dark) { selectScope(taskList); selectRow(TimeMachineSequence(208)) }
            render("composer", "main", dark) { selectScope(taskList); selectRow(TimeMachineSequence(208)); selectTab(RightTab.Composer) }
        }
    }

    @Test
    fun `the compact layout renders its rail, its drawer and its folded right pane in both themes`() {
        val intervened: InspectorSelection.() -> Unit = { selectScope(taskList); selectRow(TimeMachineSequence(238)) }
        for (dark in listOf(true, false)) {
            render("compact", "main", dark, width = 1000, height = 700, select = intervened)
            render("compact-drawer", "main", dark, width = 1000, height = 700, view = { drawerOpen = true }, select = intervened)
            render("compact-collapsed", "main", dark, width = 1000, height = 700, view = { rightCollapsed = true }, select = intervened)
            render("compact-empty", "empty", dark, width = 1000, height = 700)
        }
    }
}
