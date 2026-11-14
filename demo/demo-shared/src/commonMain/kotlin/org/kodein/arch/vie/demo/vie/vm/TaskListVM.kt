package org.kodein.arch.vie.demo.vie.vm

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.kodein.arch.vie.VIEMiddleware
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.demo.business.TaskRepository
import org.kodein.arch.vie.demo.vie.TaskListVIE
import org.kodein.arch.vie.demo.vie.TaskListVIE.Intent
import org.kodein.arch.vie.demo.vie.TaskListVIE.Model
import org.kodein.arch.vie.demo.vie.TaskListVIE.Signal

class TaskListVM(
    private val taskRepository: TaskRepository,
    // No default, on purpose: under `@DIFactory` a default outranks the module's member, and would silently leave
    // TimeMachine out.
    middlewares: List<VIEMiddleware<Model, Intent, Signal>>,
) : TaskListVIE, VIEViewModel<Model, Intent, Signal>(
    firstModel = Model(),
    middlewares = middlewares,
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
