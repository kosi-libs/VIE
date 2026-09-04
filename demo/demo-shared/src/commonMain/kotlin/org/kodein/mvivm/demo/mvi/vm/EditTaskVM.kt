package org.kodein.mvivm.demo.mvi.vm

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.kodein.mvivm.MVIViewModel
import org.kodein.mvivm.demo.business.TaskRepository
import org.kodein.mvivm.demo.mvi.EditTaskMVI
import org.kodein.mvivm.demo.mvi.EditTaskMVI.Intent
import org.kodein.mvivm.demo.mvi.EditTaskMVI.Model
import org.kodein.mvivm.demo.mvi.EditTaskMVI.Signal
import kotlin.uuid.Uuid


class EditTaskVM(
    val taskId: Uuid?,
    private val taskRepository: TaskRepository,
) : EditTaskMVI, MVIViewModel<Model, Intent, Signal>(
    firstModel = Model(
        loading = taskId != null,
    ),
    middlewares = listOfNotNull(),
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
