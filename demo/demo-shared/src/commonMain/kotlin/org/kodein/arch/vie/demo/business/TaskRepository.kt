package org.kodein.arch.vie.demo.business

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.kodein.arch.vie.demo.data.Task
import kotlin.uuid.Uuid

interface TaskRepository {

    fun flow(): Flow<List<Pair<Uuid, Task>>>

    suspend fun get(id: Uuid): Task?

    suspend fun add(task: Task): Result<Uuid>

    suspend fun delete(id: Uuid): Task?

    suspend fun update(id: Uuid, task: Task): Result<Unit>

}
