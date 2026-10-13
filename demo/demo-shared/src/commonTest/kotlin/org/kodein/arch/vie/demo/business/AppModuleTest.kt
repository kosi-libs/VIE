package org.kodein.arch.vie.demo.business

import kotlin.test.Test
import kotlin.test.assertNotSame

/**
 * The module builds the middlewares where each ViewModel is built: a TimeMachine middleware serves exactly one
 * ViewModel, so lists bound as singletons would have the second ViewModel drive the first (ADR 0002).
 */
class AppModuleTest {

    @Test
    fun editTaskMiddlewares_areBuiltPerViewModel() {
        val module = AppModule.business()

        val first = module.editTaskMiddlewares()
        val second = module.editTaskMiddlewares()

        assertNotSame(first, second)
        // The logging middleware is a new instance each time, whether or not the probe runs.
        assertNotSame(first.first(), second.first())
    }

    @Test
    fun taskListMiddlewares_areBuiltPerViewModel() {
        val module = AppModule.business()

        val first = module.taskListMiddlewares()
        val second = module.taskListMiddlewares()

        assertNotSame(first, second)
        assertNotSame(first.first(), second.first())
    }
}
