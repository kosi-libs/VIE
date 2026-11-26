@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.fixtures

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlin.test.assertEquals
import kotlin.test.fail


/** Presses and releases [key] on whatever has the keyboard, and lets what it did settle. */
internal fun DesktopComposeUiTest.press(key: Key) {
    onRoot().performKeyInput { pressKey(key) }
    waitForIdle()
}

/** What [this] is, to say it in a failure: its description, else its text. */
private fun SemanticsNode.named(): String =
    config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        ?: config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        ?: "node $id"

/**
 * Presses Tab, one press at a time, until [target] has the focus, and returns it. The order of the focus
 * is the toolkit's: a test says what it must reach, not in how many presses. It fails after [limit] presses,
 * saying what has the focus then: a node that is not focusable, or is disabled, is never reached.
 */
internal fun DesktopComposeUiTest.tabTo(target: SemanticsNodeInteraction, limit: Int = 30): SemanticsNodeInteraction {
    repeat(limit) {
        press(Key.Tab)
        if (target.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true) return target
    }
    val focused = onAllNodes(isFocused()).fetchSemanticsNodes().map { it.named() }
    fail("Tab did not reach ${target.fetchSemanticsNode().named()} in $limit presses; the focus is on $focused")
}

/** Enter, then Space, each click what has the focus once: [clicks] counts them. */
internal fun DesktopComposeUiTest.assertActivatedByEnterAndSpace(clicks: () -> Int) {
    val before = clicks()
    press(Key.Enter)
    assertEquals(before + 1, clicks(), "Enter clicked once")
    press(Key.Spacebar)
    assertEquals(before + 2, clicks(), "Space clicked once more")
}
