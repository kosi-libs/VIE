package org.kodein.arch.vie.demo.vie.vm

import org.kodein.arch.vie.demo.business.AppModule
import kotlin.uuid.Uuid


/**
 * The public way to build the demo's ViewModels from another module: [VMFactory] and the functions
 * generated on it are internal to this one.
 */
object DemoViewModels {

    /** Builds the ViewModel of the task list. */
    fun taskList(appModule: AppModule): TaskListVM = VMFactory.TaskListVM(appModule)

    /** Builds the ViewModel that edits the task [taskId], or creates a new task when it is null. */
    fun editTask(appModule: AppModule, taskId: Uuid?): EditTaskVM = VMFactory.EditTaskVM(appModule, taskId)
}
