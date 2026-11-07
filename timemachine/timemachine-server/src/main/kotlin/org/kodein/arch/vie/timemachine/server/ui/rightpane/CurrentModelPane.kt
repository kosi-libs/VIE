package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.ui.common.JsonPath
import org.kodein.arch.vie.timemachine.server.ui.common.diffJson
import org.kodein.arch.vie.timemachine.server.ui.common.nearestNodePath
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.Pane
import org.kodein.arch.vie.timemachine.server.ui.core.PaneViewModel
import org.kodein.arch.vie.timemachine.server.ui.core.whileShown
import kotlin.uuid.Uuid


internal interface CurrentModelVIE : VIE<CurrentModelVIE.Model, CurrentModelVIE.Intent, VIE.NoSignal> {

    /**
     * @property raw Whether the JSON shows as text rather than as a tree.
     * @property expanded The tree nodes that are open; the root is, to begin with.
     */
    data class Controls(
        val raw: Boolean = false,
        val expanded: Set<JsonPath> = setOf(emptyList()),
    )

    data class Model(val controls: Controls = Controls(), val projection: CurrentModelProjection = CurrentModelProjection()) : VIE.Model

    sealed interface Intent : VIE.Intent {
        data class SetRaw(val raw: Boolean) : Intent
        data class ToggleNode(val path: JsonPath) : Intent

        /** Edit & replace: opens the Composer on the current Model. */
        data object EditAndReplace : Intent
    }
}

/**
 * The latest Model of the ViewModel, evicted rows included.
 *
 * @property sequence `null` when no Model was observed yet.
 * @property json `null` when the Model is not serializable: only [asString] is shown.
 * @property rawText The JSON as text, only computed while the controls ask for it.
 * @property changedPaths The tree nodes that differ from the Model observed before; empty when that one was evicted.
 * @property changedFrom The sequence of that previous Model.
 */
internal data class CurrentModelProjection(
    val typeName: String? = null,
    val sequence: TimeMachineSequence? = null,
    val at: kotlin.time.Instant? = null,
    val asString: String? = null,
    val json: JsonElement? = null,
    val rawText: String? = null,
    val changedPaths: Set<JsonPath> = emptySet(),
    val changedFrom: TimeMachineSequence? = null,
)

/** The previous Model, if still retained, and the nodes of [json] that differ from it. */
internal fun changesFrom(
    state: TimeMachineState,
    sessionId: Uuid,
    viewModelId: Uuid,
    sequence: TimeMachineSequence,
    json: JsonElement?,
): Pair<TimeMachineSequence?, Set<JsonPath>> {
    val previous = state.sessions[sessionId]?.viewModels?.get(viewModelId)?.previousModel(sequence) ?: return null to emptySet()
    val old = (previous.message as? TimeMachineClientMessage.Observed)?.observation?.asJson
    if (old == null || json == null) return previous.sequence to emptySet()
    return previous.sequence to diffJson(old, json).mapTo(LinkedHashSet()) { json.nearestNodePath(it.path) }
}

/**
 * The latest Model of ViewModel [viewModelId] of session [sessionId] in [state], its JSON as text when [raw].
 * [changes] says what changed since the Model before: the pane remembers it for as long as the Model is the same.
 */
private fun currentModelProjection(
    state: TimeMachineState,
    sessionId: Uuid,
    viewModelId: Uuid,
    raw: Boolean,
    changes: (sequence: TimeMachineSequence, json: JsonElement?) -> Pair<TimeMachineSequence?, Set<JsonPath>> =
        { sequence, json -> changesFrom(state, sessionId, viewModelId, sequence, json) },
): CurrentModelProjection {
    val current = state.sessions[sessionId]?.viewModels?.get(viewModelId)?.currentModel ?: return CurrentModelProjection()
    val observation = current.value
    val json = observation.asJson
    val (changedFrom, changedPaths) = changes(current.sequence, json)
    return CurrentModelProjection(
        typeName = observation.typeName,
        sequence = current.sequence,
        at = observation.at,
        asString = observation.asString,
        json = json,
        rawText = if (raw) json?.pretty() else null,
        changedPaths = changedPaths,
        changedFrom = changedFrom,
    )
}

/** Whether "Edit & replace" is open on [projection]: the Model has JSON, its type serializes and nothing blocks the composer. */
internal fun canReplaceModel(state: TimeMachineState, sessionId: Uuid, viewModelId: Uuid, projection: CurrentModelProjection): Boolean {
    val session = state.sessions[sessionId]
    val vm = session?.viewModels?.get(viewModelId) ?: return false
    return projection.json != null && vm.info?.model?.isSerializable == true && composerBlock(session, vm) == null
}

internal class CurrentModelVM(
    private val inspector: Inspector,
    private val sessionId: Uuid,
    private val viewModelId: Uuid,
) : CurrentModelVIE, PaneViewModel<CurrentModelVIE.Model, CurrentModelVIE.Intent, VIE.NoSignal>(CurrentModelVIE.Model()) {

    init {
        val inputs = combine(inspector.store.state, modelFlow.map { it.controls.raw }.distinctUntilChanged()) { state, raw -> state to raw }
        project(
            inspector.whileShown(key(viewModelId), inputs) {
                // Only the latest Model's differences are ever shown.
                var cached: Pair<TimeMachineSequence, Pair<TimeMachineSequence?, Set<JsonPath>>>? = null;
                { (state, raw) ->
                    currentModelProjection(state, sessionId, viewModelId, raw) { sequence, json ->
                        cached?.takeIf { it.first == sequence }?.second ?: changesFrom(state, sessionId, viewModelId, sequence, json).also { cached = sequence to it }
                    }
                }
            },
        ) { copy(projection = it) }
    }

    override suspend fun process(intent: CurrentModelVIE.Intent) {
        when (intent) {
            is CurrentModelVIE.Intent.SetRaw -> update { copy(controls = controls.copy(raw = intent.raw)) }
            is CurrentModelVIE.Intent.ToggleNode ->
                update { copy(controls = controls.copy(expanded = controls.expanded.toggled(intent.path))) }
            CurrentModelVIE.Intent.EditAndReplace -> {
                val current = inspector.store.state.value.sessions[sessionId]?.viewModels?.get(viewModelId)?.currentModel ?: return
                val json = current.value.asJson ?: return
                inspector.selection.composeFrom(viewModelId, VIEObjectKind.Model, current.value.typeName, json.pretty())
            }
        }
    }

    companion object {
        fun key(viewModelId: Uuid): String = "current:${viewModelId.toHexString()}"
    }
}

internal fun <T> Set<T>.toggled(element: T): Set<T> = if (element in this) this - element else this + element

internal fun Inspector.currentModelPane(sessionId: Uuid, viewModelId: Uuid) =
    Pane(CurrentModelVM.key(viewModelId)) { CurrentModelVM(this, sessionId, viewModelId) }
