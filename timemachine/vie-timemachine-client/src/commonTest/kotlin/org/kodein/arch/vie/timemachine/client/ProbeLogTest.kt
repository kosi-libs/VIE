package org.kodein.arch.vie.timemachine.client

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


class ProbeLogTest {

    private val said = mutableListOf<String>()
    private val log = ProbeLog { said += it }

    @Test
    fun aNoticeIsHandedToTheLoggerAsItIs() {
        log.notice("TimeMachine: first")
        log.notice("second\nline")
        assertEquals(listOf("TimeMachine: first", "second\nline"), said)
    }

    @Test
    fun aLoggerThatThrowsNeverThrowsIntoTheProbe() {
        val failures = listOf(IllegalStateException("exception"), Error("error"), CancellationException("cancellation"))
        for (failure in failures) {
            // Returning is the assertion: the message is then printed with println, which a test cannot read.
            ProbeLog { throw failure }.notice("TimeMachine: said anyway")
        }
    }

    @Test
    fun aGuardedFailureIsSaidToTheLogger() {
        assertNull(log.guarded({ "do this" }) { error("boom") })
        assertNull(log.describing({ "describe that" }) { throw Error("too deep") })

        assertEquals(2, said.size, said.toString())
        assertTrue(said[0].startsWith("TimeMachine: failed to do this: ") && "boom" in said[0], said[0])
        assertTrue(said[1].startsWith("TimeMachine: failed to describe that: ") && "too deep" in said[1], said[1])
    }

    @Test
    fun aGuardThatDoesNotFailSaysNothing() {
        assertEquals(1, log.guarded({ "do this" }) { 1 })
        assertEquals(2, log.describing({ "describe that" }) { 2 })
        assertEquals(emptyList<String>(), said)
    }
}
