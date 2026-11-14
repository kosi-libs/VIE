package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.ui.common.JsonPath
import org.kodein.arch.vie.timemachine.server.ui.common.JsonViewControls
import org.kodein.arch.vie.timemachine.server.ui.common.ModelDiffs
import org.kodein.arch.vie.timemachine.server.ui.common.nearestNodePath
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.Pane
import org.kodein.arch.vie.timemachine.server.ui.core.PaneViewModel
import org.kodein.arch.vie.timemachine.server.ui.core.whileShown
import kotlin.time.Instant
import kotlin.uuid.Uuid


internal interface CurrentModelVIE : VIE<CurrentModelVIE.Model, CurrentModelVIE.Intent, VIE.NoSignal> {

    /** @property jsonView What the Model's JSON shows as: the text or the tree, and the tree's open nodes. */
    data class Controls(val jsonView: JsonViewControls = JsonViewControls())

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
 * @property json `null` when the Model is not serializable, or [truncated]: only [asString] is shown.
 * @property truncated Whether the app sent the Model without its JSON, because it was too large.
 * @property rawText The JSON as text, only computed while the controls ask for it.
 * @property changedPaths The tree nodes that differ from the Model observed before; empty when that one was evicted.
 * @property changedFrom The sequence of that previous Model.
 * @property canReplace Whether "Edit & replace" is open: `canIntervene(session, vm, observation)` on the current Model.
 */
internal data class CurrentModelProjection(
    val typeName: String? = null,
    val sequence: TimeMachineSequence? = null,
    val at: Instant? = null,
    val asString: String? = null,
    val json: JsonElement? = null,
    val rawText: String? = null,
    val changedPaths: Set<JsonPath> = emptySet(),
    val changedFrom: TimeMachineSequence? = null,
    val canReplace: Boolean = false,
    val truncated: Boolean = false,
)

/**
 * The latest Model of ViewModel [viewModelId] of session [sessionId] in [state], its JSON as text when [raw],
 * and what changed since the Model before it, from [diffs].
 */
private fun currentModelProjection(
    state: TimeMachineState,
    sessionId: Uuid,
    viewModelId: Uuid,
    raw: Boolean,
    diffs: ModelDiffs,
): CurrentModelProjection {
    diffs.retain(state)
    val session = state.sessions[sessionId]
    val vm = session?.viewModels?.get(viewModelId)
    val current = vm?.currentModel ?: return CurrentModelProjection()
    val observation = current.value
    val json = observation.asJson
    val diff = diffs.of(vm, current.sequence)
    return CurrentModelProjection(
        typeName = observation.typeName,
        sequence = current.sequence,
        at = observation.at,
        asString = observation.asString,
        json = json,
        rawText = if (raw) json?.pretty() else null,
        changedPaths = json?.let { root -> diff?.ops?.mapTo(LinkedHashSet()) { root.nearestNodePath(it.path) } }.orEmpty(),
        changedFrom = diff?.previous,
        canReplace = canIntervene(session, vm, observation),
        truncated = observation.truncated,
    )
}

internal class CurrentModelVM(
    private val inspector: Inspector,
    private val sessionId: Uuid,
    private val viewModelId: Uuid,
) : CurrentModelVIE, PaneViewModel<CurrentModelVIE.Model, CurrentModelVIE.Intent, VIE.NoSignal>(
    CurrentModelVIE.Model(projection = currentModelProjection(inspector.store.state.value, sessionId, viewModelId, raw = false, inspector.modelDiffs)),
) {

    init {
        val inputs = combine(inspector.store.state, modelFlow.map { it.controls.jsonView.raw }.distinctUntilChanged()) { state, raw -> state to raw }
        project(
            inspector.whileShown(key(viewModelId), inputs) { { (state, raw) -> currentModelProjection(state, sessionId, viewModelId, raw, inspector.modelDiffs) } },
        ) { copy(projection = it) }
    }

    override suspend fun process(intent: CurrentModelVIE.Intent) {
        when (intent) {
            is CurrentModelVIE.Intent.SetRaw -> update { copy(controls = controls.copy(jsonView = controls.jsonView.copy(raw = intent.raw))) }
            is CurrentModelVIE.Intent.ToggleNode -> update { copy(controls = controls.copy(jsonView = controls.jsonView.toggled(intent.path))) }
            CurrentModelVIE.Intent.EditAndReplace -> {
                val current = inspector.store.state.value.sessions[sessionId]?.viewModels?.get(viewModelId)?.currentModel ?: return
                inspector.selection.composeFrom(viewModelId, current.value)
            }
        }
    }

    companion object {
        fun key(viewModelId: Uuid): String = "current:${viewModelId.toHexString()}"
    }
}

internal fun Inspector.currentModelPane(sessionId: Uuid, viewModelId: Uuid) =
    Pane(CurrentModelVM.key(viewModelId), InspectorScope.ViewModel(sessionId, viewModelId)) { CurrentModelVM(this, sessionId, viewModelId) }
