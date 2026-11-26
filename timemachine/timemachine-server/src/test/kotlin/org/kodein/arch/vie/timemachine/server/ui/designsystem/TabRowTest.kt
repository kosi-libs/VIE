@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest


/** What a tab row is: tabs, the shown one saying so, each read with its badge. */
class TabRowTest {

    @Test
    fun `a tab is a tab, the shown one says so, and its badge is read with it`() {
        var picked: String? = null
        contentTest(content = {
            TabRow(listOf("Detail", "Intervene"), "Detail", onSelect = { picked = it }, label = { it }, badge = { if (it == "Intervene") "3" else null })
        }) {
            val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            onNode(hasText("Detail") and tab).assertIsSelected()
            onNode(hasText("Intervene") and hasText("3") and tab).assertIsNotSelected().performClick()
            assertEquals("Intervene", picked)
        }
    }
}
