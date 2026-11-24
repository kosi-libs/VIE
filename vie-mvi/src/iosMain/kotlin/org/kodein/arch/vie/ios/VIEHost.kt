package org.kodein.arch.vie.ios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.launch
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.VIEViewScope
import kotlin.reflect.KClass


/**
 * Hosts a [VIEViewModel] for a Swift view, which can neither collect a `Flow` nor clear a
 * ViewModel by itself. It owns the ViewModel through a [ViewModelStore] of its own: the ViewModel
 * is created on first use, and lives until [clear].
 *
 * This is the Kotlin half of VIE's SwiftUI support: the Swift helpers wrap it in an observable
 * object and a view container. It is a class, not an interface, so that Objective-C export keeps
 * its type parameters, and Swift sees a typed [model].
 *
 * Every member is meant to be called from the main thread, and every callback is called on it.
 *
 * @param Model The state the view renders.
 * @param Intent The events the view sends up.
 * @param Signal The one-offs the view reacts to but does not render.
 * @param newVM Creates the ViewModel, on first use of this host.
 */
public class VIEHost<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    newVM: () -> VIEViewModel<Model, Intent, Signal>,
) {
    private val store = ViewModelStore()

    private var cleared = false

    private val created: VIEViewModel<Model, Intent, Signal> by lazy {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return newVM() as T
            }
        }
        // The store holds this one ViewModel, so the abstract class is enough to file it under.
        @Suppress("UNCHECKED_CAST")
        ViewModelProvider.create(store, factory)[VIEViewModel::class] as VIEViewModel<Model, Intent, Signal>
    }

    // Every member but `clear` goes through here, so a cleared host hands out nothing, used or not,
    // and creates no ViewModel after the clear, which nobody would clear.
    private val vm: VIEViewModel<Model, Intent, Signal>
        get() {
            check(!cleared) { "This VIEHost has been cleared" }
            return created
        }

    /**
     * The current model: always complete, so a view's first render needs no placeholder.
     *
     * @throws IllegalStateException If this host has been cleared.
     */
    public val model: Model get() = vm.modelFlow.value

    /**
     * What a view sees of the ViewModel: it posts intents to it, and [collectSignals] reads its signals.
     * Typed as a [VIEViewScope] only (the object is the ViewModel itself, but its static type
     * exposes no more), so that a view renders just as well against `EmptyVIE`.
     *
     * @throws IllegalStateException If this host has been cleared.
     */
    public val scope: VIEViewScope<Intent, Signal> get() = vm

    /**
     * Calls [onModel] with the current model, then with every new one, until the returned
     * [VIECancellable] is cancelled or this host is cleared.
     *
     * Called from the main thread, as it should be, the first call to [onModel] usually happens
     * before this function returns: the returned [VIECancellable] is not available to it yet.
     * It comes after the return when this is called from within another callback that runs the
     * same way. Rely on neither order.
     *
     * @param onModel Called with each model.
     * @throws IllegalStateException If this host has been cleared.
     */
    public fun observeModels(onModel: (Model) -> Unit): VIECancellable {
        // Read once, so that the guard can only fail here, never inside the coroutine.
        val vm = vm
        return VIECancellable(vm.viewModelScope.launch {
            vm.modelFlow.collect { onModel(it) }
        })
    }

    /**
     * Clears the ViewModel, which cancels its `viewModelScope` and with it [observeModels] and the
     * collections started by [collectSignals] on [scope].
     * Idempotent. A host cleared before its ViewModel was created never creates one.
     *
     * After it, [model], [scope] and [observeModels] throw an [IllegalStateException], whether the
     * ViewModel was created or not. They are not declared with `@Throws`, so called from Swift the
     * exception ends the process, as a failed `precondition` does: a view must not read a host it
     * cleared. What was handed out before keeps working without error but does nothing: a [scope]
     * taken earlier ignores the intents posted to it, and a collection started on it ends at once.
     */
    public fun clear() {
        cleared = true
        store.clear()
    }
}
