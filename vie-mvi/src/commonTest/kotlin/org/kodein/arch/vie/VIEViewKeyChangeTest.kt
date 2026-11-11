package org.kodein.arch.vie

import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

private data object Pong : KeySignal, VIE.Signal.Event

/** A ViewModel told apart by its first model. */
internal class ValueVM(value: Int) : VIEViewModel<KeyModel, KeyIntent, KeySignal>(KeyModel(value), emptyList()) {
    override suspend fun process(intent: KeyIntent) = Unit
}

/** What happens to the content of one [VIEView] call site when its key changes, and when it does not. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VIEViewKeyChangeTest : VIETest() {

    @Test
    fun theContentIsNeverComposedWithTheModelOfThePreviousViewModel() = hostedTest { host ->
        var key by mutableStateOf("a")
        val composed = mutableListOf<Pair<VIEViewScope<KeyIntent, KeySignal>, KeyModel>>()
        host.setContent {
            val current = key
            VIEView(newVM = { host.track(ValueVM(if (current == "a") 1 else 2)) }, key = current) { model ->
                composed += this to model
            }
        }
        advanceUntilIdle()

        key = "b"
        host.recompose()

        val (a, b) = host.created
        assertEquals(listOf(a to KeyModel(1), b to KeyModel(2)), composed.distinct())
    }

    @Test
    fun aKeyChangeDropsWhatTheContentRemembered() = hostedTest { host ->
        var key by mutableStateOf("a")
        val remembered = mutableListOf<Any>()
        host.setContent {
            val current = key
            VIEView(newVM = { host.track(ValueVM(0)) }, key = current) {
                remembered += remember { Any() }
            }
        }
        advanceUntilIdle()

        key = "b"
        host.recompose()

        assertNotSame(remembered.first(), remembered.last())
    }

    @Test
    fun aKeyChangeMovesTheEffectsOfTheContentToTheNewViewModel() = hostedTest { host ->
        var key by mutableStateOf("a")
        val received = mutableListOf<KeySignal>()
        host.setContent {
            val current = key
            VIEView(newVM = { host.track(ValueVM(0)) }, key = current) {
                SignalEffect { received += it }
            }
        }
        advanceUntilIdle()

        key = "b"
        host.recompose()
        host.created.last().signal(Pong)
        advanceUntilIdle()

        assertEquals(2, host.created.size)
        assertEquals(listOf<KeySignal>(Pong), received)
    }

    @Test
    fun theContentKeepsWhatItRememberedWhileTheKeyStays() = hostedTest { host ->
        var tick by mutableIntStateOf(0)
        val remembered = mutableListOf<Any>()
        host.setContent {
            tick // read, so that the content is composed again
            VIEView(newVM = { host.track(ValueVM(0)) }, key = "a") {
                remembered += remember { Any() }
            }
        }
        advanceUntilIdle()

        repeat(3) {
            tick++
            host.recompose()
        }

        assertTrue(remembered.size > 1, "The content was composed once only")
        assertEquals(1, remembered.distinct().size)
    }

    // `rememberSaveable` files its state under this hash: it must survive a ViewModel that is created again,
    // as it is after a process death. Keying the content by the ViewModel instance would break that.
    @Test
    fun theCompositeKeyOfTheContentDoesNotDependOnTheViewModelInstance() = hostedTest { host ->
        val other = Host(this)
        try {
            val hashes = mutableListOf<Any>()
            listOf(host, other).forEach {
                it.setContent {
                    VIEView(newVM = { it.track(ValueVM(0)) }, key = "a") {
                        hashes += currentCompositeKeyHashCode
                    }
                }
            }
            advanceUntilIdle()

            assertNotSame(host.created.single(), other.created.single())
            assertEquals(2, hashes.size)
            assertEquals(1, hashes.distinct().size)
        } finally {
            other.close()
        }
    }
}
