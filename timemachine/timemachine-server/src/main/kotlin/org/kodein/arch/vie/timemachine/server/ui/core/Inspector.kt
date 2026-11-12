package org.kodein.arch.vie.timemachine.server.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
 * whole, so no ViewModel and no selection outlives the store it reads. The ViewModels of a scope go
 * sooner, with the scope: see [releaseDroppedScopes].
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

/**
 * The pane ViewModels of one store instance, filed by what they show. Those of the window, which show
 * the store as a whole, are in [viewModelStore] for as long as the inspector lives. Those of a scope
 * are in a store of that scope's own, which [retain] clears once the store no longer holds the scope:
 * a `ViewModelStore` cannot remove one key, so what goes together is filed together.
 *
 * It is the main thread's, as the composition that fills it is.
 */
internal class InspectorViewModels : ViewModelStoreOwner {

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val scoped = HashMap<InspectorScope, ViewModelStoreOwner>()

    /** Where the ViewModels of the panes of [scope] live; the window's own when it is `null`. */
    fun owner(scope: InspectorScope?): ViewModelStoreOwner =
        if (scope == null) this else scoped.getOrPut(scope) { ScopeViewModels() }

    /** Clears the ViewModels of every scope that is not [held] any more. */
    fun retain(held: (InspectorScope) -> Boolean) {
        val dropped = scoped.keys.filterNot(held)
        dropped.forEach { scoped.remove(it)?.viewModelStore?.clear() }
    }

    fun clear() {
        viewModelStore.clear()
        scoped.values.forEach { it.viewModelStore.clear() }
        scoped.clear()
    }

    private class ScopeViewModels : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
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

/**
 * A pane: the [key] its ViewModel lives under, and how to build it.
 *
 * @property scope The scope whose records the pane shows, which its ViewModel is released with once the
 *   store drops it; `null` for a pane of the window, which shows the store as a whole.
 */
internal class Pane<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    val key: String,
    val scope: InspectorScope? = null,
    val newVM: () -> VIEViewModel<Model, Intent, Signal>,
) {
    /**
     * The key the pane's ViewModel is filed under, in the store [InspectorViewModels.owner] gives its [scope]. It mirrors `VIEView`,
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
    return ViewModelProvider.create(viewModels.owner(pane.scope), factory)[pane.storeKey, VIEViewModel::class] as VIEViewModel<Model, Intent, Signal>
}

/**
 * Releases, for as long as it is collected, the pane ViewModels of the scopes the store drops: a
 * cleared session, or the closed ViewModels cleared from a connected one. It runs on the main thread,
 * where the composition creates them.
 *
 * Each state is asked which scopes it still holds, rather than compared with the one before: a
 * ViewModel that a late composition creates for a scope already dropped goes with the next state.
 */
internal suspend fun Inspector.releaseDroppedScopes(): Nothing =
    store.state.collect { state -> viewModels.retain(state::holds) }

/**
 * A pane's view: [VIEView] under the pane's key, in the inspector's ViewModels of its scope, with the pane marked
 * shown for as long as it is composed. Every pane view goes through it, so none can forget either.
 *
 * One call site shows a pane per scope, one at a time, and the view state below it belongs to the pane
 * shown: when another takes its place, what [content] remembers starts anew, so no list, no effect and
 * no model crosses from one ViewModel to another, and what it saves (a list's or a column's scroll) is
 * kept and given back when its pane is shown there again. The saved states are the call site's own,
 * because two call sites can show the same pane at once, as the rail and the drawer do.
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
    rememberSaveableStateHolder().SaveableStateProvider(pane.key) {
        // The provider alone hands the nodes of the pane it replaces to the next one, which a lazy list
        // with a prefetch in flight does not survive: under a key of its own, the next pane's are new.
        key(pane.key) {
            // Asked for at each composition, never remembered: a store cleared under a pane that is still
            // composed is filed again, so the next state the store publishes releases it.
            CompositionLocalProvider(LocalViewModelStoreOwner provides inspector.viewModels.owner(pane.scope)) {
                VIEView(newVM = { pane.newVM() }, key = pane.key) { model -> content(model) }
            }
        }
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
