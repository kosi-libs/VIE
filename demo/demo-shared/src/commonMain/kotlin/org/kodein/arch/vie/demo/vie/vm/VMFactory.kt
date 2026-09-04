package org.kodein.arch.vie.demo.vie.vm

import org.kodein.di.inara.DIFactory
import org.kodein.arch.vie.demo.business.AppModule
import kotlin.uuid.Uuid


@DIFactory(
    builds = TaskListVM::class,
    from = AppModule::class,
)
@DIFactory(
    builds = EditTaskVM::class,
    from = AppModule::class,
    withNullable = Uuid::class,
)
internal object VMFactory
