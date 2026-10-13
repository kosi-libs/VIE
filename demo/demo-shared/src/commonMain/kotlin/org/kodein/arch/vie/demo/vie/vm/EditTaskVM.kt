package org.kodein.arch.vie.demo.vie.vm

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.kodein.arch.vie.VIEMiddleware
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.demo.business.TaskRepository
import org.kodein.arch.vie.demo.vie.EditTaskVIE
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Intent
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Model
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Signal
import kotlin.uuid.Uuid


class EditTaskVM(
    val taskId: Uuid?,
    private val taskRepository: TaskRepository,
    // No default, on purpose: under `@DIFactory` a default outranks the module's member, and would silently leave
    // TimeMachine out.
    middlewares: List<VIEMiddleware<Model, Intent, Signal>>,
) : EditTaskVIE, VIEViewModel<Model, Intent, Signal>(
    firstModel = Model(
        loading = taskId != null,
    ),
    middlewares = middlewares,
) {
    init {
        if (taskId != null) {
            viewModelScope.launch {
                val task = taskRepository.get(taskId)
                if (task == null) signal(Signal.LoadFail)
                else {
                    update {
                        copy(
                            loading = false,
                            task = task
                        )
                    }
                }
            }
        }
    }

    override suspend fun process(intent: Intent) {
        when (intent) {
            is Intent.Edit -> {
                update { copy(loading = true) }
                val result =
                    if (taskId == null) taskRepository.add(intent.task)
                    else taskRepository.update(taskId, intent.task)
                if (result.isFailure) {
                    signal(Signal.Error(result.exceptionOrNull()?.message ?: "Unknown error"))
                    update { copy(loading = false) }
                } else {
                    signal(Signal.Edited)
                }
            }
        }
    }
}
