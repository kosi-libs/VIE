package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals


class CompactTest {

    @Test
    fun `the layout is compact under 1200 dp, and not at it`() {
        assertEquals(1200.dp, CompactBelow)
        assertEquals(true, isCompact(1000.dp), "the design's compact frame")
        assertEquals(true, isCompact(1199.99.dp))
        assertEquals(false, isCompact(1200.dp))
        assertEquals(false, isCompact(1440.dp), "the design's full frame")
    }
}
