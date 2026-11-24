package org.kodein.arch.vie

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private data class SignalModel(val count: Int) : VIE.Model

private sealed interface SignalIntent : VIE.Intent

private sealed interface OneOff : VIE.Signal {
    data class Shown(val id: Int) : OneOff, VIE.Signal.Effect
    data object Saved : OneOff, VIE.Signal.Event
}

private class SignalVM : VIEViewModel<SignalModel, SignalIntent, OneOff>(SignalModel(0)) {
    override suspend fun process(intent: SignalIntent) = Unit

    suspend fun send(signal: OneOff.Shown) = signal(signal)
    suspend fun send(signal: OneOff.Saved) = signal(signal)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class VIEViewModelSignalTest : VIETest() {

    @Test
    fun anUncollectedEffectDoesNotHoldBackLaterSignals() = runTest {
        val vm = SignalVM()
        advanceUntilIdle()
        var sent = false
        launch {
            vm.send(OneOff.Shown(1))
            vm.send(OneOff.Shown(2))
            sent = true
        }
        advanceUntilIdle()
        assertEquals(true, sent)

        val events = mutableListOf<OneOff>()
        val collector = launch { vm.signalFlow.collect { events += it } }
        advanceUntilIdle()
        vm.send(OneOff.Saved)
        advanceUntilIdle()
        collector.cancel()

        assertEquals<List<OneOff>>(listOf(OneOff.Shown(1), OneOff.Shown(2), OneOff.Saved), events)
    }

    @Test
    fun anEffectReachesOnlyOneOfTwoCollectors() = runTest {
        val vm = SignalVM()
        val first = mutableListOf<OneOff>()
        val second = mutableListOf<OneOff>()
        val collectors = listOf(
            launch { vm.signalFlow.collect { first += it } },
            launch { vm.signalFlow.collect { second += it } },
        )
        advanceUntilIdle()
        vm.send(OneOff.Shown(1))
        advanceUntilIdle()
        collectors.forEach { it.cancel() }

        assertEquals(1, first.size + second.size)
    }

    // Only the Effect a collector is handling when it is cancelled is lost: the ones queued behind
    // it stay for the next collector. This is the case of a collector that subscribes after the
    // Effects were queued.
    @Test
    fun effectsQueuedBehindAnEffectBeingHandledSurviveItsCollectorsCancellation() = runTest {
        val vm = SignalVM()
        advanceUntilIdle()
        launch {
            vm.send(OneOff.Shown(1))
            vm.send(OneOff.Shown(2))
        }
        advanceUntilIdle()

        val first = mutableListOf<OneOff>()
        val collectorA = launch { vm.signalFlow.collect { first += it; awaitCancellation() } }
        advanceUntilIdle()
        collectorA.cancel()
        advanceUntilIdle()

        val second = mutableListOf<OneOff>()
        val collectorB = launch { vm.signalFlow.collect { second += it } }
        advanceUntilIdle()
        collectorB.cancel()

        assertEquals<List<OneOff>>(listOf(OneOff.Shown(1)), first)
        assertEquals<List<OneOff>>(listOf(OneOff.Shown(2)), second)
    }

    // The same, for a collector that was already collecting when the Effects were queued.
    @Test
    fun effectsQueuedWhileACollectorHandlesOneSurviveItsCancellation() = runTest {
        val vm = SignalVM()
        val first = mutableListOf<OneOff>()
        val collectorA = launch { vm.signalFlow.collect { first += it; awaitCancellation() } }
        advanceUntilIdle()
        launch {
            vm.send(OneOff.Shown(1))
            vm.send(OneOff.Shown(2))
        }
        advanceUntilIdle()
        collectorA.cancel()
        advanceUntilIdle()

        val second = mutableListOf<OneOff>()
        val collectorB = launch { vm.signalFlow.collect { second += it } }
        advanceUntilIdle()
        collectorB.cancel()

        assertEquals<List<OneOff>>(listOf(OneOff.Shown(1)), first)
        assertEquals<List<OneOff>>(listOf(OneOff.Shown(2)), second)
    }

    @Test
    fun aBusyCollectorDoesNotHoldBackEffectsFromAnIdleOne() = runTest {
        val vm = SignalVM()
        val busy = mutableListOf<OneOff>()
        val collectorA = launch { vm.signalFlow.collect { busy += it; awaitCancellation() } }
        advanceUntilIdle()
        launch { vm.send(OneOff.Shown(1)) }
        advanceUntilIdle()

        val idle = mutableListOf<OneOff>()
        val collectorB = launch { vm.signalFlow.collect { idle += it } }
        advanceUntilIdle()
        launch { vm.send(OneOff.Shown(2)) }
        advanceUntilIdle()

        assertEquals<List<OneOff>>(listOf(OneOff.Shown(1)), busy)
        assertEquals<List<OneOff>>(listOf(OneOff.Shown(2)), idle)

        collectorA.cancel()
        collectorB.cancel()
    }

    // The Effects queued before a collector subscribed are handed to it first, but must not be
    // handed ahead of an Event that was emitted before them.
    @Test
    fun anEffectDeliveredOnSubscriptionDoesNotOvertakeAnEarlierEvent() = runTest {
        val vm = SignalVM()
        advanceUntilIdle()
        launch { vm.send(OneOff.Shown(1)) }
        advanceUntilIdle()

        val received = mutableListOf<OneOff>()
        val release = CompletableDeferred<Unit>()
        val collector = launch {
            vm.signalFlow.collect {
                received += it
                if (received.size == 1) release.await()
            }
        }
        advanceUntilIdle()
        assertEquals<List<OneOff>>(listOf(OneOff.Shown(1)), received)

        launch {
            vm.send(OneOff.Saved)
            vm.send(OneOff.Shown(2))
        }
        advanceUntilIdle()
        release.complete(Unit)
        advanceUntilIdle()
        collector.cancel()

        assertEquals<List<OneOff>>(listOf(OneOff.Shown(1), OneOff.Saved, OneOff.Shown(2)), received)
    }

    // A handler that swallows its cancellation returns normally: the collector must not go on to
    // take another Effect it would then drop.
    @Test
    fun aCollectorSwallowingItsCancellationDoesNotTakeTheNextEffect() = runTest {
        val vm = SignalVM()
        advanceUntilIdle()
        launch {
            vm.send(OneOff.Shown(1))
            vm.send(OneOff.Shown(2))
        }
        advanceUntilIdle()

        val first = mutableListOf<OneOff>()
        val collectorA = launch {
            vm.signalFlow.collect {
                first += it
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    // Swallowed on purpose.
                }
            }
        }
        advanceUntilIdle()
        collectorA.cancel()
        advanceUntilIdle()

        val second = mutableListOf<OneOff>()
        val collectorB = launch { vm.signalFlow.collect { second += it } }
        advanceUntilIdle()
        collectorB.cancel()

        assertEquals<List<OneOff>>(listOf(OneOff.Shown(1)), first)
        assertEquals<List<OneOff>>(listOf(OneOff.Shown(2)), second)
    }

    @Test
    fun anEventEmittedWithNoCollectorIsLost() = runTest {
        val vm = SignalVM()
        advanceUntilIdle()
        launch { vm.send(OneOff.Saved) }
        advanceUntilIdle()

        val events = mutableListOf<OneOff>()
        val collector = launch { vm.signalFlow.collect { events += it } }
        advanceUntilIdle()
        collector.cancel()

        assertEquals<List<OneOff>>(emptyList(), events)
    }

    @Test
    fun anEventReachesEveryCollector() = runTest {
        val vm = SignalVM()
        val first = mutableListOf<OneOff>()
        val second = mutableListOf<OneOff>()
        val collectors = listOf(
            launch { vm.signalFlow.collect { first += it } },
            launch { vm.signalFlow.collect { second += it } },
        )
        advanceUntilIdle()
        launch { vm.send(OneOff.Saved) }
        advanceUntilIdle()
        collectors.forEach { it.cancel() }

        assertEquals<List<OneOff>>(listOf(OneOff.Saved), first)
        assertEquals<List<OneOff>>(listOf(OneOff.Saved), second)
    }
}
