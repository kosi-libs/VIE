@file:OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.paneTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector


/**
 * The pinned current model's view over a live inspector of the main scenario, whose panes project on a
 * dispatcher the test runs by hand: what its button does as the store changes under it.
 */
class CurrentModelViewTest {

    private val scheduler = TestCoroutineScheduler()

    private fun currentModel(block: suspend DesktopComposeUiTest.(inspector: Inspector) -> Unit) =
        paneTest(compute = StandardTestDispatcher(scheduler), content = { CurrentModelView(Demo.session.id, Demo.taskList) }, block = block)

    /** Lets the panes project what the store holds now, and the view compose it. */
    private fun DesktopComposeUiTest.project() = repeat(3) {
        scheduler.runCurrent()
        waitForIdle()
    }

    @Test
    fun `Edit and replace opens the composer on a live ViewModel, and no longer once its session disconnected`() = currentModel { inspector ->
        until { scheduler.runCurrent(); shown("Edit & replace") }
        onNodeWithText("Edit & replace").performClick()
        until { inspector.selection.state.value.composerRequest != null }
        assertNotNull(inspector.selection.takeRequest(Demo.taskList))

        inspector.store.post(TimeMachineEvent.Disconnected(Demo.connection, Demo.at(29_000)))
        until { inspector.store.state.value.sessions[Demo.session.id]?.isConnected == false }
        project()

        onNodeWithText("Edit & replace").performClick()
        project()
        assertNull(inspector.selection.state.value.composerRequest, "the button still opened the composer after the session disconnected")
    }
}
