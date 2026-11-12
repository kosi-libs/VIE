@file:OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.paneTest
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.viewModel


/**
 * The composer's view over a live inspector of the main scenario, whose panes project on a dispatcher
 * the test runs by hand: what the editor shows while no projection lands.
 */
class ComposerViewTest {

    /** Nothing is projected but in [projected]. */
    private val scheduler = TestCoroutineScheduler()

    private fun composer(block: suspend DesktopComposeUiTest.(inspector: Inspector) -> Unit) =
        paneTest(compute = StandardTestDispatcher(scheduler), content = { ComposerView(Demo.session.id, Demo.taskList) }, block = block)

    /** Lets the panes project what there is to, until [condition]. */
    private fun DesktopComposeUiTest.projected(condition: () -> Boolean) = until { scheduler.runCurrent(); condition() }

    private fun DesktopComposeUiTest.editorShown(): Boolean = onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()

    private fun DesktopComposeUiTest.editorText(): String =
        onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /** The segment of kind [name]: the type field shows a type's name as well, which can be "Model". */
    private fun segment(name: String) = hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun Inspector.draft(): String? = viewModel(composerPane(Demo.session.id, Demo.taskList)).modelFlow.value.controls.draft

    @Test
    fun `what is typed shows at once, and Reset brings the prefill back, with no projection in between`() = composer { inspector ->
        projected { editorShown() }
        val prefill = editorText()

        onNode(hasSetTextAction()).performTextInput("a")
        // Then two characters within one frame, as fast typing gives: the second goes into what the first left.
        onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.InsertTextAtCursor) { type ->
            type(AnnotatedString("b"))
            type(AnnotatedString("c"))
        }
        waitForIdle()
        assertEquals("abc$prefill", editorText())
        assertEquals("abc$prefill", inspector.draft())

        onNodeWithText("Reset").performClick()
        until { inspector.draft() == null }
        waitForIdle()
        assertEquals(prefill, editorText())
    }

    @Test
    fun `Send is disabled for as long as the JSON does not parse, and the kind chosen is the selected segment`() = composer { _ ->
        projected { editorShown() }
        onNodeWithText("Replace model").assertIsEnabled()
        onNode(segment("Model")).assertIsSelected()
        onNode(segment("Intent")).assertIsNotSelected()

        onNode(hasSetTextAction()).performTextInput("not JSON ")

        projected { runCatching { onNodeWithText("Replace model").assertIsNotEnabled() }.isSuccess }
    }

    @Test
    fun `the type menu is as wide as its field`() = composer { _ ->
        projected { editorShown() }
        onNode(segment("Intent")).performClick()
        projected { runCatching { onNode(segment("Intent")).assertIsSelected() }.isSuccess }
        // The field ends with the arrow that opens its menu; an option of the menu starts with its check.
        val field = hasClickAction() and hasText("arrow_drop_down")
        val option = hasClickAction() and hasText("check") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        projected { onAllNodes(field).fetchSemanticsNodes().size == 1 }
        val width = onNode(field).fetchSemanticsNode().size.width

        onNode(field).performClick()
        projected { onAllNodes(option).fetchSemanticsNodes().size > 1 }

        assertEquals(listOf(width), onAllNodes(option).fetchSemanticsNodes().map { it.size.width }.distinct())
    }

    @Test
    fun `a request shows in the editor before any projection lands`() = composer { inspector ->
        projected { editorShown() }

        inspector.selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, "{\"#type\": \"x\"}")
        until { inspector.draft() == "{\"#type\": \"x\"}" }
        waitForIdle()

        assertEquals("{\"#type\": \"x\"}", editorText())
    }

    @Test
    fun `the editor numbers its lines in one text, however many`() = composer { inspector ->
        projected { editorShown() }
        val draft = (1..300).joinToString("\n") { "\"line$it\"" }

        inspector.selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, draft)
        until { inspector.draft() == draft }
        waitForIdle()

        assertEquals(0, onAllNodes(hasText("150")).fetchSemanticsNodes().size, "a line's number is a node of its own")
        assertEquals(1, onAllNodes(hasText("149\n150\n151", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun `the recent interventions end with how many older ones there are`() = composer { inspector ->
        projected { editorShown() }
        assertEquals(0, onAllNodes(hasText("older", substring = true)).fetchSemanticsNodes().size)

        Demo.Builder(Demo.connection).apply {
            repeat(25) {
                intervene(Demo.taskList, Uuid.fromLongs(7L, it.toLong()), VIEObjectKind.Intent, Demo.DeleteTask, Demo.deleteTask(Demo.Milk), Demo.at(29_000L + it))
            }
        }.events.forEach(inspector.store::post)
        projected { onAllNodes(hasText("#29")).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()

        // 4 in the scenario and 25 more: #29 down to #10 are listed.
        assertEquals(1, onAllNodes(hasText("#10")).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(hasText("#9")).fetchSemanticsNodes().size, "the 21st intervention is listed")
        assertEquals(1, onAllNodes(hasText("9 older")).fetchSemanticsNodes().size)
    }

    @Test
    fun `a pending intervention says how long its store waits`() =
        // The store of this scenario waits an hour, not the default five seconds.
        paneTest(id = "compPending", compute = StandardTestDispatcher(scheduler), content = { ComposerView(Demo.session.id, Demo.taskList) }) {
            projected { editorShown() }

            onNodeWithText("Replace model").performClick()
            projected { onAllNodes(hasText("times out after", substring = true)).fetchSemanticsNodes().isNotEmpty() }

            onNode(hasText("times out after", substring = true)).assertTextContains("times out after 1 h", substring = true)
        }
}
