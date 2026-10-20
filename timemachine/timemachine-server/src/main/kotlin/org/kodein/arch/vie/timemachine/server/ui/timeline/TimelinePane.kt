package org.kodein.arch.vie.timemachine.server.ui.timeline

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo
import org.kodein.arch.vie.timemachine.server.store.Row
import org.kodein.arch.vie.timemachine.server.store.SessionRecord
import org.kodein.arch.vie.timemachine.server.store.SessionTimelineEntry
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.store.TimelineEntry
import org.kodein.arch.vie.timemachine.server.store.ViewModelRecord
import org.kodein.arch.vie.timemachine.server.store.sessionTimeline
import org.kodein.arch.vie.timemachine.server.store.timelineEntry
import org.kodein.arch.vie.timemachine.server.ui.common.DiffOp
import org.kodein.arch.vie.timemachine.server.ui.common.diffJson
import org.kodein.arch.vie.timemachine.server.ui.common.interventionNumber
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.common.shortSessionId
import org.kodein.arch.vie.timemachine.server.ui.common.shortViewModelId
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.Pane
import org.kodein.arch.vie.timemachine.server.ui.core.PaneViewModel
import org.kodein.arch.vie.timemachine.server.ui.core.Selection
import org.kodein.arch.vie.timemachine.server.ui.core.ViewModelStatus
import org.kodein.arch.vie.timemachine.server.ui.core.key
import org.kodein.arch.vie.timemachine.server.ui.core.whileShown
import kotlin.time.Instant
import kotlin.uuid.Uuid


internal interface TimelineVIE : VIE<TimelineVIE.Model, TimelineVIE.Intent, VIE.NoSignal> {

    /**
     * What the developer sets in the pane.
     *
     * @property expanded The rows shown in full.
     */
    data class Controls(
        val filter: TimelineFilter = TimelineFilter(),
        val follow: FollowState = FollowState.Following,
        val expanded: Set<TimeMachineSequence> = emptySet(),
    )

    data class Model(val controls: Controls = Controls(), val projection: TimelineProjection = TimelineProjection()) : VIE.Model

    sealed interface Intent : VIE.Intent {
        data class ToggleKind(val kind: VIEObjectKind) : Intent
        data class SetIntervenedOnly(val intervenedOnly: Boolean) : Intent
        data class SetQuery(val query: String) : Intent
        data object ClearFilters : Intent

        /** The developer scrolled away from the end. */
        data object ScrolledAway : Intent

        /** The "N new" pill was pressed, or the list was scrolled to the end. */
        data object Resume : Intent
        data class ToggleExpanded(val sequence: TimeMachineSequence) : Intent
        data class SelectRow(val sequence: TimeMachineSequence) : Intent

        /** The arrow keys: the row [delta] places after the selected one, in the filtered list. */
        data class Step(val delta: Int) : Intent

        /** Edit & intervene on a row. */
        data class ComposeFrom(val sequence: TimeMachineSequence) : Intent

        /** The row's ViewModel chip in a session scope: its own timeline, with the row selected. */
        data class OpenViewModel(val viewModelId: Uuid, val sequence: TimeMachineSequence) : Intent

        /** The "selected row is hidden by filters" chip: clears the filters that hide it. */
        data object ShowSelectedRow : Intent

        /** Posted by the pane itself, when [count] entries arrived. */
        data class EntriesArrived(val count: Int) : Intent
    }
}

/**
 * @property total How many rows (observations, bookends and drop reports) the scope retains, whatever the filter hides:
 *   none, and the timeline is empty, whatever markers it carries.
 * @property hidden How many observations the filter hides.
 * @property selectedHidden Whether the selected row is retained but hidden by the filters.
 * @property kindCounts How many observations of each kind the scope retains, whatever the filter hides.
 * @property intervenedCount How many of those observations were sent by the inspector.
 */
internal data class TimelineProjection(
    val header: TimelineHeader? = null,
    val items: List<TimelineItem> = emptyList(),
    val total: Int = 0,
    val hidden: Int = 0,
    val filterActive: Boolean = false,
    val selectedRow: TimeMachineSequence? = null,
    val selectedHidden: Boolean = false,
    val kindCounts: Map<VIEObjectKind, Int> = emptyMap(),
    val intervenedCount: Int = 0,
)

internal sealed interface TimelineHeader {

