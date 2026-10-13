package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind


class TimelineTest {

    private val c1 = ConnectionId(1)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()
    private val other = Uuid.random()

    private fun connected(limits: Limits = Limits()) = TimeMachineState(limits = limits).on(hello(c1, session)).state

    private fun TimeMachineState.span() = vm(session.id, vm).aliveSpan(session(session.id).sequences)

    private fun TimeMachineState.historyUnavailable() = vm(session.id, vm).historyUnavailable(session(session.id).sequences)

    private fun TimeMachineState.timeline() = timeline(session.id, vm)

    private fun List<TimelineEntry>.shape(): List<Any> = map {
        when (it) {
            is TimelineEntry.Message -> it.row.sequence.value
            else -> it
        }
    }

    // Alive span

    @Test
    fun theAliveSpanRunsFromTheStartToTheClose() {
        val (state) = connected().after(receive(c1, observed(other, 0)), receive(c1, started(vm, 1)), receive(c1, closed(vm, 4)))

        assertEquals(seqs(1, 4), state.span())
    }

    @Test
    fun theAliveSpanStartsAtTheFloorWhenTheStartWasNeverSeen() {
        val (state) = connected().after(receive(c1, observed(other, 3)), receive(c1, observed(vm, 5)))

        assertEquals(seq(3)..seq(Long.MAX_VALUE), state.span())
    }

    @Test
    fun anUnknownAnswerEndsTheAliveSpanAtTheInferredEndOrTheLastRowWhenLater() {
        val inferred = connected().after(
            receive(c1, started(vm, 0)),
            receive(c1, observed(other, 2)),
            receive(c1, vmInfo(vm, known = false)),
        ).state
        assertEquals(seqs(0, 2), inferred.span())

        // A row of its own still in flight when the probe answered.
        val later = inferred.after(receive(c1, observed(vm, 1)), receive(c1, observed(vm, 5))).state
        assertEquals(seqs(0, 5), later.span())
    }

    @Test
    fun aCloseArrivingAfterAnUnknownAnswerReplacesTheInferredEnd() {
        val (state) = connected().after(
            receive(c1, started(vm, 0)),
            receive(c1, observed(other, 2)),
            receive(c1, observed(other, 5)),
            receive(c1, vmInfo(vm, known = false)),
            receive(c1, closed(vm, 1)),
        )

        assertEquals(seqs(0, 1), state.span())
    }

    // History unavailable

    @Test
    fun aSeenStartMakesTheHistoryAvailable() {
        val (state) = connected().after(receive(c1, observed(other, 0)), receive(c1, started(vm, 7)))

        assertFalse(state.historyUnavailable())
    }

    @Test
    fun theHistoryIsUnavailableWhenTheSessionWasNotSeenFromItsStart() {
        val (state) = connected().after(receive(c1, observed(vm, 4)))

        assertTrue(state.historyUnavailable())
        assertEquals(TimelineEntry.HistoryUnavailable, state.timeline().first())
    }

    @Test
    fun theHistoryIsUnavailableForAViewModelOnlyReAnnounced() {
        val (state) = connected().after(receive(c1, observed(other, 0)), receive(c1, vmInfo(vm)))

        assertTrue(state.historyUnavailable())
    }

    @Test
    fun inASessionSeenFromZeroADroppedStartLeavesTheHistoryAvailable() {
        val (state) = connected().after(receive(c1, dropped(vm, 0)), receive(c1, observed(vm, 1)))

        assertFalse(state.historyUnavailable())
    }

    @Test
    fun evictingTheStartRowDoesNotMakeTheHistoryUnavailable() {
        val (state) = connected(Limits(maxRows = 2)).after(
            receive(c1, started(vm, 0)),
            receive(c1, observed(vm, 1)),
            receive(c1, observed(vm, 2)),
        )

        assertFalse(state.historyUnavailable())
        assertEquals(listOf(TimelineEntry.Evicted(1), 1L, 2L), state.timeline().shape())
    }

    // Bounds

    @Test
    fun theOldestRowsGoFirstPastTheRowBound() {
        val (state) = connected(Limits(maxRows = 3)).after(*Array(5) { receive(c1, observed(vm, it.toLong())) })

        val record = state.vm(session.id, vm)
        assertEquals(listOf(2L, 3L, 4L), record.rows.map { it.sequence.value })
        assertEquals(2, record.evictedCount)
        assertEquals(5, record.rowCount)
    }

    @Test
    fun theOldestRowsGoFirstPastTheCharacterBound() {
        val (state) = connected(Limits(maxChars = 25)).after(
            receive(c1, observed(vm, 0), length = 10),
            receive(c1, observed(vm, 1), length = 10),
            receive(c1, observed(vm, 2), length = 10),
        )

        val record = state.vm(session.id, vm)
        assertEquals(listOf(1L, 2L), record.rows.map { it.sequence.value })
        assertEquals(20L, record.retainedChars)
        assertEquals(1, record.evictedCount)
    }

