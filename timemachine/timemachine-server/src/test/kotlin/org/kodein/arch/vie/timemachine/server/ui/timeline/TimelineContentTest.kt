@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.timeline

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.test.pendingIntents
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.Rendered
import org.kodein.arch.vie.timemachine.server.fixtures.SampleWorld
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.state
import org.kodein.arch.vie.timemachine.server.ui.common.ModelDiffs
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.Selection
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/**
 * The timeline's content on a model, with no ViewModel behind it: what a model prints, and what the
 * keys and the clicks post.
 */
class TimelineContentTest {

    private val taskList = InspectorScope.ViewModel(Demo.session.id, Demo.taskList)
    private val session = InspectorScope.Session(Demo.session.id)

    /** The header's chip of the Model's type, which the glyph of a serializable type tells from the filter's chip. */
    private val modelTypeChip = hasText("Model") and hasText(Glyph.DataObject.ligature)

    /** The timeline of [scope] in [data], as its pane projects it under [controls]. */
    private fun timeline(
        scope: InspectorScope,
        data: ScenarioData = ScenarioData.of("main"),
        controls: TimelineVIE.Controls = TimelineVIE.Controls(),
    ) = TimelineVIE.Model(controls, TimelineProjector(scope, ModelDiffs()).project(data.state(), Selection(scope), controls))

    private fun mock(model: TimelineVIE.Model) = MockVIE<TimelineVIE.Model, TimelineVIE.Intent, NoSignal>(model)

    /** A timeline that only shows its markers: no observation matches, they always pass, and a tall scene composes them all. */
    private fun onlyMarkers(scope: InspectorScope) =
        timeline(scope, controls = TimelineVIE.Controls(filter = TimelineFilter(query = "no row has this")))

