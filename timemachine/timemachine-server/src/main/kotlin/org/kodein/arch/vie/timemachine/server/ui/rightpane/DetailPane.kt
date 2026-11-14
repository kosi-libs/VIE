package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.server.store.Row
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.ui.common.JsonPath
import org.kodein.arch.vie.timemachine.server.ui.common.JsonViewControls
import org.kodein.arch.vie.timemachine.server.ui.common.ModelDiff
import org.kodein.arch.vie.timemachine.server.ui.common.ModelDiffs
import org.kodein.arch.vie.timemachine.server.ui.common.interventionNumber
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.Pane
import org.kodein.arch.vie.timemachine.server.ui.core.PaneViewModel
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import org.kodein.arch.vie.timemachine.server.ui.core.Selection
import org.kodein.arch.vie.timemachine.server.ui.core.current
import org.kodein.arch.vie.timemachine.server.ui.core.whileShown
import kotlin.uuid.Uuid


internal interface DetailVIE : VIE<DetailVIE.Model, DetailVIE.Intent, VIE.NoSignal> {

    /** @property jsonView What the row's JSON shows as: the text or the tree, and the tree's open nodes. */
    data class Controls(val jsonView: JsonViewControls = JsonViewControls())

    data class Model(val controls: Controls = Controls(), val projection: DetailProjection = DetailProjection()) : VIE.Model

    sealed interface Intent : VIE.Intent {
        data class SetRaw(val raw: Boolean) : Intent
        data class ToggleNode(val path: JsonPath) : Intent

        /** Edit & intervene: opens the Composer on the selected row. */
        data object EditAndIntervene : Intent

        /** The intervened banner's Show: the Composer tab, where the interventions are. */
        data object ShowInterventions : Intent
    }
}

/**
 * The selected row, `null` when none is or the store no longer retains it.
 *
 * @property viewModelId The ViewModel it is in.
 * @property diff For a Model, its differences from the Model observed before, when that one is retained.
 * @property interventionNumber For an intervened observation, its number within its ViewModel.
 * @property canIntervene Whether "Edit & intervene" is open on the row: `canIntervene(session, vm, observation)` on its observation.
 */
internal data class DetailProjection(
    val row: Row? = null,
    val viewModelId: Uuid? = null,
    val diff: ModelDiff? = null,
    val interventionNumber: Int? = null,
    val interventionKind: VIEObjectKind? = null,
    val canIntervene: Boolean = false,
    val rawText: String? = null,
) {

    /** The row's observation: `null` for a bookend or a drop report. */
    val observation: VIEObservation? get() = (row?.message as? TimeMachineClientMessage.Observed)?.observation
}

/**
 * The selected row of [selection] in [state], with what Detail says of it: its diff from [diffs]; its JSON as text
 * when [raw]. Nothing when none is selected or the store no longer retains it.
 */
internal fun detailProjection(state: TimeMachineState, selection: Selection, raw: Boolean, diffs: ModelDiffs): DetailProjection {
    diffs.retain(state)
    val sequence = selection.row
    val session = selection.scope?.let { state.sessions[it.sessionId] }
    val vm = sequence?.let { s -> session?.viewModels?.values?.firstOrNull { it.holds(s) } } ?: return DetailProjection()
    val row = vm.row(sequence) ?: return DetailProjection()
    val observation = (row.message as? TimeMachineClientMessage.Observed)?.observation
    return DetailProjection(
        row = row,
        viewModelId = vm.id,
        diff = if (observation?.kind == VIEObjectKind.Model) diffs.of(vm, sequence) else null,
        interventionNumber = observation?.interventionId?.let { vm.interventionNumber(it) },
        interventionKind = observation?.interventionId?.let { vm.interventions[it]?.kind },
        canIntervene = observation != null && canIntervene(session, vm, observation),
        rawText = if (raw) observation?.asJson?.pretty() else null,
    )
}

internal class DetailVM(
    private val inspector: Inspector,
) : DetailVIE, PaneViewModel<DetailVIE.Model, DetailVIE.Intent, VIE.NoSignal>(
    inspector.current().let { (state, selection) -> DetailVIE.Model(projection = detailProjection(state, selection, raw = false, inspector.modelDiffs)) },
) {

    init {
        val inputs = combine(
            inspector.store.state,
            inspector.selection.state,
            modelFlow.map { it.controls.jsonView.raw }.distinctUntilChanged(),
        ) { state, selection, raw -> Triple(state, selection.resolvedIn(state), raw) }
        project(inspector.whileShown(Key, inputs) { { (state, selection, raw) -> detailProjection(state, selection, raw, inspector.modelDiffs) } }) { copy(projection = it) }
    }

    override suspend fun process(intent: DetailVIE.Intent) {
        when (intent) {
            is DetailVIE.Intent.SetRaw -> update { copy(controls = controls.copy(jsonView = controls.jsonView.copy(raw = intent.raw))) }
            is DetailVIE.Intent.ToggleNode -> update { copy(controls = controls.copy(jsonView = controls.jsonView.toggled(intent.path))) }
            DetailVIE.Intent.EditAndIntervene -> {
                val projection = model.projection
                if (projection.canIntervene) inspector.selection.composeFrom(projection.viewModelId ?: return, projection.observation ?: return)
            }
            DetailVIE.Intent.ShowInterventions -> inspector.selection.selectTab(RightTab.Composer)
        }
    }

    companion object {
        const val Key = "detail"
    }
}

internal fun Inspector.detailPane() = Pane(DetailVM.Key) { DetailVM(this) }
