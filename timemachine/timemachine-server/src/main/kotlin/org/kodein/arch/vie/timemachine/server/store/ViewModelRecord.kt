package org.kodein.arch.vie.timemachine.server.store

import kotlinx.serialization.json.JsonElement
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.TimeMachineTimelineMessage
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo
import kotlin.time.Instant
import kotlin.uuid.Uuid


/**
 * How much timeline each ViewModel keeps: [maxRows] rows or [maxChars] characters of frame
 * text, whichever comes first. A row weighs its frame's length in characters.
 */
data class Limits(val maxRows: Int = 1_000, val maxChars: Long = 32L * 1024 * 1024)

/**
 * A timeline message placed in its ViewModel's timeline, and the length, in characters, of the
 * frame that carried it.
 */
data class Row(val message: TimeMachineTimelineMessage, val length: Int) {
    val sequence: TimeMachineSequence get() = message.sequence
}

/** Before every sequence: what an end that is not known yet counts as. */
private val NoSequence = TimeMachineSequence(Long.MIN_VALUE)

/** After every sequence: the end of a ViewModel that is not closed. */
private val EndOfTime = TimeMachineSequence(Long.MAX_VALUE)

/** The last value of something, kept with the sequence it came from: only a newer one replaces it. */
data class Latest<out T>(val sequence: TimeMachineSequence, val value: T)

/** An observed object's type, as the latest JSON per type is indexed. */
data class TypeKey(val kind: VIEObjectKind, val typeName: String)

/**
 * A ViewModel of a session, as the inspector knows it.
 *
 * @property info `null` while the ViewModel is unknown: a timeline message named it before anything
 *   described it.
 * @property firstSeenAt When the inspector first heard of it.
 * @property seenOnTimeline Whether any timeline message named it, rather than only a re-announce.
 * @property startSequence The sequence of its [TimeMachineClientMessage.ViewModelStarted], once
 *   seen; kept when that row is evicted.
 * @property startedAt When the probe says it started, once its [TimeMachineClientMessage.ViewModelStarted]
 *   was seen; kept when that row is evicted.
 * @property closeSequence The sequence of its [TimeMachineClientMessage.ViewModelClosed], once seen.
 * @property closed Whether it is known not to be alive. Terminal: nothing reopens it.
 * @property inferredEnd The session's highest sequence when the probe answered that it no longer
 *   knows the ViewModel: the end of its life, when its close was never seen.
 * @property awaitingInfo Whether its info was asked for, because it was unknown or a drop report may
 *   hide its close, and no [TimeMachineClientMessage.ViewModelInfo] has arrived since. A revival
 *   asks again exactly these. One answer clears it even if several requests are in flight: a close
 *   is never lost, so the one a lost answer would have inferred is not missed.
 * @property rows The retained rows, by sequence.
 * @property retainedChars How many characters of frame text [rows] weigh.
 * @property evictedCount How many rows the bounds removed, or never placed.
 * @property rowCount How many rows it ever had, evicted ones included.
 * @property latestJson The latest JSON of each observed type, evicted rows included.
 * @property signalDelivery The delivery kind of each Signal type observed, by type name, evicted rows
 *   included. A type that picked neither kind has no entry.
 * @property currentModel The latest Model observed, evicted rows included.
 * @property lastActivity The display time of its latest row, evicted rows included.
 * @property activity How many rows it had in each of its newest ten 3 s buckets, evicted rows
 *   included; `null` before its first row.
 * @property interventions The interventions asked of it, in the order they were asked, kept for as
 *   long as the store holds the ViewModel.
 */
