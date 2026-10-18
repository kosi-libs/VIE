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
 * @param Signal The enclosing ViewModel's signal type.
 * @param S The converted signal's type: a [Signal] that has picked a delivery kind.
 * @param T The type of value this flow emits.
 * @receiver The external flow to collect.
 * @param convert Converts a collected value into the signal to emit.
 */
// https://youtrack.jetbrains.com/projects/KT/issues/KT-19215
@Suppress("BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER")
context(vm: VIEViewModel<*, *, Signal>)
public suspend fun <Signal : VIE.Signal, S, T> Flow<T>.collectToSignal(convert: suspend (T) -> S) where S : Signal, S : VIE.Signal.Type {
    collect {
        vm.signal(convert(it))
    }
}
