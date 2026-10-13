package org.kodein.arch.vie

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private data class TestModel(val value: Int) : VIE.Model

private sealed interface TestIntent : VIE.Intent {
    data object Ping : TestIntent
}

private sealed interface TestSignal : VIE.Signal {
    data object Pong : TestSignal, VIE.Signal.Event
}

private class TestVM(
    middlewares: List<VIEMiddleware<TestModel, TestIntent, TestSignal>>,
) : VIEViewModel<TestModel, TestIntent, TestSignal>(TestModel(0), middlewares) {
    val processed = mutableListOf<TestIntent>()
    override suspend fun process(intent: TestIntent) {
        processed += intent
    }
}

private class RecordingAwareMiddleware : VIEMiddleware<TestModel, TestIntent, TestSignal> {
    var attachCount = 0
    var attachedVM: VIEViewModel<TestModel, TestIntent, TestSignal>? = null

    override fun onAttached(vm: VIEViewModel<TestModel, TestIntent, TestSignal>) {
        attachCount++
        attachedVM = vm
    }
}

private class PlainMiddleware : VIEMiddleware<TestModel, TestIntent, TestSignal>

@OptIn(ExperimentalCoroutinesApi::class)
internal class VIEMiddlewareOnAttachedTest : VIETest() {

    @Test
    fun onAttachedFiresOnceWithTheRightInstanceBeforeTheFirstIntentIsProcessed() = runTest {
        val aware = RecordingAwareMiddleware()
        val vm = TestVM(listOf(aware))
        assertEquals(1, aware.attachCount)
        assertSame(vm, aware.attachedVM)
        assertEquals(emptyList<TestIntent>(), vm.processed)

        vm.post(TestIntent.Ping)
        advanceUntilIdle()
        assertEquals(listOf<TestIntent>(TestIntent.Ping), vm.processed)
    }

    // Immediate, as `Dispatchers.Main.immediate` is in an app: the model flow then runs during
    // construction, and must still not run before the middleware is attached.
    @Test
    fun onAttachedFiresBeforeTheFirstModelReachesInterceptModels() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val events = mutableListOf<String>()
        val middleware = object : VIEMiddleware<TestModel, TestIntent, TestSignal> {
            override fun interceptModels(flow: Flow<TestModel>): Flow<TestModel> = flow.onEach { events += "model ${it.value}" }
            override fun onAttached(vm: VIEViewModel<TestModel, TestIntent, TestSignal>) {
                events += "attached"
            }
        }
        TestVM(listOf(middleware))
        advanceUntilIdle()
        assertEquals(listOf("attached", "model 0"), events)
    }

    @Test
    fun plainMiddlewareIsUnaffected()= runTest {
        val vm = TestVM(listOf(PlainMiddleware()))
        vm.post(TestIntent.Ping)
        advanceUntilIdle()
        assertEquals(listOf<TestIntent>(TestIntent.Ping), vm.processed)
    }

    @Test
    fun onAttachedFiresForEveryAwareMiddlewareInAMixedList() = runTest {
        val first = RecordingAwareMiddleware()
        val second = RecordingAwareMiddleware()
        val vm = TestVM(listOf(first, PlainMiddleware(), second))

        assertEquals(1, first.attachCount)
        assertSame(vm, first.attachedVM)
        assertEquals(1, second.attachCount)
        assertSame(vm, second.attachedVM)
    }
}
