package org.kodein.arch.vie

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal data class KeyModel(val value: Int = 0) : VIE.Model

internal sealed interface KeyIntent : VIE.Intent

internal sealed interface KeySignal : VIE.Signal

internal class FirstVM : VIEViewModel<KeyModel, KeyIntent, KeySignal>(KeyModel(), emptyList()) {
    override suspend fun process(intent: KeyIntent) = Unit
}

internal class SecondVM : VIEViewModel<KeyModel, KeyIntent, KeySignal>(KeyModel(), emptyList()) {
    override suspend fun process(intent: KeyIntent) = Unit
}

/** Compose runtime only: an applier with no nodes, and a frame clock the test drives. */
private class NoApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class Host(private val test: TestScope, extras: CreationExtras = CreationExtras.Empty) {
    val store = ViewModelStore()
    val created = mutableListOf<VIEViewModel<KeyModel, KeyIntent, KeySignal>>()
    private val owner = object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = store
        override val defaultViewModelProviderFactory = object : ViewModelProvider.Factory {}
        override val defaultViewModelCreationExtras = extras
    }
    private val clock = BroadcastFrameClock()
    private val recomposer = Recomposer(test.coroutineContext + clock)
    private val composition = Composition(NoApplier(), recomposer)
    private var tick by mutableIntStateOf(0)

    fun <VM : VIEViewModel<KeyModel, KeyIntent, KeySignal>> track(vm: VM): VM = vm.also { created += it }

    fun setContent(content: @Composable () -> Unit) {
        test.launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        composition.setContent {
            tick // read, so that recompose() has something to invalidate
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content)
        }
    }

    fun recompose() {
        tick++
        Snapshot.sendApplyNotifications()
        clock.sendFrame(0L)
        test.advanceUntilIdle()
    }

    fun close() {
        composition.dispose()
        recomposer.cancel()
        store.clear()
    }
}

internal class VIEViewKeyTest : VIETest() {

    @Test
    fun unkeyedViewsOfTwoClassesEachGetTheirOwnViewModel() = hostedTest { host ->
        host.setContent {
            VIEView(newVM = { host.track(FirstVM()) }) { }
            VIEView(newVM = { host.track(SecondVM()) }) { }
        }
        assertEquals(listOf(FirstVM::class, SecondVM::class), host.created.map { it::class })
        assertEquals(2, host.store.keys().size)
    }

    @Test
    fun unkeyedViewsOfOneClassShareOneViewModel() = hostedTest { host ->
        host.setContent {
            VIEView(newVM = { host.track(FirstVM()) }) { }
            VIEView(newVM = { host.track(FirstVM()) }) { }
        }
        assertEquals(1, host.created.size)
    }

    @Test
    fun theSameKeyOnTwoClassesClearsNeitherViewModel() = hostedTest { host ->
        host.setContent {
            VIEView(newVM = { host.track(FirstVM()) }, key = "k") { }
            VIEView(newVM = { host.track(SecondVM()) }, key = "k") { }
        }
        repeat(3) { host.recompose() }
        assertEquals(2, host.created.size)
        assertTrue(host.created.all { it.viewModelScope.isActive })
    }

    @Test
    fun theSameClassUnderTwoKeysGetsTwoViewModels() = hostedTest { host ->
        host.setContent {
            VIEView(newVM = { host.track(FirstVM()) }, key = "a") { }
            VIEView(newVM = { host.track(FirstVM()) }, key = "b") { }
        }
        assertEquals(2, host.created.size)
    }

    @Test
    fun aViewKeepsItsViewModelAcrossRecompositions() = hostedTest { host ->
        host.setContent { VIEView(newVM = { host.track(FirstVM()) }) { } }
        repeat(3) { host.recompose() }
        assertEquals(1, host.created.size)
    }

    @Test
    fun theStoreKeyIsTheSimpleNameAndTheKey() = hostedTest { host ->
        host.setContent { VIEView(newVM = { host.track(FirstVM()) }, key = "k") { } }
        assertEquals(setOf("FirstVM:k"), host.store.keys())
    }

    @Test
    fun aLambdaTypedAsTheSupertypeIsFiledUnderTheSupertype() = hostedTest { host ->
        host.setContent {
            VIEView(newVM = { host.track(FirstVM()) as VIEViewModel<KeyModel, KeyIntent, KeySignal> }, key = "k") { }
        }
        assertEquals(setOf("VIEViewModel:k"), host.store.keys())
    }

    @Test
    fun theFactoryLambdaReceivesTheOwnersCreationExtras() = hostedTest(
        extras = MutableCreationExtras().apply { set(ExtraKey, "from the owner") },
    ) { host ->
        var received: String? = null
        host.setContent { VIEView(newVM = { received = this[ExtraKey]; host.track(FirstVM()) }) { } }
        assertEquals("from the owner", received)
    }
}

private val ExtraKey = object : CreationExtras.Key<String> {}
