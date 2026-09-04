package org.kodein.arch.vie

import org.kodein.arch.vie.VIE.NoSignal
import kotlin.test.Test
import kotlin.test.assertEquals


private data class Model(val count: Int) : VIE.Model

private sealed interface Intent : VIE.Intent {
    data object Increment : Intent
    data object Decrement : Intent
}

class MockVIETest {

    @Test
    fun postThenPendingIntents_returnsThemInOrder() {
        val vie = MockVIE<Model, Intent, NoSignal>(Model(count = 0))

        vie.post(Intent.Increment)
        vie.post(Intent.Increment)
        vie.post(Intent.Decrement)

        assertEquals(
            listOf(Intent.Increment, Intent.Increment, Intent.Decrement),
            vie.pendingIntents(),
        )
    }

    @Test
    fun pendingIntents_onUntouchedMock_isEmpty() {
        val vie = MockVIE<Model, Intent, NoSignal>(Model(count = 0))

        assertEquals(emptyList(), vie.pendingIntents())
    }

    @Test
    fun pendingIntents_drains() {
        val vie = MockVIE<Model, Intent, NoSignal>(Model(count = 0))

        vie.post(Intent.Increment)
        assertEquals(listOf(Intent.Increment), vie.pendingIntents())
        assertEquals(emptyList(), vie.pendingIntents())

        vie.post(Intent.Decrement)
        assertEquals(listOf(Intent.Decrement), vie.pendingIntents())
    }

    @Test
    fun model_reflectsModelFlowValue() {
        val vie = MockVIE<Model, Intent, NoSignal>(Model(count = 0))

        vie.modelFlow.value = Model(count = 42)

        assertEquals(Model(count = 42), vie.model)
    }
}
