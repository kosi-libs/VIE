package org.kodein.arch.vie

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
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

    suspend fun increment() = update { copy(count = count + 1) }

    val current get() = model
}

private class RecordingMiddleware : VIEMiddleware<CountModel, CountIntent, CountSignal> {
    val seen = mutableListOf<CountModel>()
    override fun interceptModels(flow: Flow<CountModel>): Flow<CountModel> = flow.onEach { seen += it }
}

private class NoHookMiddleware : VIEMiddleware<CountModel, CountIntent, CountSignal>

@OptIn(ExperimentalCoroutinesApi::class)
internal class VIEMiddlewareInterceptModelsTest : VIETest() {

    @Test
    fun modelIsCurrentAfterUpdateWithNoCollector()= runTest {
        val vm = CountVM()
        assertEquals(CountModel(0), vm.current)

        launch { vm.set(7) }
        advanceUntilIdle()
        assertEquals(CountModel(7), vm.current)

        vm.post(CountIntent.Ping)
        advanceUntilIdle()
        assertEquals(listOf(CountModel(8)), vm.seenByProcess)
    }

    @Test
    fun modelFlowFollowsUpdatesWithNoCollector()= runTest {
        val vm = CountVM()
        launch { vm.set(4) }
        advanceUntilIdle()
        assertEquals(CountModel(4), vm.modelFlow.value)
    }

    @Test
    fun updatesAppliedBackToBackComposeEachOnTheLatestModel()= runTest {
        val vm = CountVM()
        repeat(5) { launch { vm.increment() } }
        advanceUntilIdle()

        assertEquals(CountModel(5), vm.current)
        assertEquals(CountModel(5), vm.modelFlow.value)
    }

    @Test
    fun aBurstOfUpdatesReachesInterceptModelsOnceEachInOrderAfterTheFirstModel()= runTest {
        val recording = RecordingMiddleware()
        val vm = CountVM(listOf(recording))
        repeat(5) { launch { vm.set(it + 1) } }
        advanceUntilIdle()

        assertEquals((0..5).map(::CountModel), recording.seen)
    }

    @Test
    fun anUpdateLeavingTheModelEqualStillReachesInterceptModels()= runTest {
        val recording = RecordingMiddleware()
        val vm = CountVM(listOf(recording))
        launch { vm.set(0) }
        advanceUntilIdle()

        assertEquals(listOf(CountModel(0), CountModel(0)), recording.seen)
    }

    @Test
    fun everyMiddlewareSeesEveryModel()= runTest {
        val first = RecordingMiddleware()
        val second = RecordingMiddleware()
        val vm = CountVM(listOf(first, NoHookMiddleware(), second))
        launch { vm.set(3) }
        advanceUntilIdle()

        assertEquals(listOf(CountModel(0), CountModel(3)), first.seen)
        assertEquals(listOf(CountModel(0), CountModel(3)), second.seen)
    }

    @Test
    fun aSlowMiddlewareDoesNotDelayUpdate() = runTest {
        val recording = RecordingMiddleware()
        val slow = object : VIEMiddleware<CountModel, CountIntent, CountSignal> {
            override fun interceptModels(flow: Flow<CountModel>): Flow<CountModel> = flow.onEach { delay(1000) }
        }
        val vm = CountVM(listOf(slow, recording))
        var returnedAt = -1L
        launch {
            repeat(5) { vm.set(it + 1) }
            returnedAt = currentTime
        }
        advanceUntilIdle()

        assertEquals(0L, returnedAt)
        assertEquals((0..5).map(::CountModel), recording.seen)
    }

    @Test
    fun aMiddlewareUpdatingFromInterceptModelsDoesNotDeadlock() = runTest {
        lateinit var attached: CountVM
        val reentrant = object : VIEMiddleware<CountModel, CountIntent, CountSignal> {
            override fun onAttached(vm: VIEViewModel<CountModel, CountIntent, CountSignal>) {
                attached = vm as CountVM
            }

            override fun interceptModels(flow: Flow<CountModel>): Flow<CountModel> = flow.onEach {
                if (it.count == 1) attached.update { copy(count = count + 100) }
            }
        }
        val vm = CountVM(listOf(reentrant))
        launch { vm.set(1) }
        launch { vm.set(2) }
        advanceUntilIdle()

        assertEquals(CountModel(102), vm.current)
        assertEquals(CountModel(102), vm.modelFlow.value)
    }

    @Test
    fun plainMiddlewareIsUnaffected()= runTest {
        val vm = CountVM(listOf(NoHookMiddleware()))
        vm.post(CountIntent.Ping)
        advanceUntilIdle()
        assertEquals(listOf(CountModel(1)), vm.seenByProcess)
    }
}
