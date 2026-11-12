package org.kodein.arch.vie.timemachine.server.store

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import kotlin.time.Instant
import kotlin.uuid.Uuid


/** One entry of a session's timeline: every ViewModel's, merged. */
sealed interface SessionTimelineEntry {

    /** An entry of ViewModel [viewModelId]'s own timeline: a row, or one of its top markers. */
    data class Scoped(val viewModelId: Uuid, val entry: TimelineEntry) : SessionTimelineEntry

    /** A missing range of the session: one per range, naming no ViewModel. */
    data class Shared(val entry: TimelineEntry.PossiblyIncomplete) : SessionTimelineEntry

    /**
     * The session's connection changed at [at], right after the row at [after] (the top when `null`).
     *
     * [at] is read on the inspector's clock, when it accepted or lost the connection: the probe does
     * not report its connection changes. A row's display time is read on the device's clock, so the
     * two compare only as far as the clocks agree; [after], not [at], places the divider.
     *
     * @property span The index, in [SessionRecord.connections], of the connection it is an edge of. That
     *   list only grows, so [span] and [kind] identify the divider for as long as its session is held.
     */
    data class Divider(val kind: Kind, val at: Instant, val after: TimeMachineSequence?, val span: Int) : SessionTimelineEntry {
        enum class Kind { Connected, Reconnected, Disconnected }
    }
}

/**
 * The [TimelineEntry] this entry stands for: the row or top marker of a [SessionTimelineEntry.Scoped],
 * the range of a [SessionTimelineEntry.Shared]; `null` for a [SessionTimelineEntry.Divider], which stands for none.
 */
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
 * a [SessionTimelineEntry.Shared] per missing range of the session above its oldest retained row, even
 * one no known ViewModel was alive for, since a whole ViewModel may have vanished in it, and per range
 * below it that a ViewModel's timeline shows. A range among evicted rows is covered by the
 * [TimelineEntry.Evicted] markers. Then a divider per edge of [SessionRecord.connections]: right after
 * the row whose sequence is the edge's `after`, at the top of the rows when it is `null`. A span is
 * "connected" for the first, "reconnected" for each later one, and has a "disconnected" divider where
 * it has that edge. Empty for an unknown session.
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
    for ((id, entries) in timelines) for (entry in entries) when (entry) {
        is TimelineEntry.Message -> placed += Placed(entry.row.sequence.value, 0, SessionTimelineEntry.Scoped(id, entry))
        else -> Unit
    }
    // Walking `missing` once yields one entry per range, whatever the ViewModels' own timelines hold.
    val shown = timelines.values.flatten().filterIsInstance<TimelineEntry.PossiblyIncomplete>().map { it.range }.toSet()
    val oldest = session.viewModels.values.mapNotNull { it.rows.firstOrNull()?.sequence }.minOrNull()
    for (range in session.sequences.missing) {
        if (range in shown || (oldest != null && range.start > oldest)) {
            placed += Placed(range.start.value, 0, SessionTimelineEntry.Shared(TimelineEntry.PossiblyIncomplete(range)))
        }
    }
    session.connections.forEachIndexed { index, span ->
        val kind = if (index == 0) SessionTimelineEntry.Divider.Kind.Connected else SessionTimelineEntry.Divider.Kind.Reconnected
        for ((edgeKind, edge) in listOf(kind to span.connected, SessionTimelineEntry.Divider.Kind.Disconnected to span.disconnected)) {
            if (edge != null) placed += Placed(edge.after?.value ?: Long.MIN_VALUE, 1, SessionTimelineEntry.Divider(edgeKind, edge.at, edge.after, index))
        }
    }
    // Stable: dividers sharing a position keep their chronological order.
    return markers + placed.sortedWith(compareBy<Placed> { it.position }.thenBy { it.rank }).map { it.entry }
}
