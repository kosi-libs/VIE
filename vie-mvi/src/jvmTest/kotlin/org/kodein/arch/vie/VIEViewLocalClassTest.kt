package org.kodein.arch.vie

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// JVM only: androidx rejects a local class by its missing canonical name, which other targets do not have.
internal class VIEViewLocalClassTest : VIETest() {

    @Test
    fun anUnkeyedLocalClassFailsFast() = hostedTest { host ->
        class LocalVM : VIEViewModel<KeyModel, KeyIntent, KeySignal>(KeyModel(), emptyList()) {
            override suspend fun process(intent: KeyIntent) = Unit
        }
        assertFailsWith<IllegalArgumentException> {
            host.setContent { VIEView(newVM = { LocalVM() }) { } }
        }
    }

    @Test
    fun aKeyedLocalClassWorks() = hostedTest { host ->
        class LocalVM : VIEViewModel<KeyModel, KeyIntent, KeySignal>(KeyModel(), emptyList()) {
            override suspend fun process(intent: KeyIntent) = Unit
        }
        host.setContent { VIEView(newVM = { LocalVM() }, key = "k") { } }
        assertEquals(setOf("LocalVM:k"), host.store.keys())
    }
}
