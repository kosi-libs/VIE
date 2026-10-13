package org.kodein.arch.vie.test

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CountModel(val count: Int) : VIE.Model

private data object NoIntent : VIE.Intent

private data object NoSignal : VIE.Signal

private class CountVM : VIEViewModel<CountModel, NoIntent, NoSignal>(CountModel(0)) {
    override suspend fun process(intent: NoIntent) = Unit

    suspend fun set(count: Int) = update { copy(count = count) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RunVMTestTest {

    @Test
    fun modelsAreRecordedInOrder() = runVMTest(
        newVM = { CountVM() },
    ) { vm, models, _ ->
        vm.set(1)
        vm.set(2)
        advanceUntilIdle()

        assertEquals(listOf(CountModel(0), CountModel(1), CountModel(2)), models)
    }

    @Test
    fun concurrentUpdatesAreAllRecorded() = runVMTest(
        newVM = { CountVM() },
    ) { vm, models, _ ->
        repeat(5) { launch { vm.set(it + 1) } }
        advanceUntilIdle()

        assertEquals((0..5).map(::CountModel), models)
    }

    @Test
    fun anEqualModelIsNotRecorded() = runVMTest(
        newVM = { CountVM() },
    ) { vm, models, _ ->
        vm.set(0)
        advanceUntilIdle()

        assertEquals(listOf(CountModel(0)), models)
    }
}
