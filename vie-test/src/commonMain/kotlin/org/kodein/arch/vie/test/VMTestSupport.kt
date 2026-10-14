package org.kodein.arch.vie.test

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * What a test sees of a [VIEViewModel] under [runVMTest]: what a view sees, never the ViewModel itself.
 * It posts intents, reads the current [model] and drains the models and signals the view would have received.
 *
 * It mirrors [org.kodein.arch.vie.VIEViewScope] rather than implementing it: effects reach a single
 * collector, and this scope must be the only one on the ViewModel's `signalFlow`.
 *
 * It is a [CoroutineScope] on the test's own scope, so `launch` works in the block.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class VMTestScope<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> internal constructor(
    public val testScope: TestScope,
    private val vm: VIEViewModel<Model, Intent, Signal>,
    private val models: Channel<Model>,
    private val signals: Channel<Signal>,
) : CoroutineScope by testScope {

    /** The scheduler every coroutine of the test runs on. */
    public val testScheduler: TestCoroutineScheduler get() = testScope.testScheduler

    /** A scope for background work */
    public val backgroundScope: CoroutineScope get() = testScope.backgroundScope

    /** The current model, whatever has been drained. */
    public val model: Model get() = vm.modelFlow.value

    /** Sends [intent] up to the ViewModel, as the view would. */
    public fun post(intent: Intent): Unit = vm.post(intent)

    /**
     * Drains the models the view would have rendered since the previous call, in order. The first call starts with
     * the model current when `newVM` returned. A model equal to the previous one is not recorded (`modelFlow` is a
     * `StateFlow`).
     */
    public fun pendingModels(): List<Model> = models.drain()

    /** Drains the signals emitted since the previous call, in order. */
    public fun pendingSignals(): List<Signal> = signals.drain()

    /** Runs every task due now or later, until the scheduler has none left. */
    public fun advanceUntilIdle(): Unit = testScope.advanceUntilIdle()

    /** Runs the tasks due now, without moving virtual time. */
    public fun runCurrent(): Unit = testScope.runCurrent()

    /** Moves virtual time forward by [delayTimeMillis], running the tasks that come due. */
    public fun advanceTimeBy(delayTime: Duration): Unit = testScope.advanceTimeBy(delayTime)
}

/**
 * Builds a [VIEViewModel] by hand in [newVM] — no DI container — and drives it from [block] on virtual time, the way
 * a view would.
 *
 * - `signalFlow` has no replay: a signal emitted with nobody collecting is lost. Models and signals are therefore
 *   collected from the moment [newVM] returns, before any of the ViewModel's coroutines run (unless [newVM] itself
 *   advances the scheduler). The collectors use an [UnconfinedTestDispatcher]: a signal is posted from another
 *   dispatcher than a [StandardTestDispatcher] collector's, and once its emitter has resumed from a `delay(...)`,
 *   `advanceUntilIdle` does not see that hand-off as pending and the signal is dropped.
 * - `viewModelScope` resolves `Dispatchers.Main` when first touched, in `init {}`, so [Dispatchers.Main] is swapped
 *   for a [StandardTestDispatcher] on this test's scheduler *before* [newVM] runs. Every `delay(...)` then runs
 *   on virtual time, advanced from [block].
 *
 * The ViewModel is cleared once [block] returns, cancelling `viewModelScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal, VM : VIEViewModel<Model, Intent, Signal>> runVMTest(
    newVM: suspend TestScope.() -> VM,
    block: suspend VMTestScope<Model, Intent, Signal>.() -> Unit,
): TestResult = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val vm = newVM()
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            val models = Channel<Model>(Channel.UNLIMITED)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.modelFlow.collect { models.send(it) }
            }
            val signals = Channel<Signal>(Channel.UNLIMITED)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.signalFlow.collect { signals.send(it) }
            }
            runCurrent()

            VMTestScope(this, vm, models, signals).block()
        } finally {
            store.clear()
        }
    } finally {
        Dispatchers.resetMain()
    }
}
