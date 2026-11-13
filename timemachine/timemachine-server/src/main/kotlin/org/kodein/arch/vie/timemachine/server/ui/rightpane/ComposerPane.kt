package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.store.Intervention
import org.kodein.arch.vie.timemachine.server.store.InterventionStatus
import org.kodein.arch.vie.timemachine.server.store.SessionRecord
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.store.ViewModelRecord
import org.kodein.arch.vie.timemachine.server.ui.common.clock
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.common.type
import org.kodein.arch.vie.timemachine.server.ui.common.shortViewModelId
import org.kodein.arch.vie.timemachine.server.ui.common.viewModelLabel
import org.kodein.arch.vie.timemachine.server.ui.common.viewModelName
import org.kodein.arch.vie.timemachine.server.ui.core.ComposerRequest
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.Pane
import org.kodein.arch.vie.timemachine.server.ui.core.PaneViewModel
import org.kodein.arch.vie.timemachine.server.ui.core.Selection
import org.kodein.arch.vie.timemachine.server.ui.core.current
import org.kodein.arch.vie.timemachine.server.ui.core.whileShown
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid


internal interface ComposerVIE : VIE<ComposerVIE.Model, ComposerVIE.Intent, VIE.NoSignal> {

    /**
     * What the developer sets.
     *
     * @property chosenType The explicit choice of type, `null` to let the composer pick one.
     * @property draft The text the developer edited, `null` while the editor shows the prefill: kind
     *   and type changes, reset and a request replace it.
     * @property typeMenuOpen Whether the type dropdown is open.
     * @property requestId The latest intervention sent from here, whose result the card shows.
     */
    data class Controls(
        val kind: VIEObjectKind = VIEObjectKind.Model,
        val chosenType: String? = null,
        val draft: String? = null,
        val typeMenuOpen: Boolean = false,
        val requestId: Uuid? = null,
    )

    data class Model(val controls: Controls = Controls(), val projection: ComposerProjection = ComposerProjection()) : VIE.Model {

        /**
         * What the editor shows: the draft, else the prefill. The draft is the controls', set as it is
         * typed, so the editor never waits for a projection, and never shows a text older than the draft.
         */
        val editorText: String get() = controls.draft ?: projection.target?.prefill?.json.orEmpty()
    }

    sealed interface Intent : VIE.Intent {
        data class SetKind(val kind: VIEObjectKind) : Intent
        data class SetType(val type: String) : Intent
        data class SetText(val text: String) : Intent
        data class SetTypeMenu(val open: Boolean) : Intent
        data object Format : Intent
        data object Reset : Intent
        data object Send : Intent

        /** Sends intervention [requestId] of the history again. */
        data class Resend(val requestId: Uuid) : Intent

        /** Puts intervention [requestId] of the history in the editor. */
        data class LoadFromHistory(val requestId: Uuid) : Intent

        /** Shows the result card of intervention [requestId] of the history. */
        data class ShowResult(val requestId: Uuid) : Intent

        /** A result card's link to the intervened row. */
        data class SelectRow(val sequence: TimeMachineSequence) : Intent

        /** Posted by the pane itself, for a request the holder has for this ViewModel. */
        data class ApplyRequest(val request: ComposerRequest) : Intent
    }
}

/**
 * @property target What the composer offers; `null` before the store knows the ViewModel.
 * @property parse What the parser says of the text the editor had when this was projected.
 * @property canSend Whether Send is enabled.
 * @property current The intervention sent from here last.
 * @property history The [MaxRecentInterventions] latest interventions asked of the ViewModel, newest first.
 * @property olderInterventions How many interventions it was asked before those of [history].
 * @property sessionName The app the ViewModel runs in, as the result card names it.
 * @property name The ViewModel as the Target line names it: its simple type name, else its short identifier.
 * @property shortId Its short identifier, `unknown` while no info describes it.
 * @property blockText Why the composer is disabled, in plain words; `null` when it is not.
 * @property unserializable The first kind whose type is not `@Serializable`, with its type name.
 * @property timeout How long the store waits for an intervention's answer and row, as the result card says it.
 */
internal data class ComposerProjection(
    val sessionName: String = "",
    val name: String = "",
    val shortId: String = "",
    val blockText: String? = null,
    val unserializable: Pair<VIEObjectKind, String>? = null,
    val target: ComposerTarget? = null,
    val parse: JsonParse = JsonParse.Valid(JsonObject(emptyMap())),
    val canSend: Boolean = false,
    val current: InterventionView? = null,
    val history: List<InterventionView> = emptyList(),
    val olderInterventions: Int = 0,
    val timeout: Duration = Duration.ZERO,
)

/** How many interventions the composer lists as recent. */
internal const val MaxRecentInterventions = 20

