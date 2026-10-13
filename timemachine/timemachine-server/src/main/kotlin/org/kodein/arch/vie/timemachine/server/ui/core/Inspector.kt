package org.kodein.arch.vie.timemachine.server.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEView
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.VIEViewScope
import org.kodein.arch.vie.timemachine.server.store.TimeMachineStore
import kotlin.time.Clock


/**
 * What lives and dies together with one [store]: its [selection] holder, the panes' [visibility],
 * the [viewModels] they live in, the dispatcher their projections run on, the clock and the
 * listener's retry. It is provided at the window root through [LocalInspector], and closed as a
 * whole, so no ViewModel and no selection outlives the store it reads.
 *
 * @property compute `Dispatchers.Default` live, the test dispatcher in tests.
 * @property clock `Clock.System` live, a fixed or virtual-time clock in tests.
 * @property retry Listens again after a failure.
 */
internal class Inspector(
    val store: TimeMachineStore,
    val selection: InspectorSelection,
    val visibility: PaneVisibility,
    val viewModels: InspectorViewModels,
    val compute: CoroutineDispatcher,
    val clock: Clock,
    val retry: () -> Unit,
) {

    /** Clears every pane ViewModel, which cancels the collectors of the store they read. */
    fun close() {
        viewModels.clear()
    }
}

internal val LocalInspector: ProvidableCompositionLocal<Inspector> =
    staticCompositionLocalOf { error("No Inspector provided") }

/** The [ViewModelStoreOwner] of the pane ViewModels of one store instance. */
internal class InspectorViewModels : ViewModelStoreOwner {

    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun clear() {
        viewModelStore.clear()
    }
}

/**
 * The keys of the panes that are composed, counted: the compact drawer and the rail can compose the
 * same pane, so a key shown twice and hidden once is still shown.
 */
internal class PaneVisibility {

    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())

    fun show(key: String) {
        counts.update { it + (key to (it[key] ?: 0) + 1) }
    }

    fun hide(key: String) {
        counts.update { val left = (it[key] ?: 0) - 1; if (left > 0) it + (key to left) else it - key }
    }

    fun shown(key: String): Flow<Boolean> = counts.map { (it[key] ?: 0) > 0 }.distinctUntilChanged()
}

/** A pane: the [key] its ViewModel lives under, and how to build it. */
internal class Pane<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    val key: String,
    val newVM: () -> VIEViewModel<Model, Intent, Signal>,
) {
    /**
     * The key the pane's ViewModel is filed under in [Inspector.viewModels]. It mirrors `VIEView`,
     * which files a ViewModel under `<simple name of its class>:<key>`, and a pane types `newVM` as
     * `VIEViewModel<…>`. The [key] itself is also the pane's `PaneVisibility` id.
     */
    val storeKey: String get() = "${VIEViewModel::class.simpleName}:$key"
}

/**
 * The ViewModel of [pane] in this inspector's [Inspector.viewModels]: created if it is not there
 * yet, exactly as `VIEView` does, so a test can script it before anything is composed.
 */
internal fun <Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> Inspector.viewModel(
    pane: Pane<Model, Intent, Signal>,
): VIEViewModel<Model, Intent, Signal> {
    val factory = viewModelFactory { initializer<VIEViewModel<Model, Intent, Signal>> { pane.newVM() } }
    @Suppress("UNCHECKED_CAST")
    return ViewModelProvider.create(viewModels, factory)[pane.storeKey, VIEViewModel::class] as VIEViewModel<Model, Intent, Signal>
}

/**
 * A pane's view: [VIEView] under the pane's key, in the inspector's ViewModels, with the pane marked
 * shown for as long as it is composed. Every pane view goes through it, so none can forget either.
 */
@Composable
internal inline fun <reified Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> InspectorPane(
    pane: Pane<Model, Intent, Signal>,
    crossinline content: @Composable VIEViewScope<Intent, Signal>.(Model) -> Unit,
) {
    val inspector = LocalInspector.current
    DisposableEffect(inspector, pane.key) {
        inspector.visibility.show(pane.key)
        onDispose { inspector.visibility.hide(pane.key) }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides inspector.viewModels) {
        VIEView(newVM = pane.newVM, key = pane.key) { model -> content(model) }
    }
}

/**
 * [inputs] projected while [key] is shown, on [Inspector.compute]. [projector] is called each time
 * the pane is shown, and gives the function that projects one input: whatever caches it closes over
 * live in that branch only, are touched by that one flow, need no lock, and are freed when the pane
 * is hidden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <In, Out> Inspector.whileShown(key: String, inputs: Flow<In>, projector: () -> (In) -> Out): Flow<Out> =
    visibility.shown(key).flatMapLatest { shown ->
        if (!shown) emptyFlow() else {
            val project = projector()
            inputs.conflate().map(project)
        }
    }.flowOn(compute)
