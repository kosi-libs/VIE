package org.kodein.arch.vie.timemachine.server.ui.shell

import kotlinx.coroutines.flow.combine
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineProtocolVersion
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.Pane
import org.kodein.arch.vie.timemachine.server.ui.core.PaneViewModel
import org.kodein.arch.vie.timemachine.server.ui.core.whileShown
import kotlin.time.Instant
import kotlin.uuid.Uuid


internal interface StatusBarVIE : VIE<StatusBarVIE.Model, VIE.NoIntent, VIE.NoSignal> {

    /**
     * @property listener Where the listener stands.
     * @property address What probes connect to; `null` until it is listening.
     * @property connectedSessions How many of the [sessions] are connected.
     */
    data class Model(
        val listener: ListenerState = ListenerState.Starting,
        val address: String? = null,
        val protocolVersion: TimeMachineProtocolVersion = TimeMachineConnection.protocolVersion,
        val connectedSessions: Int = 0,
        val sessions: Int = 0,
    ) : VIE.Model
}

/** What the status bar tells of [state]: where the listener stands, where probes connect, how many sessions are there. */
private fun statusBarModel(state: TimeMachineState) = StatusBarVIE.Model(
    listener = state.listener,
    address = (state.listener as? ListenerState.Listening)?.let { "ws://localhost:${it.port}${TimeMachineConnection.path}" },
    connectedSessions = state.sessions.values.count { it.isConnected },
    sessions = state.sessions.size,
)

internal class StatusBarVM(inspector: Inspector) :
    StatusBarVIE, PaneViewModel<StatusBarVIE.Model, VIE.NoIntent, VIE.NoSignal>(StatusBarVIE.Model()) {

    init {
        project(inspector.whileShown(Key, inspector.store.state) { { state -> statusBarModel(state) } }) { it }
    }

    override suspend fun process(intent: VIE.NoIntent) = Unit

    companion object {
        const val Key = "status"
    }
}

internal fun Inspector.statusBarPane() = Pane(StatusBarVM.Key) { StatusBarVM(this) }

internal interface NoticesVIE : VIE<NoticesVIE.Model, NoticesVIE.Intent, VIE.NoSignal> {

    /**
     * @property rejections The rejected probes not dismissed, one per session however often it retried.
     * @property disconnected The banner for the selected scope's session, when it is disconnected.
     * @property screen What replaces the panes when there is nothing to browse.
     */
    data class Model(
        val rejections: List<RejectionNotice> = emptyList(),
        val disconnected: DisconnectedNotice? = null,
        val screen: Screen = Screen.None,
    ) : VIE.Model

    sealed interface Screen {
        data object None : Screen

        /** No session yet, and the listener is on its way or listening. */
        data object Empty : Screen

        /** Nothing can connect: [message] says why. */
        data class CannotListen(val message: String) : Screen
    }

    sealed interface Intent : VIE.Intent {
        data class DismissRejection(val sessionId: Uuid) : Intent

        /** Listens again after a failure. */
        data object Retry : Intent
    }
}

internal data class RejectionNotice(
    val sessionId: Uuid,
    val name: String,
    val protocolVersion: TimeMachineProtocolVersion,
    val attempts: Int,
    val lastAttemptAt: Instant,
)

internal data class DisconnectedNotice(val sessionId: Uuid, val name: String, val disconnectedAt: Instant?)

/** What the window tells the developer apart from the panes: notices of what was turned away, and of a session that is gone, and the screen when there is nothing to show. */
private fun noticesModel(state: TimeMachineState, scope: InspectorScope?) = NoticesVIE.Model(
    rejections = state.rejections.values.filterNot { it.dismissed }
        .map { RejectionNotice(it.sessionId, it.name, it.protocolVersion, it.attempts, it.lastAttemptAt) },
    disconnected = scope?.let { state.sessions[it.sessionId] }?.takeIf { !it.isConnected }
        ?.let { DisconnectedNotice(it.id, it.session.name, it.disconnectedAt) },
    screen = when {
        state.listener is ListenerState.Failed -> NoticesVIE.Screen.CannotListen(state.listener.message)
        state.sessions.isEmpty() -> NoticesVIE.Screen.Empty
        else -> NoticesVIE.Screen.None
    },
)

internal class NoticesVM(private val inspector: Inspector) :
    NoticesVIE, PaneViewModel<NoticesVIE.Model, NoticesVIE.Intent, VIE.NoSignal>(NoticesVIE.Model()) {

    init {
        val inputs = combine(inspector.store.state, inspector.selection.state) { state, selection -> state to selection.resolvedIn(state).scope }
        project(inspector.whileShown(Key, inputs) { { (state, scope) -> noticesModel(state, scope) } }) { it }
    }

    override suspend fun process(intent: NoticesVIE.Intent) {
        when (intent) {
            is NoticesVIE.Intent.DismissRejection -> inspector.store.post(TimeMachineEvent.DismissRejection(intent.sessionId))
            NoticesVIE.Intent.Retry -> inspector.retry()
        }
    }

    companion object {
        const val Key = "notices"
    }
}

internal fun Inspector.noticesPane() = Pane(NoticesVM.Key) { NoticesVM(this) }
