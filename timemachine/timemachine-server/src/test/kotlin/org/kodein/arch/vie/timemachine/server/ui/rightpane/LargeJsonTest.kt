@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.Rendered
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.store.Row
import org.kodein.arch.vie.timemachine.server.ui.common.JsonViewControls
import org.kodein.arch.vie.timemachine.server.ui.common.pretty


/**
 * A JSON document of two thousand keys in the two places that show one whole, as a tree and as text:
 * only the lines in view are composed, however long the document.
 */
class LargeJsonTest {

    private val large = buildJsonObject { repeat(2_000) { put("key$it", JsonPrimitive(it)) } }

    /** How many lines of [large] are composed: each has a key. */
    private fun DesktopComposeUiTest.composedLines(): Int = onAllNodes(hasText("key", substring = true)).fetchSemanticsNodes().size

    private fun DesktopComposeUiTest.assertOnlyTheLinesInView(most: Int) {
        val composed = composedLines()
        assertTrue(composed in 1..most, "$composed lines of 2 000 are composed, for a view of fewer than $most")
    }

    private fun currentModel(raw: Boolean) = MockVIE<CurrentModelVIE.Model, CurrentModelVIE.Intent, NoSignal>(
        CurrentModelVIE.Model(
            controls = CurrentModelVIE.Controls(JsonViewControls(raw = raw)),
            projection = CurrentModelProjection(
                typeName = Demo.TaskListModel, sequence = TimeMachineSequence(1), asString = "Model", json = large,
                rawText = if (raw) large.pretty() else null,
            ),
        ),
    )

    private fun detail(raw: Boolean): MockVIE<DetailVIE.Model, DetailVIE.Intent, NoSignal> {
        val observation = VIEObservation(Demo.taskList, Demo.at(0), VIEObjectKind.Model, Demo.TaskListModel, "Model", large)
        val row = Row(TimeMachineClientMessage.Observed(observation, TimeMachineSequence(1)), length = 0)
        return MockVIE(
            DetailVIE.Model(
                controls = DetailVIE.Controls(JsonViewControls(raw = raw)),
                projection = DetailProjection(row = row, viewModelId = Demo.taskList, rawText = if (raw) large.pretty() else null),
            ),
        )
    }

    @Test
    fun `the current model composes the lines of a large tree that are in view`() {
        val vie = currentModel(raw = false)
        contentTest(width = 420, height = 300, content = { vie.Rendered { CurrentModelContent(it) } }) { assertOnlyTheLinesInView(most = 40) }
    }

    @Test
    fun `the current model composes the lines of a large text that are in view`() {
        val vie = currentModel(raw = true)
        contentTest(width = 420, height = 300, content = { vie.Rendered { CurrentModelContent(it) } }) { assertOnlyTheLinesInView(most = 40) }
    }

    @Test
    fun `the detail composes the lines of a large tree that are in view`() {
        val vie = detail(raw = false)
        contentTest(width = 420, height = 600, content = { vie.Rendered { DetailPaneContent(it) } }) { assertOnlyTheLinesInView(most = 60) }
    }

    @Test
    fun `the detail composes the lines of a large text that are in view`() {
        val vie = detail(raw = true)
        contentTest(width = 420, height = 600, content = { vie.Rendered { DetailPaneContent(it) } }) { assertOnlyTheLinesInView(most = 60) }
    }
}
