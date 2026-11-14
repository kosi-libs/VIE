@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest


/**
 * The room a rule takes: across what it is given, weighted beside its siblings, or down the height of its
 * row, and one rule thick the other way. Sizes are in px: the harness's density is 1.
 */
class RuleTest {
    private val tag = "rule"

    @Test
    fun `a weighted horizontal rule takes the width its siblings leave, and is one rule thick`() = contentTest(
        content = {
            Row(Modifier.width(200.dp)) {
                Box(Modifier.width(50.dp).height(10.dp))
                HorizontalRule(Modifier.weight(1f).testTag(tag))
            }
        },
    ) {
        assertEquals(IntSize(150, 1), onNodeWithTag(tag).fetchSemanticsNode().size)
    }

    @Test
    fun `a vertical rule takes the height of its row, and is one rule thick`() = contentTest(
        content = { Row(Modifier.height(32.dp)) { VerticalRule(Modifier.testTag(tag)) } },
    ) {
        assertEquals(IntSize(1, 32), onNodeWithTag(tag).fetchSemanticsNode().size)
    }
}
