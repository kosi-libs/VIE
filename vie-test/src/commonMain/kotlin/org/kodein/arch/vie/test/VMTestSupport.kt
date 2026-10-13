package org.kodein.arch.vie.test

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewModel

/**
 * Builds and drives a [VIEViewModel] on virtual time, with no DI container involved — the view
 * model is constructed by hand in [newVM], wiring in whatever real collaborators the test needs.
 *
 * A few [VIEViewModel] mechanics make this more than a plain constructor call:
 * - `signalFlow` has no replay: a signal emitted with nobody collecting is lost — [block] gets a
 *   list that is fed from a collector subscribed once [newVM] returns, before any of the view model's
 *   coroutines run (unless [newVM] itself advances the scheduler). That collector runs on an
 *   [UnconfinedTestDispatcher], not the [StandardTestDispatcher] everything else uses: a signal is
 *   posted through a zero-buffer `MutableSharedFlow` from a *different* dispatcher instance
 *   (`viewModelScope`'s) than the one this collector would otherwise use, and — only once that
 *   emitting coroutine has resumed from a `delay(...)` first — `StandardTestDispatcher` here never
 *   schedules the hand-off in a way `advanceUntilIdle`/`runCurrent` observe as pending, silently
 *   dropping the signal. `Unconfined` sidesteps this by resuming the collector inline instead of
 *   queuing it.
 * - [block] also gets the models `modelFlow` publishes, collected the same way: the first entry is the
 *   model current when [newVM] returns, then one entry per model the view would render, in order. A
 *   model equal to the previous one is not recorded (`modelFlow` is a `StateFlow`), so an intent that
 *   changes nothing leaves the list as it was.
 * - `viewModelScope` resolves `Dispatchers.Main` the first time it is touched, which for these
 *   view models happens synchronously in `init {}` — so [Dispatchers.Main] is swapped for a
 *   [StandardTestDispatcher] tied to this [runTest]'s own scheduler *before* [newVM] runs. Every
 *   `delay(...)` the view model — and any repository it calls — performs then runs on virtual
 *   time, advanced from [block] with [kotlinx.coroutines.test.advanceUntilIdle] and
 *   [runCurrent].
 *
 * The view model is cleared once [block] returns, cancelling `viewModelScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal, VM : VIEViewModel<Model, Intent, Signal>> runVMTest(
    newVM: suspend TestScope.() -> VM,
    block: suspend TestScope.(vm: VM, models: List<Model>, signals: List<Signal>) -> Unit,
): TestResult = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val vm = newVM()
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            val models = mutableListOf<Model>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.modelFlow.collect { models += it }
            }
            val signals = mutableListOf<Signal>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.signalFlow.collect { signals += it }
            }
            runCurrent()

            block(vm, models, signals)
        } finally {
            store.clear()
        }
    } finally {
        Dispatchers.resetMain()
    }
}