    /**
     * @property info `null` while it is unknown.
     * @property since When it started, or when the inspector first heard of it when [startSeen] is `false`.
     */
    data class ViewModel(
        val id: Uuid,
        val shortId: String,
        val status: ViewModelStatus,
        val info: VIEViewModelInfo?,
        val since: Instant,
        val startSeen: Boolean,
        val closedAt: Instant?,
        val sessionConnected: Boolean,
    ) : TimelineHeader

    data class Session(
        val id: Uuid,
        val name: String,
        val shortId: String,
        val connected: Boolean,
        val viewModelCount: Int,
    ) : TimelineHeader
}

/** What the Model row shows as `Δ`: the differences from the Model observed [previous]. */
internal data class ModelDiff(val previous: TimeMachineSequence, val ops: List<DiffOp>)

/**
 * One entry of the filtered list.
 *
 * @property key Identifies it in the list, stable across publications.
 * @property diff For a Model row whose previous Model is retained.
 * @property interventionNumber For an intervened observation, its number within its ViewModel.
 * @property snippet Under a query, the text that matched, trimmed to bring the hit into view.
 * @property source In a session scope, the ViewModel the row belongs to, as the `[180]` column names it.
 * @property canIntervene Whether Edit & intervene is open on it: it has JSON, its role is serializable,
 *   its ViewModel is alive and its session connected.
 */
internal data class TimelineItem(
    val key: String,
    val entry: SessionTimelineEntry,
    val diff: ModelDiff? = null,
    val interventionNumber: Int? = null,
    val snippet: String? = null,
    val source: String? = null,
    val canIntervene: Boolean = false,
) {
    val row: Row? get() = ((entry as? SessionTimelineEntry.Scoped)?.entry as? TimelineEntry.Message)?.row

    val sequence: TimeMachineSequence? get() = row?.sequence

    val observation: VIEObservation? get() = (row?.message as? TimeMachineClientMessage.Observed)?.observation
}

private fun SessionTimelineEntry.key(index: Int): String = when (this) {
    is SessionTimelineEntry.Scoped -> when (val e = entry) {
        is TimelineEntry.Message -> "r${e.row.sequence}"
        TimelineEntry.HistoryUnavailable -> "history-unavailable:$viewModelId"
        is TimelineEntry.Evicted -> "evicted:$viewModelId"
        is TimelineEntry.PossiblyIncomplete -> "incomplete:${e.range.start}"
    }
    is SessionTimelineEntry.Shared -> "incomplete:${entry.range.start}"
    is SessionTimelineEntry.Divider -> "divider:$index"
}

/** Projects the Timeline of one scope. The caches belong to it, and live as long as the pane is shown. */
internal class TimelineProjector(private val scope: InspectorScope) {

    /** Each row's JSON text, `null` for a row without any. */
    private val jsonText = LinkedHashMap<TimeMachineSequence, String?>()

    private val diffs = LinkedHashMap<TimeMachineSequence, ModelDiff?>()

    /** The rows whose JSON text and diff are cached. */
    val cachedRows: Set<TimeMachineSequence> get() = jsonText.keys + diffs.keys

    fun project(state: TimeMachineState, selection: Selection, controls: TimelineVIE.Controls): TimelineProjection {
        val session = state.sessions[scope.sessionId]
        val vm = (scope as? InspectorScope.ViewModel)?.let { session?.viewModels?.get(it.viewModelId) }
        if (session == null || (scope is InspectorScope.ViewModel && vm == null)) return TimelineProjection()

        val entries = when (scope) {
            is InspectorScope.Session -> state.sessionTimeline(scope.sessionId)
            is InspectorScope.ViewModel -> state.timeline(scope.sessionId, scope.viewModelId).map { SessionTimelineEntry.Scoped(scope.viewModelId, it) }
        }
        val rows = entries.mapNotNull { ((it as? SessionTimelineEntry.Scoped)?.entry as? TimelineEntry.Message)?.row }
        trim(rows)

        val filtered = controls.filter.apply(entries, { it.timelineEntry }, ::json)
        val query = controls.filter.query
        val items = filtered.items.mapIndexed { index, entry -> item(session, entry, index, query) }
        val selected = selection.row
        val observations = rows.mapNotNull { (it.message as? TimeMachineClientMessage.Observed)?.observation }
        return TimelineProjection(
            header = header(session, vm),
            items = items,
            total = rows.size,
            hidden = filtered.hidden,
            filterActive = filtered.active,
            selectedRow = selected,
            selectedHidden = selected != null && rows.any { it.sequence == selected } && items.none { it.sequence == selected },
            kindCounts = observations.groupingBy { it.kind }.eachCount(),
            intervenedCount = observations.count { it.interventionId != null },
        )
    }

