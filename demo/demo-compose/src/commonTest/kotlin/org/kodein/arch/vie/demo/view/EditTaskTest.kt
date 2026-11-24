package org.kodein.arch.vie.demo.view

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import kotlinx.coroutines.test.TestResult
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.test.pendingIntents
import org.kodein.arch.vie.demo.data.Task
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Intent
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Model
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Signal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [EditTask] driven with a [MockVIE] — no [EditTaskVM][org.kodein.arch.vie.demo.vie.vm.EditTaskVM],
 * no repository, just a model to render and a mock to record what the view posts.
 */
@OptIn(ExperimentalTestApi::class)
class EditTaskTest {

    private val task = Task(title = "Buy milk", description = "2%", completed = false)

    @Test
    fun loading_showsOverlay(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(loading = true))
        setContent { vie.EditTask(model = vie.model, goBack = {}, new = false) }

        onNodeWithTag("loading").assertIsDisplayed()
    }

    @Test
    fun task_prefillsFields(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(loading = false, task = task))
        setContent { vie.EditTask(model = vie.model, goBack = {}, new = false) }
        waitForIdle() // let the LaunchedEffect(model.task) fill the text fields

        onNodeWithTag("title").assertTextContains(task.title)
        onNodeWithTag("description").assertTextContains(task.description)
    }

    @Test
    fun new_showsCreateLabel(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(loading = false))
        setContent { vie.EditTask(model = vie.model, goBack = {}, new = true) }

        onNodeWithText("Create").assertIsDisplayed()
    }

    @Test
    fun existing_showsUpdateLabel(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(loading = false, task = task))
        setContent { vie.EditTask(model = vie.model, goBack = {}, new = false) }

        onNodeWithText("Update").assertIsDisplayed()
    }

    @Test
    fun typeThenSubmit_postsEditIntent(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(loading = false))
        setContent { vie.EditTask(model = vie.model, goBack = {}, new = true) }

        onNodeWithTag("title").performTextInput("Buy eggs")
        onNodeWithTag("description").performTextInput("A dozen")
        onNodeWithText("Create").performClick()

        assertEquals(
            listOf(Intent.Edit(Task(title = "Buy eggs", description = "A dozen", completed = false))),
            vie.pendingIntents(),
        )
    }

    @Test
    fun editedSignal_callsGoBack(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(loading = false, task = task))
        var wentBack = false
        setContent { vie.EditTask(model = vie.model, goBack = { wentBack = true }, new = false) }
        waitForIdle() // let SignalEffect start collecting before we emit

        vie.signal(Signal.Edited)

        waitUntil { wentBack }
    }

    @Test
    fun loadFailSignal_callsGoBack(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(loading = true))
        var wentBack = false
        setContent { vie.EditTask(model = vie.model, goBack = { wentBack = true }, new = false) }
        waitForIdle() // let SignalEffect start collecting before we emit

        vie.signal(Signal.LoadFail)

        waitUntil { wentBack }
    }

    @Test
    fun errorSignal_showsSnackbar(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(loading = false, task = task))
        setContent { vie.EditTask(model = vie.model, goBack = {}, new = false) }
        waitForIdle() // let SignalEffect start collecting before we emit

        vie.signal(Signal.Error("Could not save task"))

        waitUntilAtLeastOneExists(hasText("Could not save task"))
    }
}
