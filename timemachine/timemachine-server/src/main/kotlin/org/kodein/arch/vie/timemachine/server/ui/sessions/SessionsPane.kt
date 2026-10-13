package org.kodein.arch.vie.timemachine.server.ui.sessions

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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
import org.kodein.arch.vie.timemachine.server.ui.core.whileShown
import kotlin.time.Instant
import kotlin.uuid.Uuid


internal interface SessionsVIE : VIE<SessionsVIE.Model, SessionsVIE.Intent, VIE.NoSignal> {

    /**
     * The sessions tree. No controls: what the developer sets here, the selected scope, is the holder's.
     *
     * @property selected The scope the holder selects, as the store still holds it.
     * @property disconnectedSessions How many sessions are disconnected: what "Clear disconnected" removes.
     */
    data class Model(
        val sessions: List<SessionItem> = emptyList(),
        val selected: InspectorScope? = null,
        val disconnectedSessions: Int = 0,
    ) : VIE.Model

    sealed interface Intent : VIE.Intent {
        data class SelectScope(val scope: InspectorScope) : Intent
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
    SessionsVIE, PaneViewModel<SessionsVIE.Model, SessionsVIE.Intent, VIE.NoSignal>(SessionsVIE.Model()) {

    init {
        // The sparkline is a window ending at now: a ticker re-reads it at each bucket boundary.
        project(
            inspector.whileShown(
                SessionsKey,
                combine(inspector.store.state, inspector.selection.state, ticker()) { state, selection, now -> Triple(state, selection, now) },
            ) { { (state, selection, now) -> sessionsModel(state, selection.resolvedIn(state).scope, now) } },
        ) { it }
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
        viewModels = viewModels.values.map { it.item(now) },
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

private fun ViewModelRecord.item(now: Instant) = ViewModelItem(
    id = id,
    typeName = info?.typeName,
    shortId = shortViewModelId(id),
    status = when {
        info == null && !closed -> ViewModelStatus.Identifying
        closed -> ViewModelStatus.Closed
        else -> ViewModelStatus.Alive
    },
    since = startedAt ?: firstSeenAt,
    startSeen = startedAt != null,
    closedAt = closedAt,
    rowCount = rowCount,
    sparkline = sparkline(now),
)

private const val SessionsKey = "sessions"

internal fun Inspector.sessionsPane() = Pane(SessionsKey) { SessionsVM(this) }
