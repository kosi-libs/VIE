package org.kodein.mvi.nomi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel


@Composable
public inline fun <reified Model : MVI.Model, Intent : MVI.Intent, Signal : MVI.Signal> MviView(
    crossinline newVM: () -> MVIViewModel<Model, Intent, Signal>,
    key: String? = null,
    content: @Composable MVIViewContext<Intent, Signal>.(Model) -> Unit,
) {
    val vm = viewModel(key = key) { newVM() }
    val model by vm.modelFlow.collectAsState()
    vm.content(model)
}
