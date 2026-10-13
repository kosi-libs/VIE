package org.kodein.arch.vie.timemachine.server.store

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import kotlin.time.Instant
import kotlin.uuid.Uuid


/** One entry of a session's timeline: every ViewModel's, merged. */
sealed interface SessionTimelineEntry {

    /** An entry of ViewModel [viewModelId]'s own timeline: a row, or one of its top markers. */
    data class Scoped(val viewModelId: Uuid, val entry: TimelineEntry) : SessionTimelineEntry

    /** A [TimelineEntry.PossiblyIncomplete] some ViewModels share: one per range, naming none. */
    data class Shared(val entry: TimelineEntry.PossiblyIncomplete) : SessionTimelineEntry

    /**
     * The session's connection changed at [at], right after the row at [after] (the top when `null`).
     */
    data class Divider(val kind: Kind, val at: Instant, val after: TimeMachineSequence?) : SessionTimelineEntry {
        enum class Kind { Connected, Reconnected, Disconnected }
    }
}

/** The timeline entry the filter looks at, `null` for a divider, which always passes. */
val SessionTimelineEntry.timelineEntry: TimelineEntry?
    get() = when (this) {
        is SessionTimelineEntry.Scoped -> entry
        is SessionTimelineEntry.Shared -> entry
        is SessionTimelineEntry.Divider -> null
    }

/**
 * The timeline of session [sessionId]: every ViewModel's, closed ones included, merged by sequence.
 *
 * At the top, one [TimelineEntry.HistoryUnavailable] and one [TimelineEntry.Evicted] per affected
 * ViewModel, each naming it: a summed count could not say whose history is incomplete. Then the rows,
 * a [TimelineEntry.PossiblyIncomplete] once per range whichever ViewModels it concerns, and a
 * divider per edge of [SessionRecord.connections]: right after the row whose sequence is the edge's
 * `after`, at the top of the rows when it is `null`. A span is "connected" for the first, "reconnected"
 * for each later one, and has a "disconnected" divider where it has that edge. Empty for an unknown session.
 */
fun TimeMachineState.sessionTimeline(sessionId: Uuid): List<SessionTimelineEntry> {
    val session = sessions[sessionId] ?: return emptyList()
    val timelines = session.viewModels.keys.associateWith { timeline(sessionId, it) }

    val markers = timelines.flatMap { (id, entries) ->
        entries.filter { it is TimelineEntry.HistoryUnavailable || it is TimelineEntry.Evicted }.map { SessionTimelineEntry.Scoped(id, it) }
    }

    // Placed by (position, rank): a row sits on its sequence, a missing range on its start, and a
    // divider right after its row.
    class Placed(val position: Long, val rank: Int, val entry: SessionTimelineEntry)
    val placed = mutableListOf<Placed>()
    val ranges = LinkedHashSet<ClosedRange<TimeMachineSequence>>()
    for ((id, entries) in timelines) for (entry in entries) when (entry) {
        is TimelineEntry.Message -> placed += Placed(entry.row.sequence.value, 0, SessionTimelineEntry.Scoped(id, entry))
        is TimelineEntry.PossiblyIncomplete -> if (ranges.add(entry.range)) placed += Placed(entry.range.start.value, 0, SessionTimelineEntry.Shared(entry))
        else -> Unit
    }
    session.connections.forEachIndexed { index, span ->
        val kind = if (index == 0) SessionTimelineEntry.Divider.Kind.Connected else SessionTimelineEntry.Divider.Kind.Reconnected
        for ((edgeKind, edge) in listOf(kind to span.connected, SessionTimelineEntry.Divider.Kind.Disconnected to span.disconnected)) {
            if (edge != null) placed += Placed(edge.after?.value ?: Long.MIN_VALUE, 1, SessionTimelineEntry.Divider(edgeKind, edge.at, edge.after))
        }
    }
    // Stable: dividers sharing a position keep their chronological order.
    return markers + placed.sortedWith(compareBy<Placed> { it.position }.thenBy { it.rank }).map { it.entry }
}
