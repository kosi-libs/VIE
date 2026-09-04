package org.kodein.arch.vie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel


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
