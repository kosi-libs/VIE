package org.kodein.arch.vie.demo.business

import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.arch.vie.VIELoggingMiddleware
import org.kodein.arch.vie.VIEMiddleware
import org.kodein.arch.vie.demo.business.repo.InMemoryTaskRepository
import org.kodein.arch.vie.demo.vie.EditTaskVIE
import org.kodein.arch.vie.demo.vie.TaskListVIE
import org.kodein.arch.vie.timemachine.client.TimeMachine


@DIModule
interface AppModule {
    val taskRepo: TaskRepository

    // One member per screen, typed by its contract: `@DIFactory` matches each to the `middlewares`
    // parameter of its ViewModel by type.
    fun editTaskMiddlewares(): List<VIEMiddleware<EditTaskVIE.Model, EditTaskVIE.Intent, EditTaskVIE.Signal>>
    fun taskListMiddlewares(): List<VIEMiddleware<TaskListVIE.Model, TaskListVIE.Intent, TaskListVIE.Signal>>

    companion object
}

fun AppModule.Companion.business() = AppModule(
    taskRepo = Binding.single { InMemoryTaskRepository() },
    // `provider`, never `single`: a TimeMachine middleware serves one ViewModel, so each gets its own list.
    // The probe goes last. While it is not started, `TimeMachine.middleware()` does nothing.
    editTaskMiddlewares = Binding.provider { listOf(VIELoggingMiddleware("EditTask"), TimeMachine.middleware()) },
    taskListMiddlewares = Binding.provider { listOf(VIELoggingMiddleware("TaskList"), TimeMachine.middleware()) },
)