    private fun header(session: SessionRecord, vm: ViewModelRecord?): TimelineHeader =
        if (vm == null) TimelineHeader.Session(session.id, session.session.name, shortSessionId(session.id), session.isConnected, session.viewModels.size)
        else TimelineHeader.ViewModel(
            id = vm.id,
            shortId = shortViewModelId(vm.id),
            status = when {
                vm.closed -> ViewModelStatus.Closed
                vm.isUnknown -> ViewModelStatus.Identifying
                else -> ViewModelStatus.Alive
            },
            info = vm.info,
            since = vm.startedAt ?: vm.firstSeenAt,
            startSeen = vm.startedAt != null,
            closedAt = vm.closedAt,
            sessionConnected = session.isConnected,
        )

    private fun item(session: SessionRecord, entry: SessionTimelineEntry, index: Int, query: String): TimelineItem {
        val scoped = entry as? SessionTimelineEntry.Scoped
        val row = (scoped?.entry as? TimelineEntry.Message)?.row
        val observation = (row?.message as? TimeMachineClientMessage.Observed)?.observation
        val vm = scoped?.let { session.viewModels[it.viewModelId] }
        val source = if (scope is InspectorScope.Session && vm != null) vm.label() else null
        if (scoped == null || row == null || observation == null) return TimelineItem(entry.key(index), entry, source = source)
        return TimelineItem(
            key = entry.key(index),
            entry = entry,
            diff = if (vm != null && observation.kind == VIEObjectKind.Model) diff(vm, row, observation) else null,
            interventionNumber = observation.interventionId?.let { vm?.interventionNumber(it) },
            source = source,
            canIntervene = vm != null && canIntervene(session, vm, observation),
            snippet = if (query.isEmpty()) null else listOfNotNull(observation.asString, json(row)).firstOrNull { it.contains(query, ignoreCase = true) }?.let { searchSnippet(it, query) },
        )
    }

    private fun canIntervene(session: SessionRecord, vm: ViewModelRecord, observation: VIEObservation): Boolean {
        val info = vm.info ?: return false
        val role = when (observation.kind) {
            VIEObjectKind.Model -> info.model
            VIEObjectKind.Intent -> info.intent
            VIEObjectKind.Signal -> info.signal
        }
        return session.isConnected && !vm.closed && role.isSerializable && observation.asJson != null
    }

    private fun ViewModelRecord.label(): String =
        info?.let { "${it.typeName.substringAfterLast('.')} · ${shortViewModelId(id)}" } ?: shortViewModelId(id)

    private fun json(row: Row): String? {
        val sequence = row.sequence
        if (sequence in jsonText) return jsonText[sequence]
        return (row.message as? TimeMachineClientMessage.Observed)?.observation?.asJson?.toString().also { jsonText[sequence] = it }
    }

    private fun diff(vm: ViewModelRecord, row: Row, observation: VIEObservation): ModelDiff? {
        val sequence = row.sequence
        if (sequence in diffs) return diffs[sequence]
        val previous = vm.previousModel(sequence)
        val old = (previous?.message as? TimeMachineClientMessage.Observed)?.observation?.asJson
        val new = observation.asJson
        return (if (previous == null) null else ModelDiff(previous.sequence, if (old != null && new != null) diffJson(old, new) else emptyList()))
            .also { diffs[sequence] = it }
    }

    /** Drops the cache entries of the rows the store no longer retains. */
    private fun trim(retained: List<Row>) {
        val sequences = retained.mapTo(HashSet()) { it.sequence }
        jsonText.keys.retainAll(sequences)
        diffs.keys.retainAll(sequences)
    }
}

