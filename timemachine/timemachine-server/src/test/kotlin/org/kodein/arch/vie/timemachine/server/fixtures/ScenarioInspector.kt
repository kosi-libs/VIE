package org.kodein.arch.vie.timemachine.server.fixtures

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.kodein.arch.vie.timemachine.server.store.TimeMachineStore
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorSelection
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewModels
import org.kodein.arch.vie.timemachine.server.ui.core.PaneVisibility


/** A clock frozen on [instant]: every scenario times its events relative to it. */
internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/**
 * An [Inspector] over a new store that goes through this scenario's [ScenarioData.events], with the
 * scripted probe as its `execute` and a clock frozen at [ScenarioData.now]. The store applies the
 * events on [scope], so they are in once its writer has run.
 */
internal fun ScenarioData.newInspector(
    scope: CoroutineScope,
    compute: CoroutineDispatcher,
    clock: Clock = FixedClock(now),
    retry: () -> Unit = {},
): Inspector {
    val probe = FakeProbe(this, scope)
    val store = TimeMachineStore(scope, maxRows = maxRows, interventionTimeout = interventionTimeout, execute = probe::execute)
    probe.attach(store)
    events.forEach(store::post)
    return Inspector(store, InspectorSelection(), PaneVisibility(), InspectorViewModels(), compute, clock, retry)
}