/**
 * An intervention as the result card shows it.
 *
 * @property number Its number within its ViewModel, from 1.
 * @property rowRetained For a success, whether the intervened row is still retained: once it is
 *   gone, the link says so.
 */
internal data class InterventionView(
    val number: Int,
    val requestId: Uuid,
    val kind: VIEObjectKind,
    val typeName: String,
    val asJson: JsonElement,
    val requestedAt: Instant,
    val status: InterventionStatus,
    val rowRetained: Boolean,
)

/**
 * What the composer of ViewModel [viewModelId] of session [sessionId] shows, given what the store holds, what is selected
 * and what the developer set. [timeout] is the store's, which its state does not carry.
 */
private fun composerProjection(
    state: TimeMachineState,
    selection: Selection,
    controls: ComposerVIE.Controls,
    sessionId: Uuid,
    viewModelId: Uuid,
    timeout: Duration,
): ComposerProjection {
    val session = state.sessions[sessionId]
    val vm = session?.viewModels?.get(viewModelId)
    val target = composerTarget(session, vm, controls.kind, controls.chosenType, selection.selectedType(vm))
    val parse = parseJson(controls.draft ?: target.prefill?.json.orEmpty())
    val interventions = vm?.interventions?.values.orEmpty().toList()
    // An intervention's number is its place among those of its ViewModel, from 1.
    fun view(index: Int): InterventionView = with(interventions[index]) {
        InterventionView(
            number = index + 1,
            requestId = requestId,
            kind = kind,
            typeName = typeName,
            asJson = asJson,
            requestedAt = requestedAt,
            status = status,
            rowRetained = (status as? InterventionStatus.Succeeded)?.let { vm?.holds(it.sequence) } ?: false,
        )
    }
    val older = (interventions.size - MaxRecentInterventions).coerceAtLeast(0)
    return ComposerProjection(
        sessionName = session?.session?.name.orEmpty(),
        name = vm?.let { viewModelName(it.info?.typeName, it.id) }.orEmpty(),
        shortId = if (vm?.info == null) "unknown" else shortViewModelId(vm.id),
        blockText = blockText(session, vm, target.block),
        unserializable = vm?.info?.let { info -> VIEObjectKind.entries.firstOrNull { !info.type(it).isSerializable }?.let { it to info.type(it).typeName } },
        target = target,
        parse = parse,
        canSend = target.canSend(parse),
        current = controls.requestId?.let { id -> interventions.indexOfFirst { it.requestId == id } }?.takeIf { it >= 0 }?.let(::view),
        history = (interventions.lastIndex downTo older).map(::view),
        olderInterventions = older,
        timeout = timeout,
    )
}

private fun blockText(session: SessionRecord?, vm: ViewModelRecord?, block: ComposerBlock?): String? {
    if (block == null || session == null || vm == null) return null
    val app = session.session.name
    val name = viewModelLabel(vm.info?.typeName, vm.id)
    return when (block) {
        ComposerBlock.UnknownViewModel ->
            if (vm.closed) "$name closed before $app could identify it — it can’t receive interventions."
            else "$name isn’t identified yet — $app is being asked for its type. Interventions open once it is."
        ComposerBlock.SessionDisconnected ->
            "$app disconnected${session.disconnectedAt?.let { " at ${clock(it)}" }.orEmpty()} — nothing can reach the app until it reconnects."
        ComposerBlock.ViewModelClosed ->
            "$name closed${vm.closedAt?.let { " at ${clock(it)}" }.orEmpty()} — it can’t receive interventions."
    }
}

