@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import kotlin.test.Test
import kotlin.test.assertFalse
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
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.paneTest
import org.kodein.arch.vie.timemachine.server.fixtures.state
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.store.Row
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.Selection
import org.kodein.arch.vie.timemachine.server.ui.core.viewModel
import org.kodein.arch.vie.timemachine.server.ui.rightpane.ComposerView
import org.kodein.arch.vie.timemachine.server.ui.rightpane.CurrentModelContent
import org.kodein.arch.vie.timemachine.server.ui.rightpane.CurrentModelProjection
import org.kodein.arch.vie.timemachine.server.ui.rightpane.CurrentModelVIE
import org.kodein.arch.vie.timemachine.server.ui.rightpane.DetailPaneContent
import org.kodein.arch.vie.timemachine.server.ui.rightpane.DetailProjection
import org.kodein.arch.vie.timemachine.server.ui.rightpane.DetailVIE
import org.kodein.arch.vie.timemachine.server.ui.rightpane.composerPane
import org.kodein.arch.vie.timemachine.server.ui.sessions.SessionsContent
import org.kodein.arch.vie.timemachine.server.ui.sessions.SessionsVIE
import org.kodein.arch.vie.timemachine.server.ui.sessions.sessionsModel
import org.kodein.arch.vie.timemachine.server.ui.theme.LightInspectorScheme
import org.kodein.arch.vie.timemachine.server.ui.timeline.TimelineContent
import org.kodein.arch.vie.timemachine.server.ui.timeline.TimelineProjector
import org.kodein.arch.vie.timemachine.server.ui.timeline.TimelineVIE


/**
 * Every pane that scrolls shows where it is: a scrollbar over its right edge while its content
 * overflows, and none while it fits. The bar has no semantics, so it is looked for in the pixels.
 */
class ScrollbarsTest {

    private val main = ScenarioData.of("main")
    private val session = InspectorScope.Session(Demo.session.id)
    private val large = buildJsonObject { repeat(200) { put("key$it", JsonPrimitive(it)) } }

    /**
     * Whether a block of the scrollbar's colour at rest, 4 px by 12 px, is in the [strip] px left of
     * [right], between [top] and [bottom]: wider than a border, higher than a rule.
     */
    private fun DesktopComposeUiTest.scrollbar(right: Int, top: Int, bottom: Int, strip: Int = 10): Boolean {
        val pixels = captureToImage().toPixelMap()
        val thumb = LightInspectorScheme.outlineVariant
        for (x in (right - strip).coerceAtLeast(0)..right - 4) for (y in top..bottom - 12) {
            if ((0 until 4).all { dx -> (0 until 12).all { dy -> pixels[x + dx, y + dy] == thumb } }) return true
        }
        return false
    }

    /** A scrollbar over the right edge of the whole scene, [width] by [height]. */
    private fun DesktopComposeUiTest.scrollbarAtTheEdge(width: Int, height: Int) = scrollbar(right = width, top = 0, bottom = height)

    private fun sessions() = MockVIE<SessionsVIE.Model, SessionsVIE.Intent, NoSignal>(sessionsModel(main.state(), selected = null, now = main.now))

    @Test
    fun `the sessions show a scrollbar while they overflow, and none while they fit`() {
        val vie = sessions()
        contentTest(width = 260, height = 200, content = { vie.Rendered { SessionsContent(it) } }) {
            assertTrue(scrollbarAtTheEdge(260, 200), "the sessions overflow 200 px and show no scrollbar")
        }
        contentTest(width = 260, height = 900, content = { vie.Rendered { SessionsContent(it) } }) {
            assertFalse(scrollbarAtTheEdge(260, 900), "the sessions fit in 900 px and show a scrollbar")
        }
    }

    @Test
    fun `the timeline shows a scrollbar while its rows overflow`() {
        val controls = TimelineVIE.Controls()
        val vie = MockVIE<TimelineVIE.Model, TimelineVIE.Intent, NoSignal>(
            TimelineVIE.Model(controls, TimelineProjector(session, ModelDiffs()).project(main.state(), Selection(session), controls)),
        )
        contentTest(width = 700, height = 300, content = { vie.Rendered { TimelineContent(session, it, selectedRow = { null }) } }) {
            assertTrue(scrollbarAtTheEdge(700, 300))
        }
    }

    @Test
    fun `the current model shows a scrollbar while its JSON overflows`() {
        val vie = MockVIE<CurrentModelVIE.Model, CurrentModelVIE.Intent, NoSignal>(
            CurrentModelVIE.Model(projection = CurrentModelProjection(typeName = Demo.TaskListModel, sequence = TimeMachineSequence(1), asString = "Model", json = large)),
        )
        contentTest(width = 420, height = 300, content = { vie.Rendered { CurrentModelContent(it) } }) {
            assertTrue(scrollbarAtTheEdge(420, 300))
        }
    }

    @Test
    fun `the Detail tab shows a scrollbar while its content overflows`() {
        val observation = VIEObservation(Demo.taskList, Demo.at(0), VIEObjectKind.Model, Demo.TaskListModel, "Model", large)
        val row = Row(TimeMachineClientMessage.Observed(observation, TimeMachineSequence(1)), length = 0)
        val vie = MockVIE<DetailVIE.Model, DetailVIE.Intent, NoSignal>(DetailVIE.Model(projection = DetailProjection(row = row, viewModelId = Demo.taskList)))
        contentTest(width = 420, height = 600, content = { vie.Rendered { DetailPaneContent(it) } }) {
            assertTrue(scrollbarAtTheEdge(420, 600))
        }
    }

    private fun DesktopComposeUiTest.editorShown(): Boolean = onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the Composer tab shows a scrollbar while its content overflows`() =
        paneTest(width = 420, height = 300, content = { ComposerView(Demo.session.id, Demo.taskList) }) {
            until { editorShown() }
            waitForIdle()

            assertTrue(scrollbarAtTheEdge(420, 300))
        }

    @Test
    fun `the editor shows a scrollbar of its own while its draft overflows, in a tab that fits`() =
        paneTest(width = 420, height = 1400, content = { ComposerView(Demo.session.id, Demo.taskList) }) { inspector ->
            until { editorShown() }
            val draft = (1..300).joinToString("\n") { "\"line$it\"" }
            inspector.selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, draft)
            until { inspector.viewModel(inspector.composerPane(Demo.session.id, Demo.taskList)).modelFlow.value.controls.draft == draft }
            waitForIdle()

            val editor = onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
            // The field ends 10 px before its frame, whose right edge the bar lies over.
            assertTrue(scrollbar(right = editor.right.toInt() + 10, top = editor.top.toInt(), bottom = editor.bottom.toInt()), "the editor's draft overflows and it shows no scrollbar")
            assertFalse(scrollbarAtTheEdge(420, 1400), "the tab fits and shows a scrollbar")
        }
}
