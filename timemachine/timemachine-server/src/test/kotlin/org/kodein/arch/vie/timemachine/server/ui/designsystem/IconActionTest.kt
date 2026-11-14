@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/** What a glyph button is: one target whatever its glyph, that says what it is and does it when clicked. Sizes are in px: the harness's density is 1. */
class IconActionTest {

    @Test
    fun `a glyph button takes the same target whatever the size of its glyph`() =
        contentTest(content = {
            Row {
                IconAction(Glyph.LightMode, "small", onClick = {}, size = 15.dp)
                IconAction(Glyph.RightPanelClose, "large", onClick = {}, size = 18.dp)
            }
        }) {
            for (description in listOf("small", "large")) {
                assertEquals(IntSize(26, 26), onNodeWithContentDescription(description).fetchSemanticsNode().size, description)
            }
        }

    @Test
    fun `a glyph button says what it is, and a click on it does it`() {
        var clicks = 0
        contentTest(content = { IconAction(Glyph.Close, "Clear the search", onClick = { clicks++ }) }) {
            onNodeWithContentDescription("Clear the search").assertHasClickAction().performClick()
            assertEquals(1, clicks)
        }
    }
}
