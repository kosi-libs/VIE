@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest


/** What a text link is: at least as high as a link's least height whatever its line, and, disabled, a link that takes no click. Sizes are in px: the harness's density is 1. */
class LinkActionTest {

    @Test
    fun `a link is at least as high as a link's least height, whatever its line`() =
        contentTest(content = { LinkAction("Show", onClick = {}) }) {
            assertEquals(22, onNodeWithText("Show").fetchSemanticsNode().size.height)
        }

    @Test
    fun `a disabled link says so, and takes no click`() {
        var clicks = 0
        contentTest(content = { LinkAction("Reset", onClick = { clicks++ }, enabled = false) }) {
            onNodeWithText("Reset").assertIsNotEnabled().performClick()
            assertEquals(0, clicks)
        }
    }
}
