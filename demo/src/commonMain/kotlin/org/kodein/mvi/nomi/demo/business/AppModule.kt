package org.kodein.mvi.nomi.demo.business

import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.mvi.nomi.demo.business.repo.InMemoryTaskRepository


@DIModule
interface AppModule {
    val taskRepo: TaskRepository

    companion object
}

fun AppModule.Companion.business() = AppModule(
    taskRepo = Binding.single { InMemoryTaskRepository() }
)
