package org.kodein.arch.vie.ios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewScope
import kotlin.coroutines.resume


/**
 * Calls [onSignal] with each signal of this scope, until the returned [VIECancellable] is
 * cancelled or, when this scope is a ViewModel such as [VIEHost.scope], until it is cleared: the
 * collection then stops at once, even while it waits for `done`, and one started after the clear
 * never calls [onSignal]. On any other scope, `EmptyVIE` for one, only [VIECancellable.cancel] stops it.
 * [onSignal] is not called again until it calls its `done` callback, as `SignalEffect`
 * waits for its suspending lambda to return.
 *
 * It works on any [VIEViewScope]: a [VIEHost]'s [VIEHost.scope], or `EmptyVIE` in a preview, whose
 * signals never fire. An Effect taken by an observation that is cancelled before it called `done`
 * is lost, as with `SignalEffect`, and only that one: the Effects queued behind it go to the next
 * observation.
 *
 * Unlike `SignalEffect`, which always calls the lambda of its latest composition, [onSignal] is
 * the very lambda passed to this call, for every signal: values it captured are not refreshed.
 * To follow a changing view, cancel the returned [VIECancellable] and collect again.
 *
 * The callbacks run on the main thread. Called from it, this function usually hands [onSignal] the
 * first Effect already queued before it returns, and the next ones too as long as [onSignal] calls
 * `done` right away: the returned [VIECancellable] is not available to it yet. When this is called
 * from within another callback that runs the same way, the first call comes after the return.
 * Rely on neither order.
 *
 * @param Signal The one-off type this scope emits.
 * @receiver The scope whose signals are collected.
 * @param onSignal Called with each signal, and a `done` callback to call once it is handled.
 */
public fun <Signal : VIE.Signal> VIEViewScope<*, Signal>.collectSignals(
    onSignal: (signal: Signal, done: () -> Unit) -> Unit,
): VIECancellable {
    // On a ViewModel, as `VIEHost.scope` is, the collection ends when it is cleared, as `observeModels`
    // does. Cancelling the coroutine, not ending the flow, is what releases a collection that is
    // waiting for `done`, and the callback it holds.
    val parent = (this as? ViewModel)?.viewModelScope ?: CoroutineScope(Dispatchers.Main.immediate)
    val job = parent.launch(Dispatchers.Main.immediate) {
        signalFlow.collect { signal ->
            suspendCancellableCoroutine { continuation ->
                onSignal(signal) {
                    // `done` is called from Swift: tolerate a second call, or a call after cancellation.
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
    }
    return VIECancellable(job)
}
