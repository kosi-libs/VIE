package org.kodein.mvivm.demo.business.repo

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import org.kodein.mvivm.demo.data.Task
import org.kodein.mvivm.demo.business.TaskRepository
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


class InMemoryTaskRepository : TaskRepository {

    private val simulationDelay = 1.seconds

    private val tasks = MutableStateFlow(emptyList<Pair<Uuid, Task>>())

    private fun Task.validate() {
        require(title.isNotBlank()) { "Task name cannot be blank" }
    }

    override fun flow(): Flow<List<Pair<Uuid, Task>>> = flow {
        delay(simulationDelay)
        emitAll(tasks)
    }

    override suspend fun get(id: Uuid): Task? {
        delay(simulationDelay)
        return tasks.value.firstOrNull { (taskId) -> taskId == id }?.second
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun add(task: Task): Result<Uuid> =
        runCatching {
            task.validate()
            delay(simulationDelay)
            val id = Uuid.generateV7()
            tasks.update { it + (id to task) }
            id
        }

    override suspend fun delete(id: Uuid): Task? {
        delay(simulationDelay)
        val (_, removed) = tasks.value.firstOrNull { (taskId) -> taskId == id } ?: return null
        tasks.update { it.filterNot { (taskId) -> taskId == id } }
        return removed
    }

    override suspend fun update(id: Uuid, task: Task): Result<Unit> =
        runCatching {
            task.validate()
            delay(simulationDelay)
            if (tasks.value.none { (taskId) -> taskId == id }) error("Task not found")
            tasks.update { list ->
                list.map { (taskId, current) -> if (taskId == id) (id to task) else (taskId to current) }
            }
        }
}
