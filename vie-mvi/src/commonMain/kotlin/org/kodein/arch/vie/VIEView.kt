package org.kodein.arch.vie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel


/**
 * The single entry point for rendering a [VIE] screen: creates (and remembers, keyed by [key])
 * the ViewModel with [newVM], collects its [VIEViewModel.modelFlow] as Compose state, and invokes
 * [content] with the current model and a [VIEViewScope] receiver — never the ViewModel itself.
 *
 * ```kotlin
 * @Composable
 * fun Counter(store: CounterStore) {
 *     VIEView(newVM = { CounterVM(store) }) { model ->        // `this` is a VIEViewScope
 *         Button(onClick = { post(CounterVIE.Intent.Increment) }) { Text("+") }
 *     }
 * }
 * ```
 *
 * @param Model The state the view renders.
 * @param Intent The events the view sends up.
 * @param Signal The one-offs the view reacts to but does not render.
 * @param newVM Creates the ViewModel instance for this composition.
 * @param key Forwarded to `viewModel { }`, to hold several instances of the same ViewModel in one
 *   composition.
 * @param content The view, invoked with the current model and a [VIEViewScope] receiver.
 */
@Composable
public inline fun <reified Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> VIEView(
    crossinline newVM: () -> VIEViewModel<Model, Intent, Signal>,
    key: String? = null,
    content: @Composable VIEViewScope<Intent, Signal>.(Model) -> Unit,
) {
    val vm = viewModel(key = key) { newVM() }
    val model by vm.modelFlow.collectAsState()
    vm.content(model)
}
