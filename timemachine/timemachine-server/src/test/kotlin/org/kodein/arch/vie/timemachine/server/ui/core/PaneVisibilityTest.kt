@file:OptIn(ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest


class PaneVisibilityTest {

    @Test
    fun `a key shown twice and hidden once is still shown`() = runTest {
        val visibility = PaneVisibility()
        val shown = mutableListOf<Boolean>()
        val job = backgroundScope.launch { visibility.shown("a").collect { shown += it } }
        runCurrent()
        visibility.show("a")
        visibility.show("a")
        visibility.hide("a")
        runCurrent()
        assertEquals(listOf(false, true), shown)

        visibility.hide("a")
        runCurrent()
        assertEquals(listOf(false, true, false), shown)
        job.cancel()
    }
}