internal class TimelineVM(
    private val inspector: Inspector,
    private val scope: InspectorScope,
) : TimelineVIE, PaneViewModel<TimelineVIE.Model, TimelineVIE.Intent, VIE.NoSignal>(TimelineVIE.Model()) {

    init {
        val inputs = combine(
            inspector.store.state,
            inspector.selection.state,
            modelFlow.map { it.controls }.distinctUntilChanged(),
        ) { state, selection, controls -> Triple(state, selection.resolvedIn(state), controls) }
        var lastSequence: TimeMachineSequence? = null
        project(
            inspector.whileShown(key(scope), inputs) {
                val projector = TimelineProjector(scope);
                { (state, selection, controls) -> projector.project(state, selection, controls) }
            },
        ) { projection ->
            // What is new is whatever sits after the last row the previous projection ended on.
            val arrived = lastSequence?.let { last -> projection.items.count { (it.sequence ?: last) > last } } ?: 0
            lastSequence = projection.items.mapNotNull { it.sequence }.maxOrNull() ?: lastSequence
            if (arrived > 0) post(TimelineVIE.Intent.EntriesArrived(arrived))
            copy(projection = projection)
        }
    }

    override suspend fun process(intent: TimelineVIE.Intent) {
        when (intent) {
            is TimelineVIE.Intent.ToggleKind -> filter { copy(kinds = if (intent.kind in kinds) kinds - intent.kind else kinds + intent.kind) }
            is TimelineVIE.Intent.SetIntervenedOnly -> filter { copy(intervenedOnly = intent.intervenedOnly) }
            is TimelineVIE.Intent.SetQuery -> filter { copy(query = intent.query) }
            TimelineVIE.Intent.ClearFilters -> filter { TimelineFilter() }
            TimelineVIE.Intent.ScrolledAway -> controls { copy(follow = follow.scrolledAway()) }
            TimelineVIE.Intent.Resume -> controls { copy(follow = follow.resumed()) }
            is TimelineVIE.Intent.ToggleExpanded ->
                controls { copy(expanded = if (intent.sequence in expanded) expanded - intent.sequence else expanded + intent.sequence) }
            is TimelineVIE.Intent.SelectRow -> inspector.selection.selectRow(intent.sequence)
            is TimelineVIE.Intent.Step -> step(intent.delta)
            is TimelineVIE.Intent.ComposeFrom -> composeFrom(intent.sequence)
            is TimelineVIE.Intent.OpenViewModel -> {
                inspector.selection.selectScope(InspectorScope.ViewModel(scope.sessionId, intent.viewModelId))
                inspector.selection.selectRow(intent.sequence)
            }
            TimelineVIE.Intent.ShowSelectedRow -> showSelectedRow()
            is TimelineVIE.Intent.EntriesArrived -> controls { copy(follow = follow.received(intent.count)) }
        }
    }

    private suspend fun controls(change: TimelineVIE.Controls.() -> TimelineVIE.Controls) = update { copy(controls = controls.change()) }

    private suspend fun filter(change: TimelineFilter.() -> TimelineFilter) = controls { copy(filter = filter.change()) }

    /**
     * Read from the holder's current value, so held keys never race: a first press selects the first
     * row (the last, going up), and the ends stop.
     */
    private fun step(delta: Int) {
        val rows = model.projection.items.mapNotNull { it.sequence }
        if (rows.isEmpty()) return
        val current = inspector.selection.state.value.row?.let(rows::indexOf)?.takeIf { it >= 0 }
        val next = when {
            current != null -> (current + delta).coerceIn(rows.indices)
            delta > 0 -> 0
            else -> rows.lastIndex
        }
        inspector.selection.selectRow(rows[next])
    }

    private fun composeFrom(sequence: TimeMachineSequence) {
        val item = model.projection.items.firstOrNull { it.sequence == sequence }?.takeIf { it.canIntervene } ?: return
        val observation = item.observation ?: return
        val json = observation.asJson ?: return
        val viewModelId = (item.entry as? SessionTimelineEntry.Scoped)?.viewModelId ?: return
        inspector.selection.selectRow(sequence)
        inspector.selection.composeFrom(viewModelId, observation.kind, observation.typeName, json.pretty())
    }

    private suspend fun showSelectedRow() {
        val sequence = inspector.selection.state.value.row ?: return
        val state = inspector.store.state.value
        val row = state.sessions[scope.sessionId]?.viewModels?.values?.firstNotNullOfOrNull { it.row(sequence) } ?: return
        val observation = (row.message as? TimeMachineClientMessage.Observed)?.observation ?: return
        filter { showing(observation) { observation.asJson?.toString() } }
    }

    companion object {
        fun key(scope: InspectorScope): String = "timeline:${scope.key}"
    }
}

internal fun Inspector.timelinePane(scope: InspectorScope) = Pane(TimelineVM.key(scope)) { TimelineVM(this, scope) }
