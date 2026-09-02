package org.kodein.mvi.nomi.demo.mvi

import kotlinx.serialization.Serializable
import org.kodein.mvi.nomi.MVI
import org.kodein.mvi.nomi.demo.data.Task
import org.kodein.mvi.nomi.demo.mvi.EditTaskMVI.Intent
import org.kodein.mvi.nomi.demo.mvi.EditTaskMVI.Model
import org.kodein.mvi.nomi.demo.mvi.EditTaskMVI.Signal


interface EditTaskMVI : MVI<Model, Intent, Signal> {

    @Serializable
    data class Model(
        val loading: Boolean,
        val task: Task? = null,
    ) : MVI.Model

    @Serializable
    sealed interface Intent : MVI.Intent {
        @Serializable data class Edit(val task: Task) : Intent
    }

    @Serializable sealed interface Signal : MVI.Signal {
        @Serializable data object Edited : Signal, MVI.Signal.Event
        @Serializable data object LoadFail : Signal, MVI.Signal.Event
        @Serializable data class Error(val message: String) : Signal, MVI.Signal.Event
    }

}
