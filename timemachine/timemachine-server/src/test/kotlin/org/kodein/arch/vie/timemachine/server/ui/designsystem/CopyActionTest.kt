@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import org.kodein.arch.vie.timemachine.server.fixtures.KeptClipboard
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/** What a copy action does: it reads its text at the click only, puts it on the clipboard, and is a check for a moment. */
class CopyActionTest {

    @Test
    fun `a copy action reads its text at the click, copies it, and is a check for a moment`() {
        val clipboard = KeptClipboard()
        var reads = 0
        contentTest(content = {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                CopyAction(text = { reads++; "{\"done\": true}" }, description = "Copy it")
            }
        }) {
            val copy = onNodeWithContentDescription("Copy it")
            assertEquals(0, reads, "the text was computed before any click")
            copy.assertTextEquals(Glyph.ContentCopy.ligature)

            copy.performClick()
            waitForIdle()
            assertEquals("{\"done\": true}", clipboard.text)
            assertEquals(1, reads)
            copy.assertTextEquals(Glyph.Check.ligature)

            mainClock.advanceTimeBy(2_000)
            copy.assertTextEquals(Glyph.ContentCopy.ligature)
        }
    }
}
