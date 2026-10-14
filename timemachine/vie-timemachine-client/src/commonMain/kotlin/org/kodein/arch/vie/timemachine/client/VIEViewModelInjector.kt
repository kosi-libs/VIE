package org.kodein.arch.vie.timemachine.client

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewModel


/**
 * Places an intervened object into a live ViewModel, through the same public entry points the
 * ViewModel's own code uses — so every middleware sees it, wherever the [TimeMachineMiddleware] is
 * installed.
 *
 * Each injection runs on `viewModelScope`, so an injection dies with its ViewModel. Each runs
 * `entering` on the ViewModel's dispatcher just before its object enters the ViewModel, so that the
 * middleware can recognise the object when it is observed.
 *
 * Each injection returns the `Job` it launched: one that is already cancelled tells that the ViewModel
 * was cleared before the injection was launched, so its object will never enter it.
 */
internal class VIEViewModelInjector<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    private val vm: VIEViewModel<Model, Intent, Signal>,
) {

    /**
     * Makes [model] the current model through `update {}`, serialized against every other update,
     * so it becomes the model the next `update { copy(…) }` computes from.
     */
    fun injectModel(model: Model, entering: () -> Unit): Job =
        vm.viewModelScope.launch {
            vm.update {
                entering()
                model
            }
        }

    /** Posts [intent], as the view would. */
    fun injectIntent(intent: Intent, entering: () -> Unit): Job =
        vm.viewModelScope.launch {
            entering()
            vm.post(intent)
        }

    /**
     * Emits [signal], as the ViewModel would; or returns `null` if it is neither a
     * [VIE.Signal.Event] nor a [VIE.Signal.Effect], which the ViewModel cannot emit.
     */
    fun injectSignal(signal: Signal, entering: () -> Unit): Job? {
        // The smart cast makes signal a `Signal & VIE.Signal.Type`: exactly what signal()'s bound asks for.
        if (signal !is VIE.Signal.Type || signal is VIE.Signal.None) return null
        return vm.viewModelScope.launch {
            entering()
            vm.signal(signal)
        }
    }
}
