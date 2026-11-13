package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession


class ActivityTest {

    private val c1 = ConnectionId(1)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()

    /** The start of a 3 s bucket: `t0` is a multiple of 3 s. */
    private val base = t0

    private fun connected(limits: Limits = Limits()) = TimeMachineState(limits = limits).on(hello(c1, session)).state

    private fun TimeMachineState.sparkline(now: Instant) = vm(session.id, vm).sparkline(now)

    /** Ten buckets, the newest ones given. */
    private fun last(vararg counts: Int) = List(10 - counts.size) { 0 } + counts.toList()

    @Test
    fun aViewModelWithNoRowHasNoActivity() {
        assertEquals(last(), ViewModelRecord(vm, null, t0).sparkline(t0))
    }

    @Test
    fun rowsLandInTheBucketOfTheirReceptionTime() {
        val (state) = connected().after(
            receive(c1, observed(vm, 0), now = base),
            receive(c1, observed(vm, 1), now = base + 1.seconds),
            receive(c1, observed(vm, 2), now = base - 4.seconds),
        )

        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 1, 0, 2), state.sparkline(base + 2.seconds))
    }

    @Test
    fun theBucketsShiftOnlyAtA3SecondBoundary() {
        val (state) = connected().after(receive(c1, observed(vm, 0), now = base))

        assertEquals(last(1), state.sparkline(base))
        assertEquals(last(1), state.sparkline(base + 2.seconds))
        assertEquals(last(1, 0), state.sparkline(base + 3.seconds))
    }

    @Test
    fun aRowInTheLastBucketOnlyGrowsTheLastBar() {
        val before = connected().after(receive(c1, observed(vm, 0), now = base - 6.seconds), receive(c1, observed(vm, 1), now = base)).state
        val after = before.after(receive(c1, observed(vm, 2), now = base + 2.seconds)).state

        assertEquals(last(1, 0, 1), before.sparkline(base + 1.seconds))
        assertEquals(last(1, 0, 2), after.sparkline(base + 2.seconds))
    }

    @Test
    fun aRowOlderThanThe30SecondsWindowIsNotShown() {
        val (state) = connected().after(receive(c1, observed(vm, 0), now = base))

        assertEquals(listOf(1) + List(9) { 0 }, state.sparkline(base + 27.seconds))
        assertEquals(last(), state.sparkline(base + 30.seconds))
    }

    @Test
    fun aProbeClockFarBehindIsStillShown() {
        val (state) = connected().after(receive(c1, observed(vm, 0, at = base - 40.seconds), now = base))

        assertEquals(last(1), state.sparkline(base))
    }

    @Test
    fun aProbeClockSteppingBackIsStillCounted() {
        val (state) = connected().after(
            receive(c1, observed(vm, 0, at = base + 120.seconds), now = base),
            receive(c1, observed(vm, 1, at = base), now = base + 1.seconds),
            receive(c1, observed(vm, 2, at = base + 1.seconds), now = base + 1.seconds),
        )

        assertEquals(last(3), state.sparkline(base + 2.seconds))
    }

    @Test
    fun aProbeClockAheadKeepsTheShapeOfItsActivity() {
        val (state) = connected().after(
            receive(c1, observed(vm, 0, at = base + 1.hours - 6.seconds), now = base - 6.seconds),
            receive(c1, observed(vm, 1, at = base + 1.hours), now = base),
        )

        assertEquals(last(1, 0, 1), state.sparkline(base))
    }

    @Test
    fun aRowReceivedBeforeTheOldestBucketIsNotCounted() {
        // Only once the inspector's own clock stepped back.
        val (state) = connected().after(
            receive(c1, observed(vm, 0), now = base + 60.seconds),
            receive(c1, observed(vm, 1), now = base),
        )

        assertEquals(last(1), state.sparkline(base + 61.seconds))
        assertEquals(2, state.vm(session.id, vm).rowCount)
    }

    @Test
    fun aRowCountedAfterTheNowReadCountsInTheLastBucket() {
        val (state) = connected().after(
            receive(c1, observed(vm, 0), now = base),
            receive(c1, observed(vm, 1), now = base + 12.seconds),
        )

        assertEquals(last(2), state.sparkline(base + 1.seconds))
    }

    @Test
    fun rowsCountedYearsAfterTheNowReadAreAllInTheLastBucket() {
        val ahead = base + (10 * 365).days
        val (state) = connected().after(
            receive(c1, observed(vm, 0), now = ahead - 6.seconds),
            receive(c1, observed(vm, 1), now = ahead),
        )

        assertEquals(last(2), state.sparkline(base))
    }

    @Test
    fun theCountsSurviveEviction() {
        val (state) = connected(Limits(maxRows = 2)).after(*Array(5) { receive(c1, observed(vm, it.toLong()), now = base) })

        assertEquals(2, state.vm(session.id, vm).rows.size)
        assertEquals(last(5), state.sparkline(base))
    }

    @Test
    fun aRowOverMaxCharsIsCounted() {
        val (state) = connected(Limits(maxChars = 50)).after(receive(c1, observed(vm, 0), length = 100, now = base))

        assertEquals(emptyList(), state.vm(session.id, vm).rows)
        assertEquals(1, state.vm(session.id, vm).evictedCount)
        assertEquals(last(1), state.sparkline(base))
    }
}
