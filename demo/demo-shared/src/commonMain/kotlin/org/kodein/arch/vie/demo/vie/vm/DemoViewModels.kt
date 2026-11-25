package org.kodein.arch.vie.demo.vie.vm

import org.kodein.arch.vie.demo.business.AppModule
import org.kodein.arch.vie.demo.business.business
import kotlin.uuid.Uuid


/**
 * The public way to build the demo's ViewModels from another module: [VMFactory] and the functions
 * generated on it are internal to this one.
 */
object DemoViewModels {

    /**
     * The business singletons the ViewModels share, created on first use. An app that does not hold
     * its own [AppModule], such as the SwiftUI demo, builds its ViewModels on this one.
     */
    val appModule: AppModule by lazy { AppModule.business() }

    /** Builds the ViewModel of the task list. */
    fun taskList(appModule: AppModule): TaskListVM = VMFactory.TaskListVM(appModule)

    /** Builds the ViewModel that edits the task [taskId], or creates a new task when it is null. */
    fun editTask(appModule: AppModule, taskId: Uuid?): EditTaskVM = VMFactory.EditTaskVM(appModule, taskId)

    /** Builds the ViewModel of the task list, on the shared [appModule]. */
    fun taskList(): TaskListVM = taskList(appModule)

    /** Builds the ViewModel that edits the task [taskId], or creates a new task when it is null, on the shared [appModule]. */
    fun editTask(taskId: Uuid?): EditTaskVM = editTask(appModule, taskId)
}