data class ViewModelRecord(
    val id: Uuid,
    val info: VIEViewModelInfo?,
    val firstSeenAt: Instant,
    val seenOnTimeline: Boolean = false,
    val startSequence: TimeMachineSequence? = null,
    val startedAt: Instant? = null,
    val closeSequence: TimeMachineSequence? = null,
    val closedAt: Instant? = null,
    val closed: Boolean = false,
    val inferredEnd: TimeMachineSequence? = null,
    val awaitingInfo: Boolean = false,
    val rows: List<Row> = emptyList(),
    val retainedChars: Long = 0,
    val evictedCount: Int = 0,
    val rowCount: Int = 0,
    val latestJson: Map<TypeKey, Latest<JsonElement>> = emptyMap(),
    val signalDelivery: Map<String, VIESignalDelivery> = emptyMap(),
    val currentModel: Latest<VIEObservation>? = null,
    val lastActivity: Latest<Instant>? = null,
    val activity: Activity? = null,
    val interventions: Map<Uuid, Intervention> = emptyMap(),
) {

    val isUnknown: Boolean get() = info == null

    /** The retained row at [sequence]: `null` once it was evicted, or never placed. */
    fun row(sequence: TimeMachineSequence): Row? =
        rows.binarySearch { it.sequence.compareTo(sequence) }.let { if (it >= 0) rows[it] else null }

    /** Whether the row at [sequence] is retained: `false` once it was evicted, or never placed. */
    fun holds(sequence: TimeMachineSequence): Boolean = row(sequence) != null

    /** The ten bucket counts of its activity over the 30 s ending at [now], oldest first. */
    fun sparkline(now: Instant): List<Int> = activity.sparkline(now)

    /**
     * The Model row observed before the one at [sequence], from the retained rows: `null` when there
     * is none, e.g. once it was evicted.
     */
    fun previousModel(sequence: TimeMachineSequence): Row? =
        rows.lastOrNull { it.sequence < sequence && (it.message as? TimeMachineClientMessage.Observed)?.observation?.kind == VIEObjectKind.Model }

    /**
     * Whether its history before the inspector heard of it is out of reach: its start was never
     * seen, and either the session was not seen from its beginning or the ViewModel came only from
     * a re-announce. In a session seen from sequence 0, an unseen start was dropped (a drop report
     * is then its first row) or lost (a missing range shows it).
     */
    fun historyUnavailable(sequences: SequenceLedger): Boolean =
        startSequence == null && ((sequences.floor ?: TimeMachineSequence.First) > TimeMachineSequence.First || !seenOnTimeline)

    /**
     * The sequences it was alive for: from its start, or the session's floor when its start was
     * never seen, to its close. When only the probe's answer closed it, to the inferred end, or its
     * last row when that is later. Open-ended while it is not closed; `null` before the session
     * received any sequence.
     */
    fun aliveSpan(sequences: SequenceLedger): ClosedRange<TimeMachineSequence>? {
        val start = startSequence ?: sequences.floor ?: return null
        val end = closeSequence
            ?: if (closed) maxOf(inferredEnd ?: NoSequence, lastActivity?.sequence ?: NoSequence)
            else EndOfTime
        return start..end
    }

    /**
     * Places [row] by sequence, then evicts the oldest rows beyond [limits]. A row heavier than
     * [Limits.maxChars] on its own is counted as evicted without being placed, as is, thereby, a row
     * older than a full timeline's oldest. What survives eviction is updated first.
     */
    fun place(row: Row, limits: Limits): ViewModelRecord {
        val message = row.message
        val sequence = row.sequence
        var next = copy(
            rowCount = rowCount + 1,
            lastActivity = lastActivity.newer(sequence, message.at),
            activity = activity.counting(message.at),
        )
        if (message is TimeMachineClientMessage.Observed) {
            val observation = message.observation
            observation.asJson?.let { json ->
                val key = TypeKey(observation.kind, observation.typeName)
                next = next.copy(latestJson = latestJson + (key to (latestJson[key].newer(sequence, json))))
            }
            observation.delivery?.let { next = next.copy(signalDelivery = signalDelivery + (observation.typeName to it)) }
            if (observation.kind == VIEObjectKind.Model) next = next.copy(currentModel = currentModel.newer(sequence, observation))
        }
        if (row.length > limits.maxChars) return next.copy(evictedCount = evictedCount + 1)
        val index = rows.binarySearch { it.sequence.compareTo(sequence) }.let { if (it < 0) -it - 1 else it }
        val placed = rows.toMutableList().apply { add(index, row) }
        var chars = retainedChars + row.length
        var evicted = 0
        while (placed.isNotEmpty() && (placed.size > limits.maxRows || chars > limits.maxChars)) {
            chars -= placed.removeAt(0).length
            evicted++
        }
        return next.copy(rows = placed, retainedChars = chars, evictedCount = evictedCount + evicted)
    }
}

private fun <T> Latest<T>?.newer(sequence: TimeMachineSequence, value: T): Latest<T> =
    if (this != null && this.sequence > sequence) this else Latest(sequence, value)

/** The message's display time. */
val TimeMachineTimelineMessage.at: Instant
    get() = when (this) {
        is TimeMachineClientMessage.ViewModelStarted -> at
        is TimeMachineClientMessage.ViewModelClosed -> at
        is TimeMachineClientMessage.Observed -> observation.at
        is TimeMachineClientMessage.ObservationsDropped -> at
    }

/** The ViewModel the message is about. */
val TimeMachineTimelineMessage.viewModelId: Uuid
    get() = when (this) {
        is TimeMachineClientMessage.ViewModelStarted -> info.id
        is TimeMachineClientMessage.ViewModelClosed -> viewModelId
        is TimeMachineClientMessage.Observed -> observation.viewModelId
        is TimeMachineClientMessage.ObservationsDropped -> viewModelId
    }
