package org.kodein.arch.vie.timemachine.server.store

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import kotlin.time.Instant
import kotlin.uuid.Uuid


/**
 * A probe's session, as the inspector knows it.
 *
 * @property connection The session's current connection, `null` while it is disconnected. Requests
 *   to the probe always go there.
 * @property connections One span per accepted `Hello`, oldest first; never empty. Unbounded only in
 *   reconnects.
 * @property viewModels Its ViewModels, in first-seen order.
 * @property cleared The ViewModels a clear removed. A later message naming one of them still
 *   updates [sequences], so it opens no false missing range, and is otherwise ignored.
 * @property interventionIndex The ViewModel each intervention was asked of, by request identifier.
 *   Loses an entry when its ViewModel is cleared.
 */
data class SessionRecord(
    val session: TimeMachineSession,
    val protocolVersion: Int,
    val connection: ConnectionId?,
    val connections: List<ConnectionSpan>,
    val sequences: SequenceLedger = SequenceLedger(),
    val viewModels: Map<Uuid, ViewModelRecord> = emptyMap(),
    val cleared: Set<Uuid> = emptySet(),
    val interventionIndex: Map<Uuid, Uuid> = emptyMap(),
) {
    val id: Uuid get() = session.id

    val isConnected: Boolean get() = connection != null

    /** When the latest connection was accepted. */
    val connectedAt: Instant get() = connections.last().connected.at

    /** When the latest connection was lost, `null` while it is alive. */
    val disconnectedAt: Instant? get() = connections.last().disconnected?.at

    /** The sum of its ViewModels' activity over the 30 s ending at [now], oldest bucket first. */
    fun sparkline(now: Instant): List<Int> =
        viewModels.values.map { it.sparkline(now) }.reduceOrNull { a, b -> a.zip(b) { x, y -> x + y } } ?: List(ActivityBuckets) { 0 }
}

/**
 * One accepted connection of a session, as its timeline places it.
 *
 * @property disconnected `null` while it is the current connection, and also when the next `Hello`
 *   evicted it: the inspector never saw that disconnect, so the next span's [connected] is the only
 *   divider.
 */
data class ConnectionSpan(val connected: Edge, val disconnected: Edge? = null) {

    /**
     * @property after The session's highest sequence at that moment, `null` before the first. Sequence
     *   is the only order: the rows above it were received after this point, on either connection, so
     *   a divider sorts right after it.
     */
    data class Edge(val at: Instant, val after: TimeMachineSequence?)
}

/**
 * The probe of session [sessionId] speaks a protocol this inspector does not: one record however
 * often it retries.
 *
 * @property protocolVersion The probe's protocol version.
 * @property dismissed Whether it was dismissed. A dismissal holds for [sessionId], whatever the
 *   later attempts.
 */
data class Rejection(
    val sessionId: Uuid,
    val name: String,
    val protocolVersion: Int,
    val attempts: Int,
    val lastAttemptAt: Instant,
    val dismissed: Boolean = false,
)

/** The state of the inspector's WebSocket listener. */
sealed interface ListenerState {
    data object Starting : ListenerState
    data class Listening(val port: Int) : ListenerState
    data class Failed(val message: String) : ListenerState
}

/** One entry of a ViewModel's timeline, as the inspector shows it. */
sealed interface TimelineEntry {

    /** The ViewModel's history before the inspector heard of it is out of reach. */
    data object HistoryUnavailable : TimelineEntry

    /** The bounds removed the [count] oldest rows. */
    data class Evicted(val count: Int) : TimelineEntry

    /** A row: a bookend, an observation or a drop report. */
    data class Message(val row: Row) : TimelineEntry

    /**
     * The session's sequences [range] were not received while the ViewModel was alive: some may be
     * this ViewModel's. Softer than a drop report, which names its ViewModel.
     */
    data class PossiblyIncomplete(val range: ClosedRange<TimeMachineSequence>) : TimelineEntry
}
