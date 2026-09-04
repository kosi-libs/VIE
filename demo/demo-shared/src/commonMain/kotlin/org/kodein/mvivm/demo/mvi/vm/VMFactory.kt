package org.kodein.mvivm.demo.mvi.vm

import org.kodein.di.inara.DIFactory
import org.kodein.mvivm.demo.business.AppModule
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
