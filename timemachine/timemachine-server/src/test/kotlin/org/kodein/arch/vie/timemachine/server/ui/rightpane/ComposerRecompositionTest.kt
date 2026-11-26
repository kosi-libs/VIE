@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.countingRecompositions
import org.kodein.arch.vie.timemachine.server.fixtures.paneTest
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.ui.core.viewModel


/**
 * What a keystroke in the composer's editor recomposes. The controls change at once and the projection
 * is built again a moment later, from the same store: the type field, the result card and the recent
 * interventions show nothing of what is typed, so none of them has a reason to run again.
 */
class ComposerRecompositionTest {

    /** Nothing is projected but when the test says so. */
    private val scheduler = TestCoroutineScheduler()

    /** Lets the panes project what there is to, and the scene compose it. */
    private fun DesktopComposeUiTest.settle() = repeat(3) {
        scheduler.runCurrent()
        waitForIdle()
    }

    @Test
    fun `typing recomposes neither the type field, nor the result card, nor the recent interventions`() =
        countingRecompositions(TypeField, ResultCard, RecentInterventions) { recompositions ->
            paneTest(compute = StandardTestDispatcher(scheduler), content = { ComposerView(Demo.session.id, Demo.taskList) }) { inspector ->
                until { scheduler.runCurrent(); onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
                // The result card shows the intervention the controls ask for: one of those the scenario has.
                val vm = inspector.viewModel(inspector.composerPane(Demo.session.id, Demo.taskList))
                val requested = inspector.store.state.value.sessions.getValue(Demo.session.id).viewModels.getValue(Demo.taskList).interventions.keys.first()
                vm.post(ComposerVIE.Intent.ShowResult(requested))
                until { scheduler.runCurrent(); recompositions[ResultCard] > 0 }
                settle()
                assertTrue(recompositions[TypeField] > 0 && recompositions[RecentInterventions] > 0, "the scene composes them to begin with")

                // A character that keeps the JSON valid, then one that breaks it: the projection changes only for the second.
                for (typed in listOf(" ", "x")) {
                    recompositions.reset()
                    onNode(hasSetTextAction()).performTextInput(typed)
                    settle()
                    assertEquals(
                        mapOf("TypeField" to 0, "ResultCard" to 0, "RecentInterventions" to 0),
                        mapOf("TypeField" to recompositions[TypeField], "ResultCard" to recompositions[ResultCard], "RecentInterventions" to recompositions[RecentInterventions]),
                        "composed again after \"$typed\"",
                    )
                }
                assertTrue(onAllNodes(hasText("Fix it to send", substring = true)).fetchSemanticsNodes().isNotEmpty(), "the projection landed")
            }
        }

    private companion object {
        /** As the compiler names them for the tracer. */
        const val TypeField = "org.kodein.arch.vie.timemachine.server.ui.rightpane.TypeField"
        const val ResultCard = "org.kodein.arch.vie.timemachine.server.ui.rightpane.ResultCard"
        const val RecentInterventions = "org.kodein.arch.vie.timemachine.server.ui.rightpane.RecentInterventions"
    }
}
