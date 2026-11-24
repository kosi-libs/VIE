package org.kodein.arch.vie.ios

import kotlinx.coroutines.Job


/** Stops an observation started by [VIEHost.observeModels] or [collectSignals]. */
public class VIECancellable internal constructor(internal val job: Job) {
    /** Stops the observation. Idempotent. */
    public fun cancel() {
        job.cancel()
    }
}
