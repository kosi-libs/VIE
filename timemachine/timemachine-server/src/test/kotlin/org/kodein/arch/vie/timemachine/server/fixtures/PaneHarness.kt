@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/**
 * One pane's view, [content], over a live inspector of scenario [id], with what the window gives every
 * pane: the theme and the window's locals. The scene is [width] by [height] px, at a density of 1.
 *
 * The store applies its events off the UI thread and the panes project on [compute], so [block] waits
 * for what it looks at with [until]. A test that has to hold a projection back passes a [compute] it
 * runs by hand.
 */
internal fun paneTest(
    id: String = "main",
    width: Int = 900,
    height: Int = 600,
    compute: CoroutineDispatcher = Dispatchers.Default,
    content: @Composable () -> Unit,
    block: suspend DesktopComposeUiTest.(inspector: Inspector) -> Unit,
) = runDesktopComposeUiTest(width, height) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val inspector = ScenarioData.of(id).newInspector(scope, compute)
    try {
        setContent {
            InspectorTheme(dark = false) {
                CompositionLocalProvider(LocalInspector provides inspector, LocalInspectorViewState provides InspectorViewState()) {
                    content()
                }
            }
        }
        block(inspector)
    } finally {
        inspector.close()
        scope.cancel()
    }
}

/** Waits for [condition], while the Compose clock runs and the panes' projections land. */
internal fun DesktopComposeUiTest.until(condition: () -> Boolean) = waitUntil(timeoutMillis = 10_000, condition = condition)

/** Whether a node with exactly [text] is composed. */
internal fun DesktopComposeUiTest.shown(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
