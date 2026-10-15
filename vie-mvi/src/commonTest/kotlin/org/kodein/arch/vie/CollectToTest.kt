package org.kodein.arch.vie

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CollectModel(val total: Int) : VIE.Model

private sealed interface CollectIntent : VIE.Intent

private sealed interface CollectSignal : VIE.Signal {
    data class Shown(val value: Int) : CollectSignal, VIE.Signal.Event
}

private class CollectVM : VIEViewModel<CollectModel, CollectIntent, CollectSignal>(CollectModel(0)) {
    override suspend fun process(intent: CollectIntent) = Unit

    suspend fun addAll(values: List<Int>) = flowOf(*values.toTypedArray()).collectToModel { copy(total = total + it) }
    suspend fun showAll(values: List<Int>) = flowOf(*values.toTypedArray()).collectToSignal { CollectSignal.Shown(it) }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class CollectToTest : VIETest() {

    @Test
    fun collectToModelFoldsEachValueIntoTheModel() = runTest {
        val vm = CollectVM()
        advanceUntilIdle()
        launch { vm.addAll(listOf(1, 2, 3)) }
        advanceUntilIdle()

        assertEquals(6, vm.modelFlow.value.total)
    }

    @Test
    fun collectToSignalEmitsEachConvertedValue() = runTest {
        val vm = CollectVM()
        val received = mutableListOf<CollectSignal>()
        val collector = launch { vm.signalFlow.collect { received += it } }
        advanceUntilIdle()
        launch { vm.showAll(listOf(1, 2)) }
        advanceUntilIdle()
        collector.cancel()

        assertEquals<List<CollectSignal>>(listOf(CollectSignal.Shown(1), CollectSignal.Shown(2)), received)
    }
}
