@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.Rendered
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.countingRecompositions
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.store.Row


/** What unfolding a node recomposes of a JSON tree: the node's line, and the lines it adds. */
class JsonTreeRecompositionTest {

    /** Its root open, five lines: `{`, `a: 1`, `b: {…}`, `e: 4`, `}`. */
    private val json = buildJsonObject {
        put("a", 1)
        putJsonObject("b") { put("c", 2); put("d", 3) }
        put("e", 4)
    }

    @Test
    fun `unfolding a node of the Detail tab recomposes its line and composes the lines it adds, and no other`() {
        val observation = VIEObservation(Demo.taskList, Demo.at(0), VIEObjectKind.Model, Demo.TaskListModel, "Model", json)
        val row = Row(TimeMachineClientMessage.Observed(observation, TimeMachineSequence(1)), length = 0)
        val vie = MockVIE<DetailVIE.Model, DetailVIE.Intent, NoSignal>(DetailVIE.Model(projection = DetailProjection(row = row, viewModelId = Demo.taskList)))
        assertUnfoldingRecomposesItsLinesOnly(content = { vie.Rendered { DetailPaneContent(it) } }) {
            // As the ViewModel does for the intent the tree's line posts.
            vie.modelFlow.value = vie.model.run { copy(controls = controls.copy(jsonView = controls.jsonView.toggled(listOf("b")))) }
        }
    }

    @Test
    fun `unfolding a node of the pinned model recomposes its line and composes the lines it adds, and no other`() {
        val vie = MockVIE<CurrentModelVIE.Model, CurrentModelVIE.Intent, NoSignal>(
            CurrentModelVIE.Model(
                projection = CurrentModelProjection(
                    typeName = Demo.TaskListModel, sequence = TimeMachineSequence(2), asString = "Model", json = json,
                    // A changed node: its line draws the highlight, which every line is handed.
                    changedPaths = setOf(listOf("a")), changedFrom = TimeMachineSequence(1),
                ),
            ),
        )
        assertUnfoldingRecomposesItsLinesOnly(content = { vie.Rendered { CurrentModelContent(it) } }) {
            vie.modelFlow.value = vie.model.run { copy(controls = controls.copy(jsonView = controls.jsonView.toggled(listOf("b")))) }
        }
    }

    private fun assertUnfoldingRecomposesItsLinesOnly(content: @Composable () -> Unit, unfold: () -> Unit) =
        countingRecompositions(TreeLineRow) { recompositions ->
            contentTest(content = content) {
                // The highlight fades out first. It is read when a line is drawn, so it composes nothing.
                mainClock.advanceTimeBy(3_000)
                waitForIdle()
                assertEquals(5, recompositions[TreeLineRow], "the five lines, each composed once")
                assertFalse(shown("c") || shown("d"), "b's children are folded")
                recompositions.reset()

                unfold()
                waitForIdle()

                assertTrue(shown("c") && shown("d"), "b's children are shown")
                assertEquals(4, recompositions[TreeLineRow], "b's line, then c, d and b's closing brace")
            }
        }

    private companion object {
        /** As the compiler names it for the tracer. */
        const val TreeLineRow = "org.kodein.arch.vie.timemachine.server.ui.common.TreeLineRow"
    }
}
