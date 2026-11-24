package org.kodein.arch.vie.ios

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.kodein.arch.vie.EmptyVIE
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIETest
import org.kodein.arch.vie.VIEViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class HostModel(val count: Int) : VIE.Model

private sealed interface HostIntent : VIE.Intent {
    data object Increment : HostIntent
    data object Shout : HostIntent
    data object Ping : HostIntent
}

private sealed interface HostSignal : VIE.Signal {
    data class Shown(val id: Int) : HostSignal, VIE.Signal.Effect
    data object Pinged : HostSignal, VIE.Signal.Event
}

private class HostVM : VIEViewModel<HostModel, HostIntent, HostSignal>(HostModel(0)) {
    var cleared = false

    override suspend fun process(intent: HostIntent) {
        when (intent) {
            HostIntent.Increment -> update { copy(count = count + 1) }
            HostIntent.Shout -> {
                signal(HostSignal.Shown(1))
                signal(HostSignal.Shown(2))
            }
            HostIntent.Ping -> signal(HostSignal.Pinged)
        }
    }

    override fun onCleared() {
        cleared = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class VIEHostTest : VIETest() {

    @Test
    fun theViewModelIsCreatedOnFirstUseOnly() = runTest {
        val created = mutableListOf<HostVM>()
        val host = VIEHost { HostVM().also { created += it } }
        assertEquals(0, created.size)

        assertEquals(HostModel(0), host.model)
        host.scope.post(HostIntent.Increment)
        assertEquals(1, created.size)

        host.clear()
    }

    @Test
    fun observeModelsReceivesTheCurrentModelThenEveryUpdate() = runTest {
        val host = VIEHost { HostVM() }
        val models = mutableListOf<HostModel>()
        val observation = host.observeModels { models += it }
        advanceUntilIdle()

        host.scope.post(HostIntent.Increment)
        advanceUntilIdle()
        host.scope.post(HostIntent.Increment)
        advanceUntilIdle()
        assertEquals(listOf(HostModel(0), HostModel(1), HostModel(2)), models)
        assertEquals(HostModel(2), host.model)

        observation.cancel()
        host.scope.post(HostIntent.Increment)
        advanceUntilIdle()
        assertEquals(3, models.size)

        host.clear()
    }

    @Test
    fun theNextSignalWaitsForDone() = runTest {
        val host = VIEHost { HostVM() }
        val signals = mutableListOf<HostSignal>()
        val dones = mutableListOf<() -> Unit>()
        host.scope.collectSignals { signal, done ->
            signals += signal
            dones += done
        }
        advanceUntilIdle()

        host.scope.post(HostIntent.Shout)
        advanceUntilIdle()
        assertEquals(listOf<HostSignal>(HostSignal.Shown(1)), signals)

        dones.last().invoke()
        dones.first().invoke() // A second call to the same done is harmless.
        advanceUntilIdle()
        assertEquals(listOf<HostSignal>(HostSignal.Shown(1), HostSignal.Shown(2)), signals)

        host.clear()
    }

    @Test
    fun aCancelledCollectionReceivesNoMoreSignals() = runTest {
        val host = VIEHost { HostVM() }
        val signals = mutableListOf<HostSignal>()
        val collection = host.scope.collectSignals { signal, done ->
            signals += signal
            done()
        }
        advanceUntilIdle()
        collection.cancel()
        collection.cancel() // Cancelling twice is harmless.
        advanceUntilIdle()

        host.scope.post(HostIntent.Shout)
        advanceUntilIdle()

        assertEquals(emptyList(), signals)

        host.clear()
    }

    @Test
    fun anEffectEmittedWithNoObserverIsDeliveredToTheNextOne() = runTest {
        val host = VIEHost { HostVM() }
        host.scope.post(HostIntent.Shout)
        advanceUntilIdle()

        val signals = mutableListOf<HostSignal>()
        host.scope.collectSignals { signal, done ->
            signals += signal
            done()
        }
        advanceUntilIdle()

        assertEquals(listOf<HostSignal>(HostSignal.Shown(1), HostSignal.Shown(2)), signals)

        host.clear()
    }

    @Test
    fun anEventEmittedWithNoObserverIsLost() = runTest {
        val host = VIEHost { HostVM() }
        host.scope.post(HostIntent.Ping)
        advanceUntilIdle()

        val signals = mutableListOf<HostSignal>()
        host.scope.collectSignals { signal, done ->
            signals += signal
            done()
        }
        advanceUntilIdle()

        assertEquals(emptyList(), signals)

        host.clear()
    }

    @Test
    fun anEventReachesEveryObserver() = runTest {
        val host = VIEHost { HostVM() }
        val first = mutableListOf<HostSignal>()
        val second = mutableListOf<HostSignal>()
        host.scope.collectSignals { signal, done -> first += signal; done() }
        host.scope.collectSignals { signal, done -> second += signal; done() }
        advanceUntilIdle()

        host.scope.post(HostIntent.Ping)
        advanceUntilIdle()

        assertEquals(listOf<HostSignal>(HostSignal.Pinged), first)
        assertEquals(listOf<HostSignal>(HostSignal.Pinged), second)

        host.clear()
    }

    // An observation cancelled while it handles an Effect loses that Effect, as SignalEffect does,
    // and only that one: the Effects queued behind it go to the next observation.
    @Test
    fun anObservationCancelledWhileHandlingAnEffectLosesOnlyThatOne() = runTest {
        val host = VIEHost { HostVM() }
        host.scope.post(HostIntent.Shout)
        advanceUntilIdle()

        val first = mutableListOf<HostSignal>()
        val collection = host.scope.collectSignals { signal, _ -> first += signal } // Never calls done.
        advanceUntilIdle()
        collection.cancel()
        advanceUntilIdle()

        val second = mutableListOf<HostSignal>()
        host.scope.collectSignals { signal, done ->
            second += signal
            done()
        }
        advanceUntilIdle()

        assertEquals(listOf<HostSignal>(HostSignal.Shown(1)), first)
        assertEquals(listOf<HostSignal>(HostSignal.Shown(2)), second)

        host.clear()
    }

    @Test
    fun collectingSignalsOfAnEmptyVIENeverFiresAndCancelsCleanly() = runTest {
        val signals = mutableListOf<HostSignal>()
        val collection = EmptyVIE<HostIntent, HostSignal>().collectSignals { signal, done ->
            signals += signal
            done()
        }
        advanceUntilIdle()
        collection.cancel()
        advanceUntilIdle()

        assertEquals(emptyList(), signals)
    }

    @Test
    fun clearClearsTheViewModelAndStopsModelObservation() = runTest {
        var vm: HostVM? = null
        val host = VIEHost { HostVM().also { vm = it } }
        val models = mutableListOf<HostModel>()
        host.observeModels { models += it }
        advanceUntilIdle()

        host.clear()
        host.clear()
        advanceUntilIdle()

        assertTrue(vm!!.cleared)
        assertEquals(listOf(HostModel(0)), models)
    }

    @Test
    fun clearStopsASignalCollectionOnItsScope() = runTest {
        val host = VIEHost { HostVM() }
        val collection = host.scope.collectSignals { _, done -> done() }
        advanceUntilIdle()
        assertTrue(collection.job.isActive)

        host.clear()
        advanceUntilIdle()

        assertFalse(collection.job.isActive)
    }

    // The collection is suspended on `done`, which it would otherwise wait for forever, keeping the
    // callback it was given, and everything that callback captured.
    @Test
    fun clearStopsASignalCollectionWaitingForDone() = runTest {
        val host = VIEHost { HostVM() }
        host.scope.post(HostIntent.Shout)
        advanceUntilIdle()
        val signals = mutableListOf<HostSignal>()
        val collection = host.scope.collectSignals { signal, _ -> signals += signal } // Never calls done.
        advanceUntilIdle()
        assertEquals(listOf<HostSignal>(HostSignal.Shown(1)), signals)
        assertTrue(collection.job.isActive)

        host.clear()
        advanceUntilIdle()

        assertFalse(collection.job.isActive)
    }

    @Test
    fun cancellingASignalCollectionDoesNotClearTheViewModel() = runTest {
        var vm: HostVM? = null
        val host = VIEHost { HostVM().also { vm = it } }
        val collection = host.scope.collectSignals { _, done -> done() }
        advanceUntilIdle()
        collection.cancel()
        advanceUntilIdle()

        val models = mutableListOf<HostModel>()
        host.observeModels { models += it }
        host.scope.post(HostIntent.Increment)
        advanceUntilIdle()

        assertFalse(vm!!.cleared)
        assertEquals(listOf(HostModel(0), HostModel(1)), models)

        host.clear()
    }

    // The four tests below run on an immediate main, as `Dispatchers.Main.immediate` is on the main
    // thread of an app: the launched body runs in place, before the function returns. The other tests
    // use a main that always queues, which an app never does.

    @Test
    fun onTheMainThreadObserveModelsDeliversTheCurrentModelBeforeItReturns() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val host = VIEHost { HostVM() }
        var returned = false
        val calls = mutableListOf<Pair<HostModel, Boolean>>()

        host.observeModels { calls += it to returned }
        returned = true

        assertEquals(listOf(HostModel(0) to false), calls)

        host.clear()
    }

    @Test
    fun onTheMainThreadCollectSignalsDeliversTheQueuedEffectsBeforeItReturns() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val host = VIEHost { HostVM() }
        host.scope.post(HostIntent.Shout)
        advanceUntilIdle()

        var returned = false
        val calls = mutableListOf<Pair<HostSignal, Boolean>>()
        host.scope.collectSignals { signal, done ->
            calls += signal to returned
            done()
        }
        returned = true

        assertEquals(listOf<Pair<HostSignal, Boolean>>(HostSignal.Shown(1) to false, HostSignal.Shown(2) to false), calls)

        host.clear()
    }

