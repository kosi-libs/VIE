@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.server.InspectorWindow
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.inspectorTest
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorSelection
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import org.kodein.arch.vie.timemachine.server.ui.rightpane.HideRightPaneDescription
import org.kodein.arch.vie.timemachine.server.ui.sessions.RailMenuDescription


/**
 * One screen of the window: the real [InspectorWindow] over a scenario, as wide and high as the scene,
 * with what the developer set and selected.
 *
 * @property name The name of its renders: `<name>-<theme>.png`.
 * @property view What the developer set in the window.
 * @property select What the developer selected.
 * @property landmarks What only this screen shows once the store's events and the panes' projections are
 *   all in: what the test asserts, and what a render waits for.
 */
internal class ShellScene(
    val name: String,
    val data: ScenarioData,
    val width: Int = 1440,
    val height: Int = 900,
    val view: InspectorViewState.() -> Unit = {},
    val select: InspectorSelection.() -> Unit = {},
    val landmarks: List<SemanticsMatcher>,
)

private val taskList = InspectorScope.ViewModel(Demo.session.id, Demo.taskList)

/** A listener that failed for another reason than its port: the catalogue's scenario is a port in use. */
private val notAPortInUse = TimeMachineEvent.ListenerChanged(ListenerState.Failed(ListenerState.Failed.Reason.Other, "Too many open files"))

/** Row #208 of TaskListVM, a Model. */
private val aModel: InspectorSelection.() -> Unit = { selectScope(taskList); selectRow(TimeMachineSequence(208)) }

/** Row #238 of TaskListVM, the result of intervention #3. */
private val intervened: InspectorSelection.() -> Unit = { selectScope(taskList); selectRow(TimeMachineSequence(238)) }

private fun text(text: String) = hasText(text)
private fun textWith(part: String) = hasText(part, substring = true)

/** The screens with nothing to browse. */
internal val ShellScreens = listOf(
    ShellScene("empty", ScenarioData.of("empty"), landmarks = listOf(text("Waiting for an app to connect"), text("No sessions"))),
    ShellScene(
        "cannot-listen", ScenarioData.of("cannotListen"),
        landmarks = listOf(text("Close the other inspector window."), text("lsof -i :13229"), text("Not listening — port 13229 is in use")),
    ),
    ShellScene(
        "cannot-listen-other", ScenarioData("cannotListen", listOf(notAPortInUse)),
        landmarks = listOf(text("Too many open files"), text("Retry"), text("Not listening — port 13229 could not be opened")),
    ),
)

/** The full layout on a selected ViewModel, on each tab of the right pane. */
internal val ShellViewModelScenes = listOf(
    ShellScene(
        "detail", ScenarioData.of("main"), select = aModel,
        landmarks = listOf(text("Clear disconnected"), text("TaskListVM"), text("Current model"), textWith("#208 · "), text("FULL MODEL")),
    ),
    ShellScene(
        "composer", ScenarioData.of("main"), select = { aModel(); selectTab(RightTab.Composer) },
        landmarks = listOf(text("Clear disconnected"), text("TaskListVM"), text("Current model"), text("Replace model"), text("RECENT INTERVENTIONS")),
    ),
)

/** The compact layout: its rail, its drawer, its folded right pane, and its empty screen. */
internal val ShellCompactScenes = listOf(
    ShellScene(
        "compact", ScenarioData.of("main"), width = 1000, height = 700, select = intervened,
        landmarks = listOf(hasContentDescription(RailMenuDescription), text("TaskListVM"), hasContentDescription(HideRightPaneDescription), textWith("intervention #3")),
    ),
    ShellScene(
        "compact-drawer", ScenarioData.of("main"), width = 1000, height = 700, view = { drawerOpen = true }, select = intervened,
        landmarks = listOf(hasContentDescription(RailMenuDescription), text("Clear disconnected"), textWith("intervention #3")),
    ),
    ShellScene(
        "compact-collapsed", ScenarioData.of("main"), width = 1000, height = 700, view = { rightCollapsed = true }, select = intervened,
        landmarks = listOf(hasContentDescription(RailMenuDescription), text("TaskListVM"), hasContentDescription(ShowRightPaneDescription)),
    ),
    ShellScene(
        "compact-empty", ScenarioData.of("empty"), width = 1000, height = 700,
        landmarks = listOf(text("Waiting for an app to connect"), text("No sessions")),
    ),
)

internal val ShellScenes = ShellScreens + ShellViewModelScenes + ShellCompactScenes

/** The landmarks of [scene] that nothing composed matches. */
private fun DesktopComposeUiTest.missing(scene: ShellScene): List<SemanticsMatcher> =
    scene.landmarks.filter { onAllNodes(it).fetchSemanticsNodes().isEmpty() }

/**
 * Composes [scene] in the [dark] or the light theme, waits for its landmarks, and gives [block] the
 * window, all in. Fails with the landmarks that never came.
 */
internal fun shellSceneTest(scene: ShellScene, dark: Boolean, block: suspend DesktopComposeUiTest.() -> Unit = {}) =
    inspectorTest(width = scene.width, height = scene.height, data = scene.data) { inspector ->
        val viewState = InspectorViewState().also { it.dark = dark; scene.view(it) }
        scene.select(inspector.selection)
        setContent { InspectorWindow(inspector, viewState) }
        try {
            until { missing(scene).isEmpty() }
        } catch (_: ComposeTimeoutException) {
            throw AssertionError("${scene.name}, ${if (dark) "dark" else "light"}, never showed: ${missing(scene).joinToString { it.description }}")
        }
        waitForIdle()
        block()
    }

/** [scene] in the [dark] or the light theme, as a PNG of the scene's size. */
internal fun renderShellScene(scene: ShellScene, dark: Boolean): ByteArray {
    lateinit var png: ByteArray
    shellSceneTest(scene, dark) {
        png = checkNotNull(Image.makeFromBitmap(captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)).bytes
    }
    return png
}
