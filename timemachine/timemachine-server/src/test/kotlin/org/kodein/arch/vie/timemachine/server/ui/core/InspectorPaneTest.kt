@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.core

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.kodein.arch.vie.timemachine.server.fixtures.inspectorTest
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.ui.sessions.SessionsVIE
import org.kodein.arch.vie.timemachine.server.ui.sessions.sessionsPane
import org.kodein.arch.vie.timemachine.server.ui.shell.StatusBarVIE
import org.kodein.arch.vie.timemachine.server.ui.shell.statusBarPane


/** The pane view, composed with nothing around it: the one place the composition meets the ViewModels. */
class InspectorPaneTest {

    @Test
    fun `a pane is marked shown while composed, two panes share one owner, and the model reaches the content`() =
        inspectorTest(width = 100, height = 100) { inspector ->
            val sessions = inspector.sessionsPane()
            val status = inspector.statusBarPane()
            var seenSessions: SessionsVIE.Model? = null
            var seenStatus: StatusBarVIE.Model? = null
            var composed by mutableStateOf(true)
            fun shown(key: String): Boolean = runBlocking { inspector.visibility.shown(key).first() }

            setContent {
                if (composed) CompositionLocalProvider(LocalInspector provides inspector) {
                    InspectorPane(sessions) { model -> seenSessions = model }
                    // A second pane under the same owner, no key but its own.
                    InspectorPane(status) { model -> seenStatus = model }
                }
            }
            waitForIdle()
            assertTrue(shown("sessions"))
            assertTrue(shown("status"))
            assertTrue(sessions.storeKey in inspector.viewModels.viewModelStore.keys())
            assertTrue(status.storeKey in inspector.viewModels.viewModelStore.keys())

            // The store applies its events, the panes project them, and a frame later the content has them.
            until { seenSessions?.sessions?.size == 2 && seenStatus?.connectedSessions == 1 }
            assertEquals(2, seenSessions?.sessions?.size)

            composed = false
            waitForIdle()
            assertFalse(shown("sessions"))
        }
}
