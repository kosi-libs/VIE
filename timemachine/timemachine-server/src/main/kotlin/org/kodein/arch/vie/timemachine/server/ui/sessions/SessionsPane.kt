package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.server.store.ActivityBucketMillis
import org.kodein.arch.vie.timemachine.server.store.SessionRecord
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.store.ViewModelRecord
import org.kodein.arch.vie.timemachine.server.ui.common.shortSessionId
import org.kodein.arch.vie.timemachine.server.ui.common.shortViewModelId
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.Pane
import org.kodein.arch.vie.timemachine.server.ui.core.PaneViewModel
import org.kodein.arch.vie.timemachine.server.ui.core.ViewModelStatus
import org.kodein.arch.vie.timemachine.server.ui.core.current
import org.kodein.arch.vie.timemachine.server.ui.core.viewModelStatus
import org.kodein.arch.vie.timemachine.server.ui.core.whileShown
import kotlin.time.Instant
import kotlin.uuid.Uuid


internal interface SessionsVIE : VIE<SessionsVIE.Model, SessionsVIE.Intent, VIE.NoSignal> {

    /**
     * What the developer sets in the pane. The selected scope is not here: it is the holder's.
     *
     * @property collapsed The sessions whose ViewModels are folded away. A session is unfolded until it
     *   is folded, so a new one arrives unfolded.
     */
    data class Controls(val collapsed: Set<Uuid> = emptySet())

    /**
     * The sessions tree.
     *
     * @property selected The scope the holder selects, as the store still holds it.
     * @property disconnectedSessions How many sessions are disconnected: what "Clear disconnected" removes.
     */
    data class Model(
        val controls: Controls = Controls(),
        val sessions: List<SessionItem> = emptyList(),
        val selected: InspectorScope? = null,
        val disconnectedSessions: Int = 0,
    ) : VIE.Model

    sealed interface Intent : VIE.Intent {
        data class SelectScope(val scope: InspectorScope) : Intent

        /** Folds the ViewModels of session [sessionId] away, or unfolds them. */
        data class ToggleSession(val sessionId: Uuid) : Intent
        data class ClearSession(val sessionId: Uuid) : Intent
        data object ClearDisconnected : Intent
    }
}

/**
 * @property sparkline The sum of its ViewModels' activity over the last 30 s.
 * @property clearLabel What the session's clear action does: "Clear session" when disconnected, else "Clear N closed ViewModels".
 */
internal data class SessionItem(
    val id: Uuid,
    val name: String,
    val shortId: String,
    val connected: Boolean,
    val connectedAt: Instant,
    val disconnectedAt: Instant?,
    val viewModels: List<ViewModelItem>,
    val sparkline: List<Int>,
    val clearLabel: String,
    val clearEnabled: Boolean,
)

/**
 * @property rowCount How many rows it ever had: the count printed under its sparkline.
 * @property typeName `null` while the ViewModel is unknown: it is then shown by [shortId] only.
 * @property since When it started, or when the inspector first heard of it when its start was never seen.
 * @property startSeen Whether [since] is its start.
 */
internal data class ViewModelItem(
    val id: Uuid,
    val typeName: String?,
    val shortId: String,
    val status: ViewModelStatus,
    val since: Instant,
    val startSeen: Boolean,
    val closedAt: Instant?,
    val rowCount: Int,
    val sparkline: List<Int>,
)

internal class SessionsVM(private val inspector: Inspector) :
    SessionsVIE, PaneViewModel<SessionsVIE.Model, SessionsVIE.Intent, VIE.NoSignal>(
        inspector.current().let { (state, selection) -> sessionsModel(state, selection.scope, inspector.clock.now()) },
    ) {

    init {
        // The sparkline is a window ending at now: a ticker re-reads it at each bucket boundary.
        project(
            inspector.whileShown(
                SessionsKey,
                combine(inspector.store.state, inspector.selection.state, ticker()) { state, selection, now -> Triple(state, selection, now) },
            ) { { (state, selection, now) -> sessionsModel(state, selection.resolvedIn(state).scope, now) } },
        ) { projection ->
            // The controls are the developer's, not the store's: a projection keeps them, but for the sessions
            // the store dropped.
            val held = projection.sessions.mapTo(HashSet()) { it.id }
            projection.copy(controls = controls.copy(collapsed = controls.collapsed.filterTo(LinkedHashSet()) { it in held }))
        }
        // A ViewModel the selection moves to is shown: its session unfolds. Folding one leaves the selection
        // where it is, and the selection that is already there does not unfold it again.
        viewModelScope.launch {
            inspector.selection.state.map { it.scope }.distinctUntilChanged().collect { scope ->
                if (scope is InspectorScope.ViewModel) update { copy(controls = controls.copy(collapsed = controls.collapsed - scope.sessionId)) }
            }
        }
    }

    /** `clock.now()` at each bucket boundary of the activity sparkline. */
    private fun ticker(): Flow<Instant> = flow {
        while (true) {
            val now = inspector.clock.now()
            emit(now)
            delay(ActivityBucketMillis - now.toEpochMilliseconds().mod(ActivityBucketMillis))
        }
    }.distinctUntilChanged()

    override suspend fun process(intent: SessionsVIE.Intent) {
        when (intent) {
            is SessionsVIE.Intent.SelectScope -> inspector.selection.selectScope(intent.scope)
            is SessionsVIE.Intent.ToggleSession -> update {
                val collapsed = controls.collapsed
                copy(controls = controls.copy(collapsed = if (intent.sessionId in collapsed) collapsed - intent.sessionId else collapsed + intent.sessionId))
            }
            is SessionsVIE.Intent.ClearSession -> inspector.store.post(TimeMachineEvent.ClearSession(intent.sessionId))
            SessionsVIE.Intent.ClearDisconnected -> inspector.store.post(TimeMachineEvent.ClearDisconnected)
        }
    }
}

/** The sessions tree as [state] holds it, the sparklines ending at [now]; [selected] is the scope the holder selects. */
internal fun sessionsModel(state: TimeMachineState, selected: InspectorScope?, now: Instant) = SessionsVIE.Model(
    sessions = state.sessions.values.map { it.item(now) },
    selected = selected,
    disconnectedSessions = state.sessions.values.count { !it.isConnected },
)

private fun SessionRecord.item(now: Instant): SessionItem {
    val closed = viewModels.values.count { it.closed }
    return SessionItem(
        id = id,
        name = session.name,
        shortId = shortSessionId(id),
        connected = isConnected,
        connectedAt = connectedAt,
        disconnectedAt = disconnectedAt,
        viewModels = viewModels.values.map { it.item(this, now) },
        sparkline = sparkline(now),
        clearLabel = when {
            !isConnected -> "Clear session"
            closed == 0 -> "Clear closed ViewModels"
            closed == 1 -> "Clear 1 closed ViewModel"
            else -> "Clear $closed closed ViewModels"
        },
        clearEnabled = !isConnected || closed > 0,
    )
}

private fun ViewModelRecord.item(session: SessionRecord, now: Instant) = ViewModelItem(
    id = id,
    typeName = info?.typeName,
    shortId = shortViewModelId(id),
    status = viewModelStatus(session, this),
    since = startedAt ?: firstSeenAt,
    startSeen = startedAt != null,
    closedAt = closedAt,
    rowCount = rowCount,
    sparkline = sparkline(now),
)

private const val SessionsKey = "sessions"

internal fun Inspector.sessionsPane() = Pane(SessionsKey) { SessionsVM(this) }
