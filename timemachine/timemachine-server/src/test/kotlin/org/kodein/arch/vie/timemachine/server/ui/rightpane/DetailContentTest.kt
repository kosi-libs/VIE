@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.test.pendingIntents
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.server.fixtures.Rendered
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.state
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
import org.kodein.arch.vie.timemachine.server.ui.core.Selection


/** The Detail tab's content on a model: what it shows of the selected row, and what its buttons post. */
class DetailContentTest {

    /** The Detail of row [sequence] of the main scenario's TaskListVM. */
    private fun detail(sequence: Long): MockVIE<DetailVIE.Model, DetailVIE.Intent, NoSignal> {
        val selection = Selection(scope = taskListScope, row = TimeMachineSequence(sequence))
        return MockVIE(DetailVIE.Model(projection = detailProjection(ScenarioData.of("main").state(), selection, raw = false)))
    }

    @Test
    fun `without a selected row, it says how to select one`() {
        val vie = MockVIE<DetailVIE.Model, DetailVIE.Intent, NoSignal>(DetailVIE.Model())
        contentTest(content = { vie.Rendered { DetailPaneContent(it) } }) {
            assertTrue(shown("No entry selected"))
        }
    }

    @Test
    fun `a Model's row shows what changed and the whole model, and its button asks to edit it`() {
        val vie = detail(208)
        val diff = assertNotNull(vie.model.projection.diff, "row #208 is a Model with one before it")
        assertTrue(vie.model.projection.canIntervene)
        contentTest(width = 420, height = 900, content = { vie.Rendered { DetailPaneContent(it) } }) {
            assertTrue(shown("CHANGES VS #${diff.previous}"))
            assertTrue(shown("TOSTRING"))
            assertTrue(shown("FULL MODEL"))

            onNodeWithText("Edit & intervene").performClick()

            assertEquals(listOf<DetailVIE.Intent>(DetailVIE.Intent.EditAndIntervene), vie.pendingIntents())
        }
    }

    @Test
    fun `a row the inspector sent says so, and shows its interventions`() {
        val vie = detail(238)
        val number = assertNotNull(vie.model.projection.interventionNumber, "row #238 is the result of an intervention")
        contentTest(width = 420, height = 900, content = { vie.Rendered { DetailPaneContent(it) } }) {
            assertTrue(onNodeWithText("intervention #$number", substring = true).isDisplayed())

            onNodeWithText("Show").performClick()

            assertEquals(listOf<DetailVIE.Intent>(DetailVIE.Intent.ShowInterventions), vie.pendingIntents())
        }
    }
}