internal class ComposerVM(
    private val inspector: Inspector,
    private val sessionId: Uuid,
    private val viewModelId: Uuid,
) : ComposerVIE, PaneViewModel<ComposerVIE.Model, ComposerVIE.Intent, VIE.NoSignal>(firstModel(inspector, sessionId, viewModelId)) {

    init {
        // Applied whether the pane is shown or not: a request made while it is hidden waits in the
        // holder until this runs. It reads without consuming; `process` consumes.
        viewModelScope.launch {
            inspector.selection.state
                .map { selection -> selection.composerRequest?.takeIf { it.viewModelId == viewModelId } }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { post(ComposerVIE.Intent.ApplyRequest(it)) }
        }
        val inputs = combine(
            inspector.store.state,
            inspector.selection.state,
            modelFlow.map { it.controls }.distinctUntilChanged(),
        ) { state, selection, controls -> Triple(state, selection.resolvedIn(state), controls) }
        project(inspector.whileShown(key(viewModelId), inputs) { { (state, selection, controls) -> composerProjection(state, selection, controls, sessionId, viewModelId, inspector.store.interventionTimeout) } }) {
            copy(projection = it)
        }
    }

    override suspend fun process(intent: ComposerVIE.Intent) {
        when (intent) {
            is ComposerVIE.Intent.SetKind -> controls { copy(kind = intent.kind, chosenType = null, draft = null, typeMenuOpen = false) }
            is ComposerVIE.Intent.SetType -> controls { copy(chosenType = intent.type, draft = null, typeMenuOpen = false) }
            is ComposerVIE.Intent.SetTypeMenu -> controls { copy(typeMenuOpen = intent.open) }
            is ComposerVIE.Intent.SetText -> controls { copy(draft = intent.text) }
            ComposerVIE.Intent.Format -> {
                val parse = parseJson(effectiveText())
                if (parse is JsonParse.Valid) controls { copy(draft = parse.element.pretty()) }
            }
            ComposerVIE.Intent.Reset -> controls { copy(draft = null) }
            ComposerVIE.Intent.Send -> send()
            is ComposerVIE.Intent.LoadFromHistory -> load(intent.requestId)
            is ComposerVIE.Intent.Resend -> if (load(intent.requestId)) send()
            is ComposerVIE.Intent.ShowResult -> controls { copy(requestId = intent.requestId) }
            is ComposerVIE.Intent.SelectRow -> inspector.selection.selectRow(intent.sequence)
            is ComposerVIE.Intent.ApplyRequest -> {
                applyRequest(intent.request)
                // Then the holder forgets it: if a newer one replaced it meanwhile, that one is applied too.
                inspector.selection.takeRequest(viewModelId)?.takeIf { it != intent.request }?.let { applyRequest(it) }
            }
        }
    }

    private suspend fun controls(change: ComposerVIE.Controls.() -> ComposerVIE.Controls) = update { copy(controls = controls.change()) }

    private suspend fun applyRequest(request: ComposerRequest) = controls { applying(request) }

    private suspend fun load(requestId: Uuid): Boolean {
        val intervention = inspector.store.state.value.sessions[sessionId]?.viewModels?.get(viewModelId)?.interventions?.get(requestId) ?: return false
        controls { copy(kind = intervention.kind, chosenType = intervention.typeName, draft = intervention.asJson.pretty()) }
        return true
    }

    /** The target for the current controls, read from the store now rather than from a projection. */
    private fun currentTarget(): ComposerTarget {
        val state = inspector.store.state.value
        val session = state.sessions[sessionId]
        val vm = session?.viewModels?.get(viewModelId)
        val controls = model.controls
        return composerTarget(session, vm, controls.kind, controls.chosenType, inspector.selection.state.value.resolvedIn(state).selectedType(vm))
    }

    private fun effectiveText(): String = model.controls.draft ?: currentTarget().prefill?.json.orEmpty()

    private suspend fun send() {
        val target = currentTarget()
        val parse = parseJson(effectiveText())
        if (!target.canSend(parse)) return
        val requestId = inspector.store.intervene(sessionId, viewModelId, target.kind, target.type!!, (parse as JsonParse.Valid).element)
        controls { copy(requestId = requestId) }
    }

    companion object {
        fun key(viewModelId: Uuid): String = "composer:${viewModelId.toHexString()}"

        /**
         * What the composer shows of the store as it is created. A request the holder has for its ViewModel
         * is in it, since a request is what opens a composer: `init` applies it again, which changes
         * nothing, and consumes it.
         */
        private fun firstModel(inspector: Inspector, sessionId: Uuid, viewModelId: Uuid): ComposerVIE.Model {
            val (state, selection) = inspector.current()
            val request = selection.composerRequest?.takeIf { it.viewModelId == viewModelId }
            val controls = ComposerVIE.Controls().let { if (request == null) it else it.applying(request) }
            return ComposerVIE.Model(controls, composerProjection(state, selection, controls, sessionId, viewModelId, inspector.store.interventionTimeout))
        }
    }
}

/** These controls once [request] is applied: its kind, its type, and its JSON as the draft. */
private fun ComposerVIE.Controls.applying(request: ComposerRequest): ComposerVIE.Controls =
    copy(kind = request.kind, chosenType = request.type, draft = request.json)

/** The kind and type of the selected row, if it is an observation of [vm]. */
private fun Selection.selectedType(vm: ViewModelRecord?): Pair<VIEObjectKind, String>? {
    val observation = (row?.let { vm?.row(it) }?.message as? TimeMachineClientMessage.Observed)?.observation ?: return null
    return observation.kind to observation.typeName
}

internal fun Inspector.composerPane(sessionId: Uuid, viewModelId: Uuid) =
    Pane(ComposerVM.key(viewModelId), InspectorScope.ViewModel(sessionId, viewModelId)) { ComposerVM(this, sessionId, viewModelId) }