    /** The sequences of the observations' rows that are whole in the list, from the top. */
    private fun DesktopComposeUiTest.wholeRows(): List<TimeMachineSequence> {
        val list = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        return onAllNodes(hasClickAction() and hasText("#", substring = true)).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= list.top && it.boundsInRoot.bottom <= list.bottom }
            .sortedBy { it.boundsInRoot.top }
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text?.removePrefix("#")?.toLongOrNull() }
            .map(::TimeMachineSequence)
    }

    @Test
    fun `after a click on a row, Enter expands the selected row`() {
        val vie = mock(timeline(taskList))
        var selected: TimeMachineSequence? = null
        contentTest(height = 300, content = { vie.Rendered { TimelineContent(taskList, it, selectedRow = { selected }) } }) {
            val clicked = wholeRows().firstOrNull() ?: fail("no row is whole in the list")
            val observations = vie.model.projection.items.filter { it.observation != null }.mapNotNull { it.sequence }
            val target = observations[observations.indexOf(clicked) + 2]
            vie.pendingIntents()

            // A click with the mouse, which gives the row the focus.
            onNode(hasClickAction() and hasText("#$clicked")).performClick()
            repeat(2) { onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
            selected = target
            onRoot().performKeyInput { pressKey(Key.Enter) }

            assertEquals(
                listOf(
                    TimelineVIE.Intent.SelectRow(clicked),
                    TimelineVIE.Intent.Step(1),
                    TimelineVIE.Intent.Step(1),
                    TimelineVIE.Intent.ToggleExpanded(target),
                ),
                vie.pendingIntents(),
                "Enter went to the row that has the focus",
            )
        }
    }

    @Test
    fun `a row's chevron says what a click on it does, and posts it`() {
        val model = timeline(taskList)
        val vie = mock(model)
        contentTest(height = 300, content = { vie.Rendered { TimelineContent(taskList, it, selectedRow = { null }) } }) {
            assertTrue(onAllNodesWithContentDescription(CollapseRowDescription).fetchSemanticsNodes().isEmpty())
            val last = wholeRows().last()
            vie.pendingIntents()

            // The last row: the timeline ends on it, whole.
            onAllNodesWithContentDescription(ExpandRowDescription).onLast().performClick()
            assertEquals(listOf<TimelineVIE.Intent>(TimelineVIE.Intent.ToggleExpanded(last)), vie.pendingIntents())

            vie.modelFlow.value = model.copy(controls = model.controls.copy(expanded = setOf(last)))
            waitForIdle()
            assertEquals(1, onAllNodesWithContentDescription(CollapseRowDescription).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `while the search field has the keyboard, the timeline's keys are not taken`() {
        val vie = mock(timeline(taskList))
        val selected = vie.model.projection.items.last { it.observation != null }.sequence
        contentTest(height = 300, content = { vie.Rendered { TimelineContent(taskList, it, selectedRow = { selected }) } }) {
            onNode(hasSetTextAction()).performClick()
            waitForIdle()
            vie.pendingIntents()

            onRoot().performKeyInput { pressKey(Key.Enter); pressKey(Key.F) }
            onNode(hasSetTextAction()).performTextInput("x")

            assertEquals(listOf<TimelineVIE.Intent>(TimelineVIE.Intent.SetQuery("x")), vie.pendingIntents(), "Enter or F was taken from the search field")
        }
    }

    /** A session whose TaskListVM has one row, #0: a Model of [keys] keys, so its JSON is [keys] + 2 lines of text. */
    private fun aModelOf(keys: Int) = ScenarioData(
        "large",
        Demo.Builder(Demo.connection).apply {
            hello(Demo.session, Demo.at(0))
            observed(Demo.taskList, 0, Demo.at(100), VIEObjectKind.Model, Demo.TaskListModel, buildJsonObject { repeat(keys) { put("key$it", JsonPrimitive(it)) } })
        }.events,
    )

    /** The lines of JSON text that are composed and have a key: `"key0": 0`, which the row's `toString` writes without a space. */
    private fun DesktopComposeUiTest.jsonKeyLines(): Int = onAllNodes(hasText("\": ", substring = true)).fetchSemanticsNodes().size

    private fun DesktopComposeUiTest.moreLines(): List<String> =
        onAllNodes(hasText("more line", substring = true)).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].first().text }

    private fun expandedModelOf(keys: Int) =
        mock(timeline(taskList, aModelOf(keys), TimelineVIE.Controls(expanded = setOf(TimeMachineSequence(0)))))

    @Test
    fun `an expanded row shows the first 40 lines of its JSON, and says how many more Detail has`() {
        val long = expandedModelOf(keys = 200)
        contentTest(height = 1200, content = { long.Rendered { TimelineContent(taskList, it, selectedRow = { null }) } }) {
            // The opening bracket, then 39 keys.
            assertEquals(39, jsonKeyLines())
            assertEquals(listOf("… 162 more lines — see Detail"), moreLines())
        }
        val oneMore = expandedModelOf(keys = 39)
        contentTest(height = 1200, content = { oneMore.Rendered { TimelineContent(taskList, it, selectedRow = { null }) } }) {
            assertEquals(39, jsonKeyLines())
            assertEquals(listOf("… 1 more line — see Detail"), moreLines())
        }
    }

    @Test
    fun `an expanded row whose JSON has 40 lines shows them all`() {
        val whole = expandedModelOf(keys = 38)
        contentTest(height = 1200, content = { whole.Rendered { TimelineContent(taskList, it, selectedRow = { null }) } }) {
            assertEquals(38, jsonKeyLines())
            assertEquals(emptyList(), moreLines())
        }
    }

    @Test
    fun `the type info opens over the timeline, which stays where it is, and Escape or a click outside closes it`() {
        val vie = mock(timeline(taskList))
        contentTest(height = 500, content = { vie.Rendered { TimelineContent(taskList, it, selectedRow = { null }) } }) {
            val columnHeads = onNodeWithText("SEQ").fetchSemanticsNode().boundsInRoot

            onNode(modelTypeChip).performClick()
            waitForIdle()
            assertTrue(shown("Type info"))
            assertEquals(columnHeads, onNodeWithText("SEQ").fetchSemanticsNode().boundsInRoot, "opening the type info moved the timeline")
            val chip = onNode(modelTypeChip).fetchSemanticsNode().boundsInWindow
            val title = onNodeWithText("Type info").fetchSemanticsNode().boundsInWindow
            assertTrue(title.top > chip.bottom && title.left < chip.left, "the type info is not under the chips: $title, $chip")

            onNodeWithText("Type info").performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertFalse(shown("Type info"), "Escape left the type info open")

            onNode(modelTypeChip).performClick()
            waitForIdle()
            assertTrue(shown("Type info"))
            onNodeWithText("SEQ").performClick()
            waitForIdle()
            assertFalse(shown("Type info"), "a click outside left the type info open")
        }
    }

    @Test
    fun `a missing range names this ViewModel in a ViewModel's timeline, and the session's ViewModels in a session's`() {
        val lostInViewModel = "entries were lost in this session here; some may belong to this ViewModel"
        val lostInSession = "entries were lost in this session here; they may belong to any of its ViewModels"

        val ofViewModel = mock(onlyMarkers(taskList))
        contentTest(height = 1200, content = { ofViewModel.Rendered { TimelineContent(taskList, it, selectedRow = { null }) } }) {
            assertTrue(shown(lostInViewModel))
        }
        val ofSession = mock(onlyMarkers(session))
        contentTest(height = 1200, content = { ofSession.Rendered { TimelineContent(session, it, selectedRow = { null }) } }) {
            assertTrue(shown("Possibly incomplete"))
            assertFalse(shown(lostInViewModel), "a session's timeline has no \"this ViewModel\"")
            assertTrue(shown(lostInSession))
        }
    }

    @Test
    fun `a ViewModel's header says whether it is alive, its session disconnected, or it is being identified`() {
        val unknown = InspectorScope.ViewModel(Demo.session.id, Demo.unknown)
        for ((scope, data, status) in listOf(
            Triple(taskList, ScenarioData.of("main"), "alive"),
            Triple(taskList, ScenarioData.of("disconnected"), "session disconnected"),
            Triple(unknown, ScenarioData.of("unknownVM"), "identifying…"),
        )) {
            val vie = mock(timeline(scope, data))
            contentTest(content = { vie.Rendered { TimelineContent(scope, it, selectedRow = { null }) } }) {
                assertTrue(shown(status), "\"$status\" in the header of ${data.id}")
            }
        }
    }

    @Test
    fun `one evicted entry is counted in the singular`() {
        // The earlier session's TaskListVM had three rows: a bound of two evicts one.
        val earlierTaskList = InspectorScope.ViewModel(Demo.oldSession.id, Demo.oldTaskList)
        val vie = mock(timeline(earlierTaskList, ScenarioData("main", SampleWorld.events(), maxRows = 2)))
        contentTest(content = { vie.Rendered { TimelineContent(earlierTaskList, it, selectedRow = { null }) } }) {
            assertFalse(shown("1 older entries evicted · timeline size limit"))
            assertTrue(shown("1 older entry evicted · timeline size limit"))
        }
    }
}
