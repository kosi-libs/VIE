@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import java.awt.datatransfer.DataFlavor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.paneTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent


/** What the window says of a listener that did not start: the screen and the status bar, by what stopped it. */
class CannotListenTest {

    private val closeTheOther = "Close the other inspector window."

    private fun failed(reason: ListenerState.Failed.Reason, message: String) =
        ScenarioData("failed", listOf(TimeMachineEvent.ListenerChanged(ListenerState.Failed(reason, message))))

    @Test
    fun `a port in use is what the screen tells how to free`() =
        paneTest(content = { CannotListenScreen(ListenerState.Failed.Reason.PortInUse, "Address already in use") {} }) {
            waitForIdle()

            assertTrue(shown(closeTheOther))
            assertTrue(shown("lsof -i :13229"))
            assertTrue(shown("Address already in use · 0.0.0.0:13229"))
        }

    @Test
    fun `another failure shows what it says, and no way to free a port`() =
        paneTest(content = { CannotListenScreen(ListenerState.Failed.Reason.Other, "Too many open files") {} }) {
            waitForIdle()

            assertTrue(shown("Too many open files"))
            assertTrue(shown("Retry"))
            assertFalse(shown(closeTheOther))
            assertFalse(shown("lsof -i :13229"))
        }

    @Test
    fun `the copy glyph copies the command, and is a check for a moment`() {
        val clipboard = KeptClipboard()
        paneTest(content = {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                CannotListenScreen(ListenerState.Failed.Reason.PortInUse, "Address already in use") {}
            }
        }) {
            waitForIdle()
            onNodeWithContentDescription(CopyCommandDescription).assertTextEquals("content_copy")

            onNodeWithContentDescription(CopyCommandDescription).performClick()
            waitForIdle()

            assertEquals("lsof -i :13229", clipboard.text)
            onNodeWithContentDescription(CopyCommandDescription).assertTextEquals("check")

            mainClock.advanceTimeBy(2_000)
            onNodeWithContentDescription(CopyCommandDescription).assertTextEquals("content_copy")
        }
    }

    @Test
    fun `the status bar blames the port only when it is in use`() {
        paneTest(data = failed(ListenerState.Failed.Reason.PortInUse, "Address already in use"), content = { StatusBar() }) {
            until { shown("Not listening — port 13229 is in use") }
        }
        paneTest(data = failed(ListenerState.Failed.Reason.Other, "Too many open files"), content = { StatusBar() }) {
            until { shown("Not listening — port 13229 could not be opened") }
        }
    }
}

/** A clipboard that keeps the text it is given, in place of the system's. */
@OptIn(ExperimentalComposeUiApi::class)
private class KeptClipboard : Clipboard {

    var text: String? = null
        private set

    override suspend fun getClipEntry(): ClipEntry? = null

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        text = clipEntry?.asAwtTransferable?.getTransferData(DataFlavor.stringFlavor) as String?
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override val nativeClipboard: Any get() = this
}
