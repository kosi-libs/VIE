@file:OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `a request shows in the editor before any projection lands`() = composer { inspector ->
        projected { editorShown() }

        inspector.selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, "{\"#type\": \"x\"}")
        until { inspector.draft() == "{\"#type\": \"x\"}" }
        waitForIdle()

        assertEquals("{\"#type\": \"x\"}", editorText())
    }
}
