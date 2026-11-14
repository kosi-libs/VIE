@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/** What a toggle chip is: a checkbox that says whether it is on, and leads with a check when it is. */
class ToggleChipTest {

    @Test
    fun `a toggle chip is a checkbox that says whether it is on, and leads with a check when it is`() {
        var on by mutableStateOf(false)
        contentTest(content = {
            ToggleChip("Intervened only", selected = on, onClick = { on = !on }, tone = ChipTone.Intervened, count = 2, glyph = Glyph.TouchApp)
        }) {
            val chip = onNode(hasText("Intervened only") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            chip.assertIsNotSelected()
            assertTrue(onAllNodes(hasText(Glyph.TouchApp.ligature)).fetchSemanticsNodes().isNotEmpty())
            chip.performClick()
            waitForIdle()
            chip.assertIsSelected()
            assertTrue(onAllNodes(hasText(Glyph.Check.ligature)).fetchSemanticsNodes().isNotEmpty())
        }
    }
}