    @Test
    fun onTheMainThreadOnlyTheFirstQueuedEffectPrecedesTheReturnUntilDoneIsCalled() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val host = VIEHost { HostVM() }
        host.scope.post(HostIntent.Shout)
        advanceUntilIdle()

        var returned = false
        val calls = mutableListOf<Pair<HostSignal, Boolean>>()
        val dones = mutableListOf<() -> Unit>()
        host.scope.collectSignals { signal, done ->
            calls += signal to returned
            dones += done
        }
        returned = true

        assertEquals(listOf<Pair<HostSignal, Boolean>>(HostSignal.Shown(1) to false), calls)

        // The next Effect is handed over during `done` itself, with no scheduler to advance.
        dones.single().invoke()
        assertEquals(listOf<Pair<HostSignal, Boolean>>(HostSignal.Shown(1) to false, HostSignal.Shown(2) to true), calls)

        host.clear()
    }

    // The unconfined loop is already running while `onModel` is called, so the collection it starts
    // is queued behind it: its first callback comes after `collectSignals` returned.
    @Test
    fun aCollectionStartedFromACallbackStartsAfterItReturns() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val host = VIEHost { HostVM() }
        host.scope.post(HostIntent.Shout)
        advanceUntilIdle()

        val calls = mutableListOf<Pair<HostSignal, Boolean>>()
        var started = false
        host.observeModels {
            if (started) return@observeModels
            started = true
            var returned = false
            host.scope.collectSignals { signal, done ->
                calls += signal to returned
                done()
            }
            returned = true
        }
        advanceUntilIdle()

        assertEquals(listOf<Pair<HostSignal, Boolean>>(HostSignal.Shown(1) to true, HostSignal.Shown(2) to true), calls)

        host.clear()
    }

    @Test
    fun aHostClearedBeforeUseNeverCreatesAViewModel() = runTest {
        var created = false
        val host = VIEHost { HostVM().also { created = true } }
        host.clear()

        assertFailsWith<IllegalStateException> { host.model }
        assertFalse(created)
    }
}
