package org.kodein.arch.vie

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class VIEMiddlewareOnAttachedTest {

    @Test
    fun onAttachedFiresOnceWithTheRightInstanceBeforeTheFirstIntentIsProcessed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val aware = RecordingAwareMiddleware()
            val vm = TestVM(listOf(aware))
            val store = ViewModelStore().apply { put("vm", vm) }
            try {
                assertEquals(1, aware.attachCount)
                assertSame(vm, aware.attachedVM)
                assertEquals(emptyList<TestIntent>(), vm.processed)

                vm.post(TestIntent.Ping)
                advanceUntilIdle()
                assertEquals(listOf<TestIntent>(TestIntent.Ping), vm.processed)
            } finally {
                store.clear()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun plainMiddlewareIsUnaffected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = TestVM(listOf(PlainMiddleware()))
            val store = ViewModelStore().apply { put("vm", vm) }
            try {
                vm.post(TestIntent.Ping)
                advanceUntilIdle()
                assertEquals(listOf<TestIntent>(TestIntent.Ping), vm.processed)
            } finally {
                store.clear()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun onAttachedFiresForEveryAwareMiddlewareInAMixedList() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val first = RecordingAwareMiddleware()
            val second = RecordingAwareMiddleware()
            val vm = TestVM(listOf(first, PlainMiddleware(), second))
            val store = ViewModelStore().apply { put("vm", vm) }
            try {
                assertEquals(1, first.attachCount)
                assertSame(vm, first.attachedVM)
                assertEquals(1, second.attachCount)
                assertSame(vm, second.attachedVM)
            } finally {
                store.clear()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
