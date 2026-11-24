package org.kodein.arch.vie.ios

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

    // A known limit of VIEViewModel.signalFlow, which merges its Effects and its Events through a
    // buffer: an observation cancelled while it handles an Effect loses that Effect, and also the
    // Effects already queued behind it, which the buffer had taken from the channel.
    @Test
    fun anObservationCancelledWhileHandlingAnEffectLosesTheOnesQueuedBehindIt() = runTest {
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
        assertEquals(emptyList(), second)

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
    fun aHostClearedBeforeUseNeverCreatesAViewModel() = runTest {
        var created = false
        val host = VIEHost { HostVM().also { created = true } }
        host.clear()

        assertFailsWith<IllegalStateException> { host.model }
        assertFalse(created)
    }
}
