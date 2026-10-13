package org.kodein.arch.vie.demo.vie.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.kodein.arch.vie.demo.business.TaskRepository
import org.kodein.arch.vie.demo.business.repo.InMemoryTaskRepository
import org.kodein.arch.vie.demo.data.Task
import org.kodein.arch.vie.demo.vie.TaskListVIE.Intent
import org.kodein.arch.vie.demo.vie.TaskListVIE.Model.Work
import org.kodein.arch.vie.demo.vie.TaskListVIE.Signal
import org.kodein.arch.vie.test.runVMTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * [TaskListVM] driven directly, with no Inara container and a real [InMemoryTaskRepository] —
 * [kotlinx.coroutines.test.runTest] (via [org.kodein.arch.vie.test.runVMTest]) turns its simulated delays into virtual
 * time instead of testing that repository itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListVMTest {

    private val task = Task(title = "Buy milk", description = "2%", completed = false)

    @Test
    fun tasksAreNullUntilRepositoryEmits() = runVMTest(
        newVM = { TaskListVM(InMemoryTaskRepository(), middlewares = emptyList()) },
    ) { vm, _ ->
        assertNull(vm.modelFlow.value.tasks)

        runCurrent() // the repository's simulated delay hasn't elapsed yet
        assertNull(vm.modelFlow.value.tasks)

        advanceUntilIdle()
        assertEquals(emptyList(), vm.modelFlow.value.tasks)
    }

    @Test
    fun deleteTask_marksWorkThenRemovesTask() {
        val repo = InMemoryTaskRepository()
        runVMTest(
            newVM = { TaskListVM(repo, middlewares = emptyList()) },
        ) { vm, _ ->
            val id = repo.add(task).getOrThrow()
            advanceUntilIdle()
            assertEquals(listOf(id to task), vm.modelFlow.value.tasks)

            vm.post(Intent.DeleteTask(id))
            runCurrent()
            assertEquals(listOf(id to Work.Deleting), vm.modelFlow.value.work)

            advanceUntilIdle()
            assertEquals(emptyList(), vm.modelFlow.value.work)
            assertEquals(emptyList(), vm.modelFlow.value.tasks)
        }
    }

    @Test
    fun updateTaskCompletion_marksWorkThenUpdatesTask() {
        val repo = InMemoryTaskRepository()
        runVMTest(
            newVM = { TaskListVM(repo, middlewares = emptyList()) },
        ) { vm, _ ->
            val id = repo.add(task).getOrThrow()
            advanceUntilIdle()

            vm.post(Intent.UpdateTaskCompletion(id, completed = true))
            runCurrent()
            assertEquals(listOf(id to Work.Updating), vm.modelFlow.value.work)

            advanceUntilIdle()
            assertEquals(emptyList(), vm.modelFlow.value.work)
            assertEquals(listOf(id to task.copy(completed = true)), vm.modelFlow.value.tasks)
        }
    }

    @Test
    fun updateTaskCompletion_onUnknownTask_isIgnored() = runVMTest(
        newVM = { TaskListVM(InMemoryTaskRepository(), middlewares = emptyList()) },
    ) { vm, signals ->
        advanceUntilIdle()

        vm.post(Intent.UpdateTaskCompletion(Uuid.parse("00000000-0000-0000-0000-000000000000"), completed = true))
        advanceUntilIdle()

        assertEquals(emptyList(), vm.modelFlow.value.work)
        assertEquals(emptyList(), signals)
    }

    @Test
    fun updateTaskCompletion_whenRepositoryFails_signalsError() {
        val realRepo = InMemoryTaskRepository()
        val repo = FailingUpdateRepository(realRepo)
        runVMTest(
            newVM = { TaskListVM(repo, middlewares = emptyList()) },
        ) { vm, signals ->
            val id = realRepo.add(task).getOrThrow()
            advanceUntilIdle()

            vm.post(Intent.UpdateTaskCompletion(id, completed = true))
            advanceUntilIdle()

            assertEquals(emptyList(), vm.modelFlow.value.work)
            assertEquals(listOf(id to task), vm.modelFlow.value.tasks) // untouched: the update failed
            assertEquals(listOf(Signal.Error("offline")), signals)
        }
    }
}

/** A [TaskRepository] whose [update] always fails, to exercise [TaskListVM]'s error path. */
private class FailingUpdateRepository(delegate: TaskRepository) : TaskRepository by delegate {
    override suspend fun update(id: Uuid, task: Task): Result<Unit> = Result.failure(IllegalStateException("offline"))
}
