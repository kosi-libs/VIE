package org.kodein.mvi.nomi.demo.mvi.vm

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.kodein.mvi.nomi.MVIViewModel
import org.kodein.mvi.nomi.demo.mvi.TaskListMVI
import org.kodein.mvi.nomi.demo.mvi.TaskListMVI.Intent
import org.kodein.mvi.nomi.demo.mvi.TaskListMVI.Model
import org.kodein.mvi.nomi.demo.mvi.TaskListMVI.Signal
import org.kodein.mvi.nomi.demo.business.TaskRepository

class TaskListVM(
    private val taskRepository: TaskRepository,
) : TaskListMVI, MVIViewModel<Model, Intent, Signal>(
    firstModel = Model()
) {
    init {
        viewModelScope.launch {
            taskRepository.flow().collect { tasks ->
                update { copy(tasks = tasks) }
            }
        }
    }

    override suspend fun process(intent: Intent) {
        when (intent) {
            is Intent.DeleteTask -> {
                val workPair = Pair(intent.taskId, Model.Work.Deleting)
                update { copy(work = work + workPair) }
                viewModelScope.launch {
                    val deleted = taskRepository.delete(intent.taskId)
                    update { copy(work = work - workPair) }
                }
            }
            is Intent.UpdateTaskCompletion -> {
                val task = model.tasks?.find { (id) -> id == intent.taskId }?.second ?: return
                val workPair = Pair(intent.taskId, Model.Work.Updating)
                update { copy(work = work + workPair) }
                viewModelScope.launch {
                    val updated = task.copy(completed = intent.completed)
                    val result = taskRepository.update(intent.taskId, updated)
                    if (result.isSuccess) {
                        update { copy(tasks = tasks?.map { if (it.first == intent.taskId) it.copy(second = updated) else it }) }
                    } else {
                        signal(Signal.Error(result.exceptionOrNull()?.message ?: "Unknown error"))
                    }
                    update { copy(work = work - workPair) }
                }
            }
        }
    }
}