    @Test
    fun aRowHeavierThanTheCharacterBoundIsCountedAsEvictedWithoutBeingPlaced() {
        val (state) = connected(Limits(maxChars = 25)).after(
            receive(c1, observed(vm, 0), length = 10),
            receive(c1, observed(vm, 1), length = 10),
            receive(c1, observed(vm, 2), length = 30),
        )

        val record = state.vm(session.id, vm)
        assertEquals(listOf(0L, 1L), record.rows.map { it.sequence.value })
        assertEquals(20L, record.retainedChars)
        assertEquals(1, record.evictedCount)
        assertEquals(3, record.rowCount)
        assertEquals(seq(2), record.lastActivity?.sequence)
    }

    @Test
    fun aLateRowOlderThanAFullTimelinesOldestIsCountedAsEvictedWithoutBeingPlaced() {
        val (state) = connected(Limits(maxRows = 2)).after(
            receive(c1, observed(vm, 0)),
            receive(c1, observed(vm, 5)),
            receive(c1, observed(vm, 6)),
            receive(c1, observed(vm, 1)),
        )

        val record = state.vm(session.id, vm)
        assertEquals(listOf(5L, 6L), record.rows.map { it.sequence.value })
        assertEquals(2, record.evictedCount)
    }

    @Test
    fun aLateRowInANotFullTimelineIsPlacedBySequence() {
        val (state) = connected().after(receive(c1, observed(vm, 0)), receive(c1, observed(vm, 4)), receive(c1, observed(vm, 2)))

        assertEquals(listOf(0L, 2L, 4L), state.sequencesOf(session.id, vm))
    }

    @Test
    fun whatSurvivesEvictionIsReplacedOnlyByANewerSequence() {
        val json = { n: Int -> JsonPrimitive(n) }
        val (state) = connected(Limits(maxRows = 1)).after(
            receive(c1, observed(vm, 0, VIEObjectKind.Intent, "I", json(0), at = at(0))),
            receive(c1, observed(vm, 2, VIEObjectKind.Model, "M", json(2), at = at(2))),
            receive(c1, observed(vm, 3, VIEObjectKind.Signal, "S", at = at(3))),
            receive(c1, observed(vm, 1, VIEObjectKind.Model, "M", json(1), at = at(1))), // late
        )

        val record = state.vm(session.id, vm)
        assertEquals(listOf(3L), record.rows.map { it.sequence.value })
        assertEquals(
            mapOf(TypeKey(VIEObjectKind.Intent, "I") to Latest(seq(0), json(0)), TypeKey(VIEObjectKind.Model, "M") to Latest(seq(2), json(2))),
            record.latestJson,
        )
        assertEquals(seq(2), record.currentModel?.sequence)
        assertEquals(Latest(seq(3), at(3)), record.lastActivity)
        assertEquals(4, record.rowCount)
    }

    // Projection

    @Test
    fun theTimelineShowsTheMarkersOnTopThenTheRowsWithTheDropReports() {
        val (state) = connected(Limits(maxRows = 2)).after(
            receive(c1, observed(vm, 3)),
            receive(c1, dropped(vm, 4)),
            receive(c1, observed(vm, 5)),
        )

        assertEquals(listOf(TimelineEntry.HistoryUnavailable, TimelineEntry.Evicted(1), 4L, 5L), state.timeline().shape())
    }

    @Test
    fun aMissingRangeWhileTheViewModelWasAliveIsShownInPosition() {
        val (state) = connected().after(
            receive(c1, started(vm, 0)),
            receive(c1, observed(vm, 1)),
            receive(c1, observed(vm, 5)), // 2..4 missing
            receive(c1, observed(other, 9)), // 6..8 missing
        )

        assertEquals(
            listOf(0L, 1L, TimelineEntry.PossiblyIncomplete(seqs(2, 4)), 5L, TimelineEntry.PossiblyIncomplete(seqs(6, 8))),
            state.timeline().shape(),
        )
    }

    @Test
    fun aMissingRangeOutsideTheAliveSpanIsNotShown() {
        val (state) = connected().after(
            receive(c1, observed(other, 0)),
            receive(c1, observed(other, 3)), // 1..2 missing, before the start
            receive(c1, started(vm, 4)),
            receive(c1, closed(vm, 5)),
            receive(c1, observed(other, 8)), // 6..7 missing, after the close
        )

        assertEquals(listOf(4L, 5L), state.timeline().shape())
    }

    @Test
    fun aMissingRangeBelowTheOldestRetainedRowIsNotShown() {
        val (state) = connected(Limits(maxRows = 1)).after(
            receive(c1, observed(vm, 0)),
            receive(c1, observed(vm, 3)), // 1..2 missing
            receive(c1, observed(vm, 4)),
        )

        assertEquals(listOf(TimelineEntry.Evicted(2), 4L), state.timeline().shape())
    }

    @Test
    fun aHealedRangeIsNoLongerShown() {
        val (state) = connected().after(
            receive(c1, started(vm, 0)),
            receive(c1, observed(vm, 2)),
            receive(c1, observed(vm, 1)),
        )

        assertEquals(listOf(0L, 1L, 2L), state.timeline().shape())
    }
}
