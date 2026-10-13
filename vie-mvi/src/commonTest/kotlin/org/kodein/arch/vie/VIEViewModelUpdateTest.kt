package org.kodein.arch.vie

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private data class UpdateModel(val count: Int) : VIE.Model

private sealed interface UpdateIntent : VIE.Intent {
    data object Fail : UpdateIntent
    data object Increment : UpdateIntent
}

private sealed interface UpdateSignal : VIE.Signal

private class UpdateVM : VIEViewModel<UpdateModel, UpdateIntent, UpdateSignal>(UpdateModel(0)) {
    override suspend fun process(intent: UpdateIntent) {
        when (intent) {
            UpdateIntent.Fail -> runCatching { update { error("boom") } }
            UpdateIntent.Increment -> update { copy(count = count + 1) }
        }
    }

    suspend fun set(count: Int) = update { copy(count = count) }

    suspend fun fail() = update { error("boom") }

    val current get() = model
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class VIEViewModelUpdateTest : VIETest() {

    @Test
    fun aThrowingUpdateFailsItsOwnCallerAndLeavesTheModelUnchanged() = runTest {
        val vm = UpdateVM()
        vm.set(2)
        var failure: IllegalStateException? = null
        launch { failure = assertFailsWith<IllegalStateException> { vm.fail() } }
        advanceUntilIdle()

        assertEquals("boom", failure?.message)
        assertEquals(UpdateModel(2), vm.current)
    }

    @Test
    fun anUpdateAfterAThrowingOneStillApplies() = runTest {
        val vm = UpdateVM()
        launch { runCatching { vm.fail() } }
        launch { vm.set(3) }
        advanceUntilIdle()

        assertEquals(UpdateModel(3), vm.current)
        assertEquals(UpdateModel(3), vm.modelFlow.value)
    }

    @Test
    fun aProcessThatCatchesAThrowingUpdateKeepsHandlingIntents() = runTest {
        val vm = UpdateVM()
        vm.post(UpdateIntent.Fail)
        vm.post(UpdateIntent.Increment)
        advanceUntilIdle()

        assertEquals(UpdateModel(1), vm.current)
    }
}
