@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.ui.common.KindBadge


/** What a chip is: centred in its height and width, as high as its size, and a button only when given a click. Sizes are in px: the harness's density is 1. */
class ChipTest {

    @Test
    fun `a kind badge centres its name, whatever the line height of its text`() =
        contentTest(content = { Box(Modifier.testTag("badge")) { KindBadge(VIEObjectKind.Intent) } }) {
            val badge = onNodeWithTag("badge").fetchSemanticsNode().boundsInRoot
            val name = onNodeWithText("Intent", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(18f, badge.height)
            assertEquals(name.top - badge.top, badge.bottom - name.bottom, absoluteTolerance = 0.5f, message = "above and below the name")
            assertEquals(name.left - badge.left, badge.right - name.right, absoluteTolerance = 1f, message = "before and after the name")
        }

    @Test
    fun `a chip is as high as its size`() =
        contentTest(content = {
            Column {
                Chip("alive", ChipTone.Success, Modifier.testTag("small"))
                Chip("lsof -i :13229", ChipTone.Neutral, Modifier.testTag("medium"), mono = true, size = ChipSize.Medium)
            }
        }) {
            assertEquals(listOf(18, 24), listOf("small", "medium").map { onNodeWithTag(it).fetchSemanticsNode().size.height })
        }

    @Test
    fun `only a chip given a click is a button`() {
        var clicks = 0
        contentTest(content = {
            Column {
                Chip("initial", ChipTone.Neutral)
                Chip("TaskListVM 3f2a", ChipTone.Neutral, mono = true, onClick = { clicks++ })
            }
        }) {
            onNodeWithText("initial").assertHasNoClickAction()
            onNodeWithText("TaskListVM 3f2a").assertHasClickAction().performClick()
            assertEquals(1, clicks)
        }
    }
}
