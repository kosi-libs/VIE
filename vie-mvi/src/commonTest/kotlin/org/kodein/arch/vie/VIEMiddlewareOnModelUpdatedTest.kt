package org.kodein.arch.vie

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CountModel(val count: Int) : VIE.Model

private sealed interface CountIntent : VIE.Intent {
    data object Ping : CountIntent
}

private sealed interface CountSignal : VIE.Signal

private class CountVM(
    middlewares: List<VIEMiddleware<CountModel, CountIntent, CountSignal>> = emptyList(),
) : VIEViewModel<CountModel, CountIntent, CountSignal>(CountModel(0), middlewares) {
    /** What `process` read from `model`, which no view ever collected. */
    val seenByProcess = mutableListOf<CountModel>()

    override suspend fun process(intent: CountIntent) {
        update { copy(count = count + 1) }
        seenByProcess += model
    }

    suspend fun set(count: Int) = update { copy(count = count) }

    val current get() = model
}

private class RecordingMiddleware : VIEMiddleware<CountModel, CountIntent, CountSignal> {
    val updated = mutableListOf<CountModel>()
    override fun onModelUpdated(model: CountModel) {
        updated += model
    }
}

private class NoHookMiddleware : VIEMiddleware<CountModel, CountIntent, CountSignal>

@OptIn(ExperimentalCoroutinesApi::class)
class VIEMiddlewareOnModelUpdatedTest {

    private fun test(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun TestScope.withVM(vm: CountVM, block: suspend () -> Unit) {
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            block()
        } finally {
            store.clear()
            advanceUntilIdle()
        }
    }

    @Test
    fun modelIsCurrentAfterUpdateWithNoCollector() = test {
        val vm = CountVM()
        withVM(vm) {
            assertEquals(CountModel(0), vm.current)

            launch { vm.set(7) }
            advanceUntilIdle()
            assertEquals(CountModel(7), vm.current)

            vm.post(CountIntent.Ping)
            advanceUntilIdle()
            assertEquals(listOf(CountModel(8)), vm.seenByProcess)
        }
    }

    @Test
    fun aBurstOfUpdatesReachesOnModelUpdatedOnceEachInOrder() = test {
        val recording = RecordingMiddleware()
        val vm = CountVM(listOf(recording))
        withVM(vm) {
            repeat(5) { launch { vm.set(it + 1) } }
            advanceUntilIdle()

            assertEquals((1..5).map(::CountModel), recording.updated)
        }
    }

    @Test
    fun everyMiddlewareIsToldInListOrder() = test {
        val first = RecordingMiddleware()
        val second = RecordingMiddleware()
        val vm = CountVM(listOf(first, NoHookMiddleware(), second))
        withVM(vm) {
            launch { vm.set(3) }
            advanceUntilIdle()

            assertEquals(listOf(CountModel(3)), first.updated)
            assertEquals(listOf(CountModel(3)), second.updated)
        }
    }

    @Test
    fun plainMiddlewareIsUnaffected() = test {
        val vm = CountVM(listOf(NoHookMiddleware()))
        withVM(vm) {
            vm.post(CountIntent.Ping)
            advanceUntilIdle()
            assertEquals(listOf(CountModel(1)), vm.seenByProcess)
        }
    }
}
