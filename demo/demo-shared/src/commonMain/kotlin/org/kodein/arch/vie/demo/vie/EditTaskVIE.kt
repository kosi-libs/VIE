package org.kodein.arch.vie.demo.vie

import kotlinx.serialization.Serializable
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.demo.data.Task
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Intent
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Model
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Signal


interface EditTaskVIE : VIE<Model, Intent, Signal> {

    @Serializable
    data class Model(
        val loading: Boolean,
        val task: Task? = null,
    ) : VIE.Model

    @Serializable
    sealed interface Intent : VIE.Intent {
        @Serializable data class Edit(val task: Task) : Intent
    }

    @Serializable sealed interface Signal : VIE.Signal {
        @Serializable data object Edited : Signal, VIE.Signal.Event
        @Serializable data object LoadFail : Signal, VIE.Signal.Event
        @Serializable data class Error(val message: String) : Signal, VIE.Signal.Event
    }

}
