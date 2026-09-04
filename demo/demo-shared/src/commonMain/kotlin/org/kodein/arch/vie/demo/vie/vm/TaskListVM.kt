package org.kodein.arch.vie.demo.vie.vm

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.kodein.arch.vie.VIELoggingMiddleware
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.demo.business.TaskRepository
import org.kodein.arch.vie.demo.vie.TaskListVIE
import org.kodein.arch.vie.demo.vie.TaskListVIE.Intent
import org.kodein.arch.vie.demo.vie.TaskListVIE.Model
import org.kodein.arch.vie.demo.vie.TaskListVIE.Signal

class TaskListVM(
    private val taskRepository: TaskRepository,
) : TaskListVIE, VIEViewModel<Model, Intent, Signal>(
    firstModel = Model(),
    middlewares = listOfNotNull(VIELoggingMiddleware("TaskList")),
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
