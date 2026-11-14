@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.KeptClipboard
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/** What the screen says of a listener that did not start, by what stopped it. */
class CannotListenTest {

    private val closeTheOther = "Close the other inspector window."

    @Test
    fun `a port in use is what the screen tells how to free`() =
        contentTest(content = { CannotListenScreen(ListenerState.Failed.Reason.PortInUse, "Address already in use") {} }) {
            waitForIdle()

            assertTrue(shown(closeTheOther))
            assertTrue(shown("lsof -i :13229"))
            assertTrue(shown("Address already in use · 0.0.0.0:13229"))
        }

    @Test
    fun `another failure shows what it says, and no way to free a port`() =
        contentTest(content = { CannotListenScreen(ListenerState.Failed.Reason.Other, "Too many open files") {} }) {
            waitForIdle()

            assertTrue(shown("Too many open files"))
            assertTrue(shown("Retry"))
            assertFalse(shown(closeTheOther))
            assertFalse(shown("lsof -i :13229"))
        }

    @Test
    fun `the copy glyph copies the command, and is a check for a moment`() {
        val clipboard = KeptClipboard()
        contentTest(content = {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                CannotListenScreen(ListenerState.Failed.Reason.PortInUse, "Address already in use") {}
            }
        }) {
            waitForIdle()
            onNodeWithContentDescription(CopyCommandDescription).assertTextEquals(Glyph.ContentCopy.ligature)

            onNodeWithContentDescription(CopyCommandDescription).performClick()
            waitForIdle()

            assertEquals("lsof -i :13229", clipboard.text)
            onNodeWithContentDescription(CopyCommandDescription).assertTextEquals(Glyph.Check.ligature)

            mainClock.advanceTimeBy(2_000)
            onNodeWithContentDescription(CopyCommandDescription).assertTextEquals(Glyph.ContentCopy.ligature)
        }
    }
}
