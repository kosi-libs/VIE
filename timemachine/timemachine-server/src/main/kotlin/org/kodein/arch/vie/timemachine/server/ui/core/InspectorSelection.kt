package org.kodein.arch.vie.timemachine.server.ui.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.store.SessionRecord
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.store.ViewModelRecord
import kotlin.uuid.Uuid


/** What the inspector looks at: a whole session, or one of its ViewModels. */
sealed interface InspectorScope {

    val sessionId: Uuid

    data class Session(override val sessionId: Uuid) : InspectorScope

    data class ViewModel(override val sessionId: Uuid, val viewModelId: Uuid) : InspectorScope
}

/** The tabs of the right pane, below the pinned current model. */
enum class RightTab { Detail, Composer }

/**
 * Edit & intervene: what the Composer of [viewModelId] is to start its draft from.
 *
 * @property json The draft's text.
 */
data class ComposerRequest(val viewModelId: Uuid, val kind: VIEObjectKind, val type: String, val json: String)

/**
 * The facts several panes read.
 *
 * @property scope `null` when nothing is selected.
 * @property row The selected row, in [scope]: it means nothing without one.
 * @property composerRequest The request the Composer of its ViewModel has not applied yet.
 */
data class Selection(
    val scope: InspectorScope? = null,
    val row: TimeMachineSequence? = null,
    val tab: RightTab = RightTab.Detail,
    val composerRequest: ComposerRequest? = null,
) {

    /**
     * This selection as the [state] still holds it: a scope the store dropped is none, and so is a
     * row that is not retained in the scope any more, so the holder owns no coroutine and nothing has
     * to clear it.
     */
    fun resolvedIn(state: TimeMachineState): Selection {
        val scope = scope?.takeIf { state.resolve(it) != null } ?: return copy(scope = null, row = null)
        val row = row?.takeIf { sequence -> state.resolve(scope)!!.second.any { it.holds(sequence) } }
        return copy(scope = scope, row = row)
    }

    /**
     * The ViewModel the right pane shows: the scope's, or, in a session scope, the selected row's.
     * Computed, never held; `null` in a session scope until a row is selected.
     */
    fun target(state: TimeMachineState): Target? {
        val (session, viewModels) = scope?.let(state::resolve) ?: return null
        val vm = when (scope) {
            is InspectorScope.ViewModel -> viewModels.single()
            is InspectorScope.Session -> row?.let { sequence -> viewModels.firstOrNull { it.holds(sequence) } } ?: return null
        }
        return Target(session, vm)
    }
}

/** A ViewModel with the session it is in. */
data class Target(val session: SessionRecord, val vm: ViewModelRecord)

/** The session of [scope] with the ViewModels it covers; `null` when the store no longer holds it. */
private fun TimeMachineState.resolve(scope: InspectorScope): Pair<SessionRecord, List<ViewModelRecord>>? {
    val session = sessions[scope.sessionId] ?: return null
    return when (scope) {
        is InspectorScope.Session -> session to session.viewModels.values.toList()
        is InspectorScope.ViewModel -> session.viewModels[scope.viewModelId]?.let { session to listOf(it) }
    }
}

/**
 * The [Selection] holder, one atomic update per rule so no pane ever sees half of one. It owns no
 * coroutine: [Selection.resolvedIn] reads it against the store.
 */
internal class InspectorSelection(initial: Selection = Selection()) {

    private val mutable = MutableStateFlow(initial)

    val state: StateFlow<Selection> = mutable.asStateFlow()

    /** Another scope clears the row, keeps the tab, and drops a request unless it targets the new scope's ViewModel. */
    fun selectScope(scope: InspectorScope?) {
        mutable.update { current ->
            if (current.scope == scope) current
            else current.copy(
                scope = scope,
                row = null,
                composerRequest = current.composerRequest?.takeIf { scope is InspectorScope.ViewModel && scope.viewModelId == it.viewModelId },
            )
        }
    }

    fun selectRow(sequence: TimeMachineSequence?) {
        mutable.update { it.copy(row = sequence) }
    }

    fun selectTab(tab: RightTab) {
        mutable.update { it.copy(tab = tab) }
    }

    /** Opens the Composer tab and records the request. */
    fun composeFrom(viewModelId: Uuid, kind: VIEObjectKind, type: String, json: String) {
        mutable.update { it.copy(tab = RightTab.Composer, composerRequest = ComposerRequest(viewModelId, kind, type, json)) }
    }

    /**
     * Returns the request once, and only to the Composer of its ViewModel. It consumes, so it runs in
     * the Composer's `process`, never in its projection: a hide or a newer publication cancels a
     * projection at any point, which could lose a request the holder had already forgotten.
     */
    fun takeRequest(viewModelId: Uuid): ComposerRequest? {
        while (true) {
            val current = mutable.value
            val request = current.composerRequest?.takeIf { it.viewModelId == viewModelId } ?: return null
            if (mutable.compareAndSet(current, current.copy(composerRequest = null))) return request
        }
    }
}

/** What identifies a scope in a pane key. */
internal val InspectorScope.key: String
    get() = when (this) {
        is InspectorScope.Session -> "session-${sessionId.toHexString()}"
        is InspectorScope.ViewModel -> "vm-${viewModelId.toHexString()}"
    }
