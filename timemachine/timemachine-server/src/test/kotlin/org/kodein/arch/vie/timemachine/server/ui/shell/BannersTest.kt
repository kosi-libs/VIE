@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest


/** What the banners say, and what their action does. */
class BannersTest {

    private val session = Uuid.parse("00000000-0000-4000-8000-0000000000b1")

    @Test
    fun `a rejection banner names the probe turned away, and its action dismisses it`() {
        var dismissed = 0
        val notice = RejectionNotice(session, "Demo", protocolVersion = 1, attempts = 2, lastAttemptAt = Demo.now)
        contentTest(content = { RejectionBanner(notice) { dismissed++ } }) {
            assertEquals(1, onAllNodes(hasText("Rejected a connection from Demo", substring = true)).fetchSemanticsNodes().size)
            onNodeWithText("Dismiss").performClick()
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun `a disconnected banner names the session that went away`() {
        contentTest(content = { DisconnectedBanner(DisconnectedNotice(session, "Demo", disconnectedAt = null)) }) {
            assertTrue(onAllNodes(hasText("Demo disconnected.", substring = true)).fetchSemanticsNodes().isNotEmpty())
        }
    }
}
