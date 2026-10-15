package org.kodein.arch.vie.test

import kotlinx.coroutines.launch
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CountModel(val count: Int) : VIE.Model

private sealed interface CountIntent : VIE.Intent {
    data class Set(val count: Int) : CountIntent
}

private data class Notified(val count: Int) : VIE.Signal.Event

private class CountVM : VIEViewModel<CountModel, CountIntent, Notified>(CountModel(0)) {
    override suspend fun process(intent: CountIntent) {
        when (intent) {
            is CountIntent.Set -> {
                update { copy(count = intent.count) }
                signal(Notified(intent.count))
            }
        }
    }
}

class RunVMTestTest {

    @Test
    fun modelsAreDrainedInOrder() = runVMTest(
        newVM = { CountVM() },
    ) {
        post(CountIntent.Set(1))
        post(CountIntent.Set(2))
        advanceUntilIdle()

        assertEquals(listOf(CountModel(0), CountModel(1), CountModel(2)), pendingModels())
    }

    @Test
    fun aDrainReturnsOnlyWhatArrivedSinceThePreviousOne() = runVMTest(
        newVM = { CountVM() },
    ) {
        post(CountIntent.Set(1))
        advanceUntilIdle()
        assertEquals(listOf(CountModel(0), CountModel(1)), pendingModels())
        assertEquals(emptyList(), pendingModels())

        post(CountIntent.Set(2))
        advanceUntilIdle()
        assertEquals(listOf(CountModel(2)), pendingModels())
    }

    @Test
    fun signalsAreDrainedInOrder() = runVMTest(
        newVM = { CountVM() },
    ) {
        post(CountIntent.Set(1))
        post(CountIntent.Set(2))
        advanceUntilIdle()

        assertEquals(listOf(Notified(1), Notified(2)), pendingSignals())
        assertEquals(emptyList(), pendingSignals())
    }

    @Test
    fun modelIsTheCurrentOneWhateverWasDrained() = runVMTest(
        newVM = { CountVM() },
    ) {
        post(CountIntent.Set(3))
        advanceUntilIdle()

        assertEquals(CountModel(3), model)
        pendingModels()
        assertEquals(CountModel(3), model)
    }

    @Test
    fun concurrentIntentsAreAllRecorded() = runVMTest(
        newVM = { CountVM() },
    ) {
        repeat(5) { launch { post(CountIntent.Set(it + 1)) } }
        advanceUntilIdle()

        assertEquals((0..5).map(::CountModel), pendingModels())
    }

    @Test
    fun anEqualModelIsNotRecorded() = runVMTest(
        newVM = { CountVM() },
    ) {
        post(CountIntent.Set(0))
        advanceUntilIdle()

        assertEquals(listOf(CountModel(0)), pendingModels())
    }
}
