package org.kodein.arch.vie.ios

import kotlinx.coroutines.Job


/** Stops an observation started by [VIEHost.observeModels] or [collectSignals]. */
public class VIECancellable internal constructor(private val job: Job) {
    /**
     * Whether the observation still runs.
     *
     * It is `false` once the observation is stopped, by [cancel] or by the clear of its [VIEHost],
     * and also when it ended by itself: a [collectSignals] on `EmptyVIE`, whose signals never
     * fire, ends at once.
     */
    public val isActive: Boolean get() = job.isActive

    /** Stops the observation. Idempotent. */
    public fun cancel() {
        job.cancel()
    }
}
