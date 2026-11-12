@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.store.ListenerState


/** The status bar and the empty screen on a model: what they say of the listener and of the sessions. */
class StatusBarContentTest {

    private val listening = StatusBarVIE.Model(
        listener = ListenerState.Listening(13229),
        address = "ws://localhost:13229/vie-timemachine",
        connectedSessions = 1,
        sessions = 2,
    )

    private fun failed(reason: ListenerState.Failed.Reason, message: String) = StatusBarVIE.Model(listener = ListenerState.Failed(reason, message))

    @Test
    fun `the status bar says where it listens, and how many sessions are connected`() =
        contentTest(content = { StatusBarContent(listening, dark = false, onToggleTheme = {}) }) {
            assertTrue(shown("Listening on"))
            assertTrue(shown("ws://localhost:13229/vie-timemachine"))
            assertTrue(shown("1 of 2 sessions connected"))
            assertTrue(shown(SecurityReminder))
        }

    @Test
    fun `the status bar of an inspector without a session has no key hints`() =
        contentTest(content = { StatusBarContent(listening.copy(connectedSessions = 0, sessions = 0), dark = false, onToggleTheme = {}) }) {
            assertTrue(shown("No sessions"))
            assertFalse(shown("↑↓ step · ⏎ expand · E edit & intervene · F follow"))
        }

    @Test
    fun `the status bar blames the port only when it is in use`() {
        contentTest(content = { StatusBarContent(failed(ListenerState.Failed.Reason.PortInUse, "Address already in use"), dark = false, onToggleTheme = {}) }) {
            assertTrue(shown("Not listening — port 13229 is in use"))
        }
        contentTest(content = { StatusBarContent(failed(ListenerState.Failed.Reason.Other, "Too many open files"), dark = false, onToggleTheme = {}) }) {
            assertTrue(shown("Not listening — port 13229 could not be opened"))
        }
    }

    @Test
    fun `the theme's glyph says what a click on it does, and does it`() {
        var toggled = 0
        // As wide as the full layout: narrower, the window is compact, and the bar leaves its key hints out.
        contentTest(width = 1440, content = { StatusBarContent(listening, dark = false, onToggleTheme = { toggled++ }) }) {
            onNodeWithContentDescription(DarkThemeDescription).performClick()

            assertEquals(1, toggled)
        }
    }

    @Test
    fun `the empty screen gives the address to connect to, once the listener has one`() {
        contentTest(content = { EmptyContent(StatusBarVIE.Model()) }) {
            assertTrue(shown("Waiting for an app to connect"))
            assertTrue(shown("Starting the listener…"))
        }
        contentTest(content = { EmptyContent(listening) }) {
            assertTrue(shown("ws://localhost:13229/vie-timemachine"))
            assertFalse(shown("Starting the listener…"))
        }
    }
}
