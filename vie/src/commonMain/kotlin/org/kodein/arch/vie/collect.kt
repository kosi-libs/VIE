package org.kodein.arch.vie

import kotlinx.coroutines.flow.Flow


context(vm: VIEViewModel<Model, *, *>)
public suspend fun <Model : VIE.Model, T> Flow<T>.collectToModel(update: suspend Model.(T) -> Model) {
    collect {
        vm.update { update(it) }
    }
}

context(vm: VIEViewModel<*, *, Signal>)
public suspend fun <Signal, T> Flow<T>.collectToSignal(convert: suspend (T) -> Signal) where Signal : VIE.Signal, Signal : VIE.Signal.Type {
    collect {
        vm.signal(convert(it))
    }
}
