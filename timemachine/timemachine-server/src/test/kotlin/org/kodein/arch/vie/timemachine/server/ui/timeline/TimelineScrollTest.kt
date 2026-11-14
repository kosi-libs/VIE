package org.kodein.arch.vie.timemachine.server.ui.timeline

import kotlin.test.Test
import kotlin.test.assertEquals


class TimelineScrollTest {

    @Test
    fun `an item whole in the viewport does not move, even on its edges`() {
        assertEquals(0, revealDistance(offset = 50, size = 28, viewportStart = 0, viewportEnd = 200))
        assertEquals(0, revealDistance(offset = 0, size = 28, viewportStart = 0, viewportEnd = 200))
        assertEquals(0, revealDistance(offset = 172, size = 28, viewportStart = 0, viewportEnd = 200))
    }

    @Test
    fun `an item cut by the end moves by what is out of view`() {
        assertEquals(12, revealDistance(offset = 184, size = 28, viewportStart = 0, viewportEnd = 200))
    }

    @Test
    fun `an item cut by the start moves back by what is out of view`() {
        assertEquals(-25, revealDistance(offset = -25, size = 28, viewportStart = 0, viewportEnd = 200))
    }

    @Test
    fun `an item longer than the viewport stops with its start on the viewport's`() {
        // An expanded row: bringing its end into view would hide its first line.
        assertEquals(150, revealDistance(offset = 150, size = 400, viewportStart = 0, viewportEnd = 200))
    }

    @Test
    fun `the no-match item shifts the rows by one in the lazy list`() {
        assertEquals(3, lazyRowIndex(row = 3, noMatch = false))
        assertEquals(4, lazyRowIndex(row = 3, noMatch = true))
    }
}
