@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest


/** What a segmented control is: a radio group, whose unavailable option is never the chosen one and takes no click, whose disabled form takes none at all, and whose segments are as wide as each other. */
class SegmentedControlTest {

    private fun option(name: String) = hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun `an option that cannot be chosen is never the chosen one, and takes no click`() {
        var picked: VIEObjectKind? = null
        contentTest(content = {
            SegmentedControl(VIEObjectKind.entries, VIEObjectKind.Signal, onSelect = { picked = it }, label = { it.name }, optionEnabled = { it != VIEObjectKind.Signal })
        }) {
            onNode(option("Signal")).assertIsNotSelected().assertIsNotEnabled().performClick()
            assertEquals(null, picked)
            onNode(option("Intent")).assertIsEnabled().performClick()
            assertEquals(VIEObjectKind.Intent, picked)
        }
    }

    @Test
    fun `a disabled control shows its choice, and takes no click`() {
        var picked: VIEObjectKind? = null
        contentTest(content = {
            SegmentedControl(VIEObjectKind.entries, VIEObjectKind.Model, onSelect = { picked = it }, label = { it.name }, enabled = false)
        }) {
            onNode(option("Model")).assertIsSelected()
            onNode(option("Intent")).assertIsNotEnabled().performClick()
            assertEquals(null, picked)
        }
    }

    @Test
    fun `its segments are as wide as each other`() =
        contentTest(content = {
            SegmentedControl(listOf("Tree", "Raw and more"), "Tree", onSelect = {}, label = { it }, size = SegmentedControlSize.Small)
        }) {
            assertEquals(1, listOf("Tree", "Raw and more").map { onNode(option(it)).fetchSemanticsNode().size.width }.distinct().size)
        }
}
