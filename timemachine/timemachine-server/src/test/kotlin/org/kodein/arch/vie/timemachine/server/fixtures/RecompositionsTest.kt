package org.kodein.arch.vie.timemachine.server.fixtures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


/** The counting of recompositions shares one tracer for the JVM: two counts at once would each see the other's marks. */
class RecompositionsTest {

    @Test
    fun `a count started while another runs is refused, and the one running is not disturbed`() {
        countingRecompositions("a.B") { outer ->
            assertFailsWith<IllegalStateException> { countingRecompositions("a.B") { } }
            // The refused count did not take the tracer down with it: the outer one still counts.
            outer.traceEventStart(0, 0, 0, "a.B (File.kt:1)")
            assertEquals(1, outer["a.B"])
        }
    }

    @Test
    fun `a count that ends, or fails, frees the tracer for the next`() {
        assertFailsWith<IllegalArgumentException> { countingRecompositions("a.B") { throw IllegalArgumentException() } }
        countingRecompositions("a.B") { }
    }
}
