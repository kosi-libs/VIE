package org.kodein.arch.vie.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewScope
import kotlin.coroutines.resume


/**
 * Calls [onSignal] with each signal of this scope, until the returned [VIECancellable] is
 * cancelled. [onSignal] is not called again until it calls its `done` callback, as `SignalEffect`
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
 * The callbacks run on the main thread.
 *
 * @param Signal The one-off type this scope emits.
 * @receiver The scope whose signals are collected.
 * @param onSignal Called with each signal, and a `done` callback to call once it is handled.
 */
public fun <Signal : VIE.Signal> VIEViewScope<*, Signal>.collectSignals(
    onSignal: (signal: Signal, done: () -> Unit) -> Unit,
): VIECancellable {
    val job = Job()
    CoroutineScope(Dispatchers.Main.immediate + job).launch {
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

/** Stops an observation started by [VIEHost.observeModels] or [collectSignals]. */
public class VIECancellable internal constructor(private val job: Job) {
    /** Stops the observation. Idempotent. */
    public fun cancel() {
        job.cancel()
    }
}
