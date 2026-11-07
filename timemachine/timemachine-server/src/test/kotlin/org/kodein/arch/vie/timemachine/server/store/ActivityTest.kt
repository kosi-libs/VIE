package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession


class ActivityTest {

    private val c1 = ConnectionId(1)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()
    private val other = Uuid.random()

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
    fun rowsLandInTheBucketOfTheirDisplayTime() {
        val (state) = connected().after(
            receive(c1, observed(vm, 0, at = base)),
            receive(c1, observed(vm, 1, at = base + 1.seconds)),
            receive(c1, observed(vm, 2, at = base - 4.seconds)),
        )

        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 1, 0, 2), state.sparkline(base + 2.seconds))
    }

    @Test
    fun theBucketsShiftOnlyAtA3SecondBoundary() {
        val (state) = connected().after(receive(c1, observed(vm, 0, at = base)))

        assertEquals(last(1), state.sparkline(base))
        assertEquals(last(1), state.sparkline(base + 2.seconds))
        assertEquals(last(1, 0), state.sparkline(base + 3.seconds))
    }

    @Test
    fun aRowInTheLastBucketOnlyGrowsTheLastBar() {
        val before = connected().after(receive(c1, observed(vm, 0, at = base - 6.seconds)), receive(c1, observed(vm, 1, at = base))).state
        val after = before.after(receive(c1, observed(vm, 2, at = base + 2.seconds))).state

        assertEquals(last(1, 0, 1), before.sparkline(base + 1.seconds))
        assertEquals(last(1, 0, 2), after.sparkline(base + 2.seconds))
    }

    @Test
    fun aRowOlderThanThe30SecondsWindowIsNotShown() {
        val (state) = connected().after(receive(c1, observed(vm, 0, at = base)))

        assertEquals(listOf(1) + List(9) { 0 }, state.sparkline(base + 27.seconds))
        assertEquals(last(), state.sparkline(base + 30.seconds))
    }

    @Test
    fun aRowOlderThanTheOldestBucketIsNotCounted() {
        val (state) = connected().after(
            receive(c1, observed(vm, 0, at = base + 60.seconds)),
            receive(c1, observed(vm, 1, at = base)),
        )

        assertEquals(last(1), state.sparkline(base + 61.seconds))
        assertEquals(2, state.vm(session.id, vm).rowCount)
    }

    @Test
    fun aRowAheadOfNowCountsInTheLastBucket() {
        val (state) = connected().after(
            receive(c1, observed(vm, 0, at = base)),
            receive(c1, observed(vm, 1, at = base + 12.seconds)),
        )

        assertEquals(last(2), state.sparkline(base + 1.seconds))
    }

    @Test
    fun theCountsSurviveEviction() {
        val (state) = connected(Limits(maxRows = 2)).after(*Array(5) { receive(c1, observed(vm, it.toLong(), at = base)) })

        assertEquals(2, state.vm(session.id, vm).rows.size)
        assertEquals(last(5), state.sparkline(base))
    }

    @Test
    fun aRowOverMaxCharsIsCounted() {
        val (state) = connected(Limits(maxChars = 50)).after(receive(c1, observed(vm, 0, at = base), length = 100))

        assertEquals(emptyList(), state.vm(session.id, vm).rows)
        assertEquals(1, state.vm(session.id, vm).evictedCount)
        assertEquals(last(1), state.sparkline(base))
    }

    @Test
    fun aSessionsSparklineIsTheSumOfItsViewModels() {
        val (state) = connected().after(
            receive(c1, observed(vm, 0, at = base)),
            receive(c1, observed(other, 1, at = base - 3.seconds)),
            receive(c1, observed(other, 2, at = base)),
        )

        assertEquals(last(1, 2), state.session(session.id).sparkline(base))
        assertEquals(last(), SessionRecord(session, state.session(session.id).protocolVersion, null, state.session(session.id).connections).sparkline(base))
    }
}
