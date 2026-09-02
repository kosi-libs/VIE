package org.kodein.mvi.nomi

import kotlinx.coroutines.flow.Flow


context(vm: MVIViewModel<Model, *, *>)
public suspend fun <Model : MVI.Model, T> Flow<T>.collectToModel(update: suspend Model.(T) -> Model) {
    collect {
        vm.update { update(it) }
    }
}

context(vm: MVIViewModel<*, *, Signal>)
public suspend fun <Signal, T> Flow<T>.collectToSinal(convert: suspend (T) -> Signal) where Signal : MVI.Signal, Signal : MVI.Signal.Type {
    collect {
        vm.signal(convert(it))
    }
}
