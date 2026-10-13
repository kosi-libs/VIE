package org.kodein.arch.vie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel


/**
 * The single entry point for rendering a [VIE] screen: creates (and remembers, identified by its
 * class and [key]) the ViewModel with [newVM], collects its [VIEViewModel.modelFlow] as Compose state, and invokes
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
 * @param VM The ViewModel's concrete class, inferred from what [newVM] returns. It is part of the
 *   ViewModel's identity, so views of different classes never share one, whatever their [key].
 * @param newVM Creates the ViewModel instance for this composition. Type the lambda with the
 *   concrete class: a lambda typed `VIEViewModel<…>` gets no class discrimination.
 * @param key Tells apart several instances of one ViewModel class under one owner. Without it,
 *   views of one class share one ViewModel.
 * @param content The view, invoked with the current model and a [VIEViewScope] receiver.
 */
@Composable
public inline fun <reified Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal,
    reified VM : VIEViewModel<Model, Intent, Signal>> VIEView(
    crossinline newVM: () -> VM,
    key: String? = null,
    content: @Composable VIEViewScope<Intent, Signal>.(Model) -> Unit,
) {
    val vm = viewModel(key = viewModelKey(VM::class.simpleName, key)) { newVM() }
    val model by vm.modelFlow.collectAsState()
    vm.content(model)
}

/**
 * The key a ViewModel is filed under in its owner: `<simple name>:<key>`, the [key] alone when the
 * class has no simple name, and `null` without a [key], so that androidx derives its per-class default.
 */
@PublishedApi
internal fun viewModelKey(simpleName: String?, key: String?): String? =
    if (key == null) null else if (simpleName == null) key else "$simpleName:$key"
