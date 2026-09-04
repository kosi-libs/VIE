package org.kodein.arch.vie.demo.vie

import kotlinx.serialization.Serializable
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.demo.data.Task
import org.kodein.arch.vie.demo.vie.TaskListVIE.Intent
import org.kodein.arch.vie.demo.vie.TaskListVIE.Model
import org.kodein.arch.vie.demo.vie.TaskListVIE.Signal
import kotlin.uuid.Uuid


interface TaskListVIE : VIE<Model, Intent, Signal> {

    @Serializable
    data class Model(
        val tasks: List<Pair<Uuid, Task>>? = null,
        val work: List<Pair<Uuid, Work>> = emptyList(),
    ) : VIE.Model {
        enum class Work {
            Updating,
            Deleting
        }
    }

    @Serializable
    sealed interface Intent : VIE.Intent {
        @Serializable data class DeleteTask(val taskId: Uuid) : Intent
        @Serializable data class UpdateTaskCompletion(val taskId: Uuid, val completed: Boolean) : Intent
    }

    @Serializable
    sealed interface Signal : VIE.Signal {
        @Serializable
        data class Error(val message: String) : Signal, VIE.Signal.Event
    }
}
