package org.kodein.mvi.nomi.demo.mvi

import kotlinx.serialization.Serializable
import org.kodein.mvi.nomi.MVI
import org.kodein.mvi.nomi.demo.data.Task
import org.kodein.mvi.nomi.demo.mvi.TaskListMVI.Intent
import org.kodein.mvi.nomi.demo.mvi.TaskListMVI.Model
import org.kodein.mvi.nomi.demo.mvi.TaskListMVI.Signal
import kotlin.uuid.Uuid


interface TaskListMVI : MVI<Model, Intent, Signal> {

    @Serializable
    data class Model(
        val tasks: List<Pair<Uuid, Task>>? = null,
        val work: List<Pair<Uuid, Work>> = emptyList(),
    ) : MVI.Model {
        enum class Work {
            Updating,
            Deleting
        }
    }

    @Serializable
    sealed interface Intent : MVI.Intent {
        @Serializable data class DeleteTask(val taskId: Uuid) : Intent
        @Serializable data class UpdateTaskCompletion(val taskId: Uuid, val completed: Boolean) : Intent
    }

    @Serializable
    sealed interface Signal : MVI.Signal {
        @Serializable
        data class Error(val message: String) : Signal, MVI.Signal.Event
    }
}
