package org.kodein.arch.vie.demo.view

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import kotlinx.coroutines.test.TestResult
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.test.pendingIntents
import org.kodein.arch.vie.demo.data.Task
import org.kodein.arch.vie.demo.vie.TaskListVIE.Intent
import org.kodein.arch.vie.demo.vie.TaskListVIE.Model
import org.kodein.arch.vie.demo.vie.TaskListVIE.Model.Work
import org.kodein.arch.vie.demo.vie.TaskListVIE.Signal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [TaskList] driven with a [MockVIE] — no [TaskListVM][org.kodein.arch.vie.demo.vie.vm.TaskListVM],
 * no repository, just a model to render and a mock to record what the view posts.
 */
@OptIn(ExperimentalTestApi::class)
class TaskListTest {

    private val id1 = Uuid.parse("ed7c8378-9cbd-4c20-9d64-425fe531d438")
    private val id2 = Uuid.parse("1ee53463-078c-4bd1-b9dc-db2066b938b3")
    private val task1 = Task(title = "Buy milk", description = "2%", completed = false)
    private val task2 = Task(title = "Walk the dog", description = "", completed = true)

    @Test
    fun nullTasks_showsLoading(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(tasks = null))
        setContent { vie.TaskList(model = vie.model, goToNewTask = {}, goToTask = {}) }

        onNodeWithTag("loading").assertIsDisplayed()
    }

    @Test
    fun emptyTasks_showsNoTasks(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(tasks = emptyList()))
        setContent { vie.TaskList(model = vie.model, goToNewTask = {}, goToTask = {}) }

        onNodeWithText("No tasks").assertIsDisplayed()
    }

    @Test
    fun tasks_areListedWithCheckedState(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(tasks = listOf(id1 to task1, id2 to task2)))
        setContent { vie.TaskList(model = vie.model, goToNewTask = {}, goToTask = {}) }

        onNodeWithText(task1.title).assertIsDisplayed()
        onNodeWithText(task2.title).assertIsDisplayed()
        onNodeWithTag("checkbox-$id1").assertIsOff()
        onNodeWithTag("checkbox-$id2").assertIsOn()
    }

    @Test
    fun checkboxClick_postsUpdateTaskCompletion(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(tasks = listOf(id1 to task1)))
        setContent { vie.TaskList(model = vie.model, goToNewTask = {}, goToTask = {}) }

        onNodeWithTag("checkbox-$id1").performClick()

        assertEquals(listOf(Intent.UpdateTaskCompletion(id1, completed = true)), vie.pendingIntents())
    }

    @Test
    fun deleteClick_postsDeleteTask(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(tasks = listOf(id1 to task1, id2 to task2)))
        setContent { vie.TaskList(model = vie.model, goToNewTask = {}, goToTask = {}) }

        onNodeWithTag("delete-$id2").performClick()

        assertEquals(listOf(Intent.DeleteTask(id2)), vie.pendingIntents())
    }

    @Test
    fun workInProgress_disablesRowControls(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(
            Model(
                tasks = listOf(id1 to task1, id2 to task2),
                work = listOf(id1 to Work.Updating, id2 to Work.Deleting),
            )
        )
        setContent { vie.TaskList(model = vie.model, goToNewTask = {}, goToTask = {}) }

        onNodeWithTag("checkbox-$id1").assertIsNotEnabled()
        onNodeWithTag("delete-$id2").assertIsNotEnabled()
        onNodeWithTag("delete-$id1").assertIsEnabled()
        onNodeWithTag("checkbox-$id2").assertIsEnabled()
    }

    @Test
    fun fabClick_callsGoToNewTask(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(tasks = emptyList()))
        var called = false
        setContent { vie.TaskList(model = vie.model, goToNewTask = { called = true }, goToTask = {}) }

        onNodeWithContentDescription("Add a task").performClick()

        assertTrue(called)
    }

    @Test
    fun rowClick_callsGoToTaskWithId(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(tasks = listOf(id1 to task1)))
        var clickedId: Uuid? = null
        setContent { vie.TaskList(model = vie.model, goToNewTask = {}, goToTask = { clickedId = it }) }

        onNodeWithTag("row-$id1").performClick()

        assertEquals(id1, clickedId)
    }

    @Test
    fun errorSignal_showsSnackbar(): TestResult = runComposeUiTest {
        val vie = MockVIE<Model, Intent, Signal>(Model(tasks = emptyList()))
        setContent { vie.TaskList(model = vie.model, goToNewTask = {}, goToTask = {}) }
        waitForIdle() // let SignalEffect start collecting before we emit

        vie.signal(Signal.Error("Could not delete task"))

        waitUntilAtLeastOneExists(hasText("Could not delete task"))
    }
}
