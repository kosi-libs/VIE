package org.kodein.arch.vie

import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
