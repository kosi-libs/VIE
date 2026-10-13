package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind


class SessionTimelineTest {

    private val c1 = ConnectionId(1)
    private val c2 = ConnectionId(2)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val a = Uuid.random()
    private val b = Uuid.random()

    private fun connected(limits: Limits = Limits()) = TimeMachineState(limits = limits).on(hello(c1, session)).state

    private fun TimeMachineState.shape(): List<Any> = sessionTimeline(session.id).map {
        when (it) {
            is SessionTimelineEntry.Scoped -> (it.entry as? TimelineEntry.Message)?.row?.sequence?.value ?: (it.viewModelId to it.entry)
            is SessionTimelineEntry.Shared -> it.entry
            is SessionTimelineEntry.Divider -> it.kind
        }
    }

    @Test
    fun anUnknownSessionHasNoTimeline() {
        assertEquals(emptyList(), TimeMachineState().sessionTimeline(session.id))
    }

    @Test
    fun everyViewModelIsMergedBySequenceClosedOnesIncluded() {
        val (state) = connected().after(
            receive(c1, started(a, 0)),
            receive(c1, started(b, 1)),
            receive(c1, observed(a, 2)),
            receive(c1, closed(b, 4)),
            receive(c1, observed(a, 3)),
        )

        assertEquals(listOf<Any>(SessionTimelineEntry.Divider.Kind.Connected, 0L, 1L, 2L, 3L, 4L), state.shape())
        assertEquals(setOf(a, b), state.sessionTimeline(session.id).filterIsInstance<SessionTimelineEntry.Scoped>().map { it.viewModelId }.toSet())
    }

    @Test
    fun aRangeBothViewModelsWereAliveForIsMarkedOnce() {
        val (state) = connected().after(
            receive(c1, started(a, 0)),
            receive(c1, started(b, 1)),
            receive(c1, observed(a, 2)),
            receive(c1, observed(b, 6)),
        )

        val ranges = state.sessionTimeline(session.id).filterIsInstance<SessionTimelineEntry.Shared>()
        assertEquals(1, ranges.size)
        assertEquals(seqs(3, 5), ranges.single().entry.range)
        // Each ViewModel's own timeline has it: the merge drops the duplicate.
        assertEquals(1, state.timeline(session.id, a).count { it is TimelineEntry.PossiblyIncomplete })
        assertEquals(1, state.timeline(session.id, b).count { it is TimelineEntry.PossiblyIncomplete })
        assertEquals(listOf<Any>(SessionTimelineEntry.Divider.Kind.Connected, 0L, 1L, 2L, ranges.single().entry, 6L), state.shape())
    }

    @Test
    fun topMarkersAreOnePerAffectedViewModelAndNameIt() {
        val (state) = connected(Limits(maxRows = 1)).after(
            receive(c1, observed(a, 5)),
            receive(c1, observed(b, 6)),
            receive(c1, observed(b, 7)),
        )

        val top = state.sessionTimeline(session.id).takeWhile { it is SessionTimelineEntry.Scoped && (it.entry !is TimelineEntry.Message) }
        assertEquals(
            listOf<Any>(
                SessionTimelineEntry.Scoped(a, TimelineEntry.HistoryUnavailable),
                SessionTimelineEntry.Scoped(b, TimelineEntry.HistoryUnavailable),
                SessionTimelineEntry.Scoped(b, TimelineEntry.Evicted(1)),
            ),
            top,
        )
    }

    @Test
    fun aSingleConnectionHasOneConnectedDividerAtTheTop() {
        val (state) = connected().after(receive(c1, started(a, 0)), receive(c1, observed(a, 1)))

        assertEquals(listOf<Any>(SessionTimelineEntry.Divider.Kind.Connected, 0L, 1L), state.shape())
        assertNull((state.sessionTimeline(session.id).first() as SessionTimelineEntry.Divider).after)
    }

    @Test
    fun aDisconnectAndAReconnectAreDividedRightAfterTheirRow() {
        val (state) = connected().after(
            receive(c1, started(a, 0)),
            receive(c1, observed(a, 1)),
            TimeMachineEvent.Disconnected(c1, at(5)),
            hello(c2, session, now = at(9)),
            receive(c2, observed(a, 2)),
        )

        assertEquals(
            listOf<Any>(
                SessionTimelineEntry.Divider.Kind.Connected, 0L, 1L,
                SessionTimelineEntry.Divider.Kind.Disconnected,
                SessionTimelineEntry.Divider.Kind.Reconnected,
                2L,
            ),
            state.shape(),
        )
        val dividers = state.sessionTimeline(session.id).filterIsInstance<SessionTimelineEntry.Divider>()
        assertEquals(listOf(null, seq(1), seq(1)), dividers.map { it.after })
        assertEquals(listOf(t0, at(5), at(9)), dividers.map { it.at })
    }

    @Test
    fun aDividerWhoseRowIsGoneStillSortsByItsSequence() {
        val (state) = connected().after(
            receive(c1, observed(a, 0)),
            receive(c1, observed(a, 1)),
            hello(c2, session, now = at(2)),
            receive(c2, observed(a, 2)),
        )

        assertEquals(listOf<Any>(SessionTimelineEntry.Divider.Kind.Connected, 0L, 1L, SessionTimelineEntry.Divider.Kind.Reconnected, 2L), state.shape())
    }

    @Test
    fun theEntryOfADividerIsNull() {
        assertNull(SessionTimelineEntry.Divider(SessionTimelineEntry.Divider.Kind.Connected, t0, null).timelineEntry)
    }

    @Test
    fun theSelectedPreviousModelIsTheModelObservedBeforeIt() {
        val (state) = connected().after(
            receive(c1, observed(a, 0, VIEObjectKind.Model)),
            receive(c1, observed(a, 1, VIEObjectKind.Intent)),
            receive(c1, observed(a, 2, VIEObjectKind.Model)),
            receive(c1, observed(a, 3, VIEObjectKind.Signal)),
            receive(c1, observed(a, 4, VIEObjectKind.Model)),
        )
        val vm = state.vm(session.id, a)

        assertEquals(seq(2), vm.previousModel(seq(4))?.sequence)
        assertEquals(seq(0), vm.previousModel(seq(2))?.sequence)
        assertNull(vm.previousModel(seq(0)))
    }

    @Test
    fun thereIsNoPreviousModelOnceItWasEvicted() {
        val (state) = connected(Limits(maxRows = 1)).after(
            receive(c1, observed(a, 0, VIEObjectKind.Model)),
            receive(c1, observed(a, 1, VIEObjectKind.Model)),
        )

        assertNull(state.vm(session.id, a).previousModel(seq(1)))
    }
}
