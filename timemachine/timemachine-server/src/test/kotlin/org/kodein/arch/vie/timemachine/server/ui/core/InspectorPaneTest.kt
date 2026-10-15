package org.kodein.arch.vie.timemachine.server.ui.core

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.newInspector
import org.kodein.arch.vie.timemachine.server.ui.sessions.SessionsVIE
import org.kodein.arch.vie.timemachine.server.ui.sessions.sessionsPane
import org.kodein.arch.vie.timemachine.server.ui.shell.StatusBarVIE
import org.kodein.arch.vie.timemachine.server.ui.shell.statusBarPane


/** The pane view, composed headlessly: the one place the composition meets the ViewModels. */
@OptIn(ExperimentalComposeUiApi::class)
class InspectorPaneTest {

    @Test
    fun `a pane is marked shown while composed, two panes share one owner, and the model reaches the content`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val inspector = ScenarioData.of("main").newInspector(scope, Dispatchers.Default)
            val sessions = inspector.sessionsPane()
            val status = inspector.statusBarPane()
            var seenSessions: SessionsVIE.Model? = null
            var seenStatus: StatusBarVIE.Model? = null

            val scene = ImageComposeScene(100, 100, Density(1f)) {
                CompositionLocalProvider(LocalInspector provides inspector) {
                    InspectorPane(sessions) { model -> seenSessions = model }
                    // A second pane under the same owner, no key but its own.
                    InspectorPane(status) { model -> seenStatus = model }
                }
            }
            try {
                scene.render()
                runBlocking { withTimeout(5_000) { inspector.visibility.shown("sessions").first { it } } }
                runBlocking { withTimeout(5_000) { inspector.visibility.shown("status").first { it } } }
                assertTrue(sessions.storeKey in inspector.viewModels.viewModelStore.keys())
                assertTrue(status.storeKey in inspector.viewModels.viewModelStore.keys())

                // The projection lands, off the UI thread, and a frame later the content has it.
                runBlocking {
                    withTimeout(5_000) {
                        while (seenSessions?.sessions?.size != 2 || seenStatus?.connectedSessions != 1) {
                            scene.render()
                            kotlinx.coroutines.delay(20)
                        }
                    }
                }
                assertEquals(2, seenSessions?.sessions?.size)
            } finally {
                scene.close()
            }
            runBlocking { withTimeout(5_000) { inspector.visibility.shown("sessions").first { !it } } }
            inspector.close()
        } finally {
            scope.cancel()
        }
    }
}
