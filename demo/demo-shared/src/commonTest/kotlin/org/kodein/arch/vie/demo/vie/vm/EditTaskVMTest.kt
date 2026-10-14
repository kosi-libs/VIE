package org.kodein.arch.vie.demo.vie.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.kodein.arch.vie.demo.business.repo.InMemoryTaskRepository
import org.kodein.arch.vie.demo.data.Task
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Intent
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Model
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Signal
import org.kodein.arch.vie.test.runVMTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [EditTaskVM] driven directly, with no Inara container and a real [InMemoryTaskRepository] —
 * [kotlinx.coroutines.test.runTest] (via [org.kodein.arch.vie.test.runVMTest]) turns its simulated delays into virtual
 * time instead of testing that repository itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditTaskVMTest {

    private val task = Task(title = "Buy milk", description = "2%", completed = false)

    @Test
    fun newTask_isNotLoading() = runVMTest(
        newVM = { EditTaskVM(taskId = null, InMemoryTaskRepository()) },
    ) {
        assertFalse(model.loading)
        assertNull(model.task)
    }

    @Test
    fun existingTask_loadsIntoModel() {
        val repo = InMemoryTaskRepository()
        runVMTest(
            newVM = {
                val id = repo.add(task).getOrThrow()
                EditTaskVM(id, repo)
            },
        ) {
            advanceUntilIdle()

            assertEquals(listOf(Model(loading = true), Model(loading = false, task = task)), pendingModels())
        }
    }

    @Test
    fun unknownTask_signalsLoadFail() = runVMTest(
        newVM = { EditTaskVM(Uuid.parse("00000000-0000-0000-0000-000000000000"), InMemoryTaskRepository()) },
    ) {
        advanceUntilIdle()

        assertEquals(listOf(Signal.LoadFail), pendingSignals())
    }

    @Test
    fun edit_onNewTask_addsAndSignalsEdited() {
        val repo = InMemoryTaskRepository()
        runVMTest(
            newVM = { EditTaskVM(taskId = null, repo) },
        ) {
            post(Intent.Edit(task))
            runCurrent()
            assertTrue(model.loading)

            advanceUntilIdle()
            assertEquals(listOf(Signal.Edited), pendingSignals())
            assertEquals(listOf(task), repo.flow().first().map { it.second })
        }
    }

    @Test
    fun edit_withBlankTitle_signalsErrorAndStopsLoading() = runVMTest(
        newVM = { EditTaskVM(taskId = null, InMemoryTaskRepository()) },
    ) {
        post(Intent.Edit(task.copy(title = "")))
        advanceUntilIdle()

        assertFalse(model.loading)
        assertEquals(listOf(Signal.Error("Task name cannot be blank")), pendingSignals())
    }

    @Test
    fun edit_onExistingTask_updatesRepository() {
        val repo = InMemoryTaskRepository()
        runVMTest(
            newVM = {
                val id = repo.add(task).getOrThrow()
                EditTaskVM(id, repo)
            },
        ) {
            advanceUntilIdle() // let the existing task finish loading

            val updated = task.copy(title = "Buy oat milk")
            post(Intent.Edit(updated))
            advanceUntilIdle()

            assertEquals(listOf(Signal.Edited), pendingSignals())
            assertEquals(listOf(updated), repo.flow().first().map { it.second })
        }
    }
}
