package org.kodein.arch.vie.timemachine.server.fixtures

import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.kodein.arch.vie.timemachine.server.store.Limits
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope

/** A clock over the test scheduler's virtual time, starting at [base]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VirtualClock(private val scheduler: TestCoroutineScheduler, private val base: Instant) : Clock {
    override fun now(): Instant = base + scheduler.currentTime.milliseconds
}

/** The Demo's session, and the scopes the design looks at. */
internal val demoSession = InspectorScope.Session(Demo.session.id)
internal val taskListScope = InspectorScope.ViewModel(Demo.session.id, Demo.taskList)

/**
 * An inspector over scenario [id], its events applied and its pane ViewModels computing on the test
 * scheduler, with a clock that starts at the scenario's `now`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.inspector(id: String, data: ScenarioData = ScenarioData.of(id)): Inspector =
    data.newInspector(backgroundScope, StandardTestDispatcher(testScheduler), VirtualClock(testScheduler, data.now)).also { runCurrent() }

/** The state this scenario's events lead to in a store with the scenario's bounds, without any probe. */
internal fun ScenarioData.state(): TimeMachineState =
    TimeMachineState(limits = Limits(maxRows = maxRows)).on(events).state
