package org.kodein.arch.vie.timemachine.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource


class OutageTest {

    private val time = TestTimeSource()
    private val printed = mutableListOf<String>()

    private val unreachable = Attempt.Unreachable(IllegalStateException("nothing listens"))
    private val closed = Attempt.Dropped(held = false, cause = null)

    private fun outage(quietAfter: Duration = 1.minutes) = Outage(
        since = time.markNow(),
        address = "ws://inspector",
        firstRetryDelay = 20.milliseconds,
        maxRetryDelay = 100.milliseconds,
        quietAfter = quietAfter,
        quietRetryDelay = 30.seconds,
        notice = { printed += it },
    )

    @Test
    fun theDelayDoublesUpToItsCapAndStaysThere() {
        val outage = outage()
        val delays = List(5) { outage.failed(unreachable) }
        assertEquals(listOf(20, 40, 80, 100, 100).map { it.milliseconds }, delays)
    }

    @Test
    fun theDelayStaysAtItsCapHoweverManyFailures() {
        val outage = outage()
        // Past 31 and 64 failures, where a shift would give a negative delay, then the first one again.
        val delays = List(70) { outage.failed(unreachable) }
        assertEquals(setOf(100.milliseconds), delays.drop(3).toSet())
    }

    @Test
    fun aProblemIsPrintedOnceForAsLongAsItRepeats() {
        val outage = outage()
        repeat(3) { outage.failed(closed) }
        outage.failed(Attempt.Refused("the inspector said no"))
        outage.failed(closed)
        assertEquals(
            listOf(
                "TimeMachine: the inspector at ws://inspector closed the connection; retrying.",
                "TimeMachine: the inspector said no; retrying.",
                "TimeMachine: the inspector at ws://inspector closed the connection; retrying.",
            ),
            printed,
        )
    }

    @Test
    fun eachEndIsPrintedAsWhatHappened() {
        val cause = IllegalStateException("boom")
        val outage = outage()
        outage.failed(Attempt.Unreachable(cause))
        outage.failed(Attempt.Refused("the inspector said no"))
        outage.failed(Attempt.Dropped(held = false, cause = null))
        outage.failed(Attempt.Dropped(held = true, cause = cause))
        assertEquals(
            listOf(
                "TimeMachine: cannot reach the inspector at ws://inspector ($cause); retrying.",
                "TimeMachine: the inspector said no; retrying.",
                "TimeMachine: the inspector at ws://inspector closed the connection; retrying.",
                "TimeMachine: lost the connection to the inspector at ws://inspector ($cause); retrying.",
            ),
            printed,
        )
    }

    @Test
    fun anOutageThatLastsIsRetriedLessOftenAndSaysSoOnce() {
        val outage = outage(quietAfter = 1.minutes)
        // Enough failures for the backoff to be far past either cap.
        val before = List(12) { outage.failed(unreachable) }
        assertEquals(100.milliseconds, before.last())
        assertTrue(printed.none { "retrying every" in it }, printed.toString())

        time += 1.minutes
        val after = List(3) { outage.failed(unreachable) }
        assertEquals(setOf(30.seconds), after.toSet())
        assertEquals(
            listOf("TimeMachine: no connection to the inspector at ws://inspector has held for 1m; retrying every 30s from now on."),
            printed.filter { "retrying every" in it },
        )
    }

    @Test
    fun onlyADroppedConnectionCanHaveHeld() {
        assertEquals(
            listOf(false, false, false, true),
            listOf(unreachable, Attempt.Refused("no"), closed, Attempt.Dropped(held = true, cause = null)).map { it.held },
        )
    }
}
