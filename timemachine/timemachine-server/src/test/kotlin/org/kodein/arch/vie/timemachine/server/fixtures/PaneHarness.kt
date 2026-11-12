@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.fixtures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.kodein.arch.vie.timemachine.server.InspectorWindow
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/**
 * A Compose scene of [width] by [height] px, at a density of 1, and a live inspector of scenario [id],
 * or of [data] when the catalogue has no scenario for it. [block] composes what it tests.
 *
 * Nothing runs on a real dispatcher. The store, the probe and the panes' projections are queued on the
 * scheduler of the scene's own clock, which runs them on the UI thread as it advances, a frame at a
 * time: a delay of the store or of the probe is virtual, and what the store publishes is composed in
 * the order it was published. So [block] waits for what it looks at with [until], which advances that
 * clock. A test that has to hold a projection back passes a [compute] it runs by hand.
 */
internal fun inspectorTest(
    id: String = "main",
    width: Int = 900,
    height: Int = 600,
    compute: CoroutineDispatcher? = null,
    data: ScenarioData = ScenarioData.of(id),
    block: suspend DesktopComposeUiTest.(inspector: Inspector) -> Unit,
) {
    val scheduler = TestCoroutineScheduler()
    runDesktopComposeUiTest(width, height, effectContext = scheduler) {
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val inspector = data.newInspector(scope, compute ?: dispatcher)
        try {
            block(inspector)
        } finally {
            inspector.close()
            scope.cancel()
        }
    }
}

/**
 * One pane's view, [content], over the live inspector of [inspectorTest], with what the window gives
 * every pane: the theme and the window's locals.
 */
internal fun paneTest(
    id: String = "main",
    width: Int = 900,
    height: Int = 600,
    compute: CoroutineDispatcher? = null,
    data: ScenarioData = ScenarioData.of(id),
    content: @Composable () -> Unit,
    block: suspend DesktopComposeUiTest.(inspector: Inspector) -> Unit,
) = inspectorTest(id, width, height, compute, data) { inspector ->
    val viewState = InspectorViewState()
    setContent {
        InspectorTheme(dark = false) {
            CompositionLocalProvider(LocalInspector provides inspector, LocalInspectorViewState provides viewState) {
                content()
            }
        }
    }
    block(inspector)
}

/**
 * The real [InspectorWindow], [width] wide in a scene of 1440 by 900 px, over the live inspector of
 * [inspectorTest]. [block] is given the window's view state, and what changes the window's width.
 */
internal fun windowTest(
    id: String = "main",
    width: Dp = 1440.dp,
    block: suspend DesktopComposeUiTest.(inspector: Inspector, viewState: InspectorViewState, resize: (Dp) -> Unit) -> Unit,
) = inspectorTest(id, width = 1440, height = 900) { inspector ->
    val viewState = InspectorViewState()
    var current by mutableStateOf(width)
    setContent { Box(Modifier.width(current).fillMaxHeight()) { InspectorWindow(inspector, viewState) } }
    block(inspector, viewState) { current = it }
}

/**
 * Advances the scene's clock, a frame at a time, until [condition]: each frame runs what the store, the
 * probe and the panes' projections have queued, and composes what they changed. The timeout only stops a
 * condition that never comes.
 */
internal fun DesktopComposeUiTest.until(condition: () -> Boolean) = waitUntil(timeoutMillis = 10_000, condition = condition)

/** Whether a node with exactly [text] is composed. */
internal fun DesktopComposeUiTest.shown(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/** Whether a node described as [description] is composed. */
internal fun DesktopComposeUiTest.described(description: String): Boolean = onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
