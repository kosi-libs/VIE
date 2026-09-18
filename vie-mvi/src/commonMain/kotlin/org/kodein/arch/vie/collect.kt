package org.kodein.arch.vie

import kotlinx.coroutines.flow.Flow


/**
 * Collects this flow straight into the enclosing [VIEViewModel]'s model, until the flow
 * completes. Called from inside a `VIEViewModel` — typically an `init` block or a
 * `viewModelScope` coroutine — where the `vm` context is implicitly available.
 *
 * ```kotlin
 * init {
 *     viewModelScope.launch {
 *         taskRepository.flow().collectToModel { tasks -> copy(tasks = tasks) }
 *     }
 * }
 * ```
 *
 * @param Model The enclosing ViewModel's model type.
 * @param T The type of value this flow emits.
 * @receiver The external flow to collect.
 * @param update Computes the next model from the current one and a collected value.
 */
context(vm: VIEViewModel<Model, *, *>)
public suspend fun <Model : VIE.Model, T> Flow<T>.collectToModel(update: suspend Model.(T) -> Model) {
    collect {
        vm.update { update(it) }
    }
}

/**
 * Collects this flow straight into the enclosing [VIEViewModel]'s signals, until the flow
 * completes. Called from inside a `VIEViewModel` — typically an `init` block or a
 * `viewModelScope` coroutine — where the `vm` context is implicitly available.
 *
 * @param Signal The enclosing ViewModel's signal type, constrained to have picked a delivery kind.
 * @param T The type of value this flow emits.
 * @receiver The external flow to collect.
 * @param convert Converts a collected value into the signal to emit.
 */
context(vm: VIEViewModel<*, *, Signal>)
public suspend fun <Signal, T> Flow<T>.collectToSignal(convert: suspend (T) -> Signal) where Signal : VIE.Signal, Signal : VIE.Signal.Type {
    collect {
        vm.signal(convert(it))
    }
}
