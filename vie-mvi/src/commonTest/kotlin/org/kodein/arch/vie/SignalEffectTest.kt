package org.kodein.arch.vie

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

private data object Ping : KeySignal, VIE.Signal.Event

@OptIn(ExperimentalCoroutinesApi::class)
internal class SignalEffectTest : VIETest() {

    private class Scope : VIEViewScope<KeyIntent, KeySignal> {
        override fun post(intent: KeyIntent) = Unit
        override val signalFlow = MutableSharedFlow<KeySignal>(extraBufferCapacity = 1)
    }

    @Test
    fun aSignalReachesTheLambdaOfTheLatestComposition() = hostedTest { host ->
        val scope = Scope()
        val received = mutableListOf<String>()
        var name by mutableStateOf("first")
        host.setContent {
            val current = name
            scope.SignalEffect { received += current }
        }
        advanceUntilIdle()

        name = "second"
        host.recompose()
        scope.signalFlow.emit(Ping)
        advanceUntilIdle()

        assertEquals(listOf("second"), received)
    }
}
