@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewScope
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/**
 * A pane's content, [content], with nothing behind it: no inspector, no store, no ViewModel, only the
 * theme and the window's view state. The scene is [width] by [height] px, at a density of 1.
 *
 * Nothing runs off the UI thread, so [block] waits for nothing but the composition (`waitForIdle`).
 */
internal fun contentTest(
    width: Int = 900,
    height: Int = 600,
    viewState: InspectorViewState = InspectorViewState(),
    content: @Composable () -> Unit,
    block: suspend DesktopComposeUiTest.() -> Unit,
) = runDesktopComposeUiTest(width, height) {
    setContent {
        InspectorTheme(dark = false) {
            CompositionLocalProvider(LocalInspectorViewState provides viewState) { content() }
        }
    }
    waitForIdle()
    block()
}

/** [content] on this mock's current model, composed again each time the test gives it another. */
@Composable
internal fun <Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> MockVIE<Model, Intent, Signal>.Rendered(
    content: @Composable VIEViewScope<Intent, Signal>.(Model) -> Unit,
) {
    val model by modelFlow.collectAsState()
    content(model)
}
