package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlinx.coroutines.flow.combine
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.Pane
import org.kodein.arch.vie.timemachine.server.ui.core.PaneViewModel
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import org.kodein.arch.vie.timemachine.server.ui.core.Selection
import org.kodein.arch.vie.timemachine.server.ui.core.current
import org.kodein.arch.vie.timemachine.server.ui.core.whileShown
import kotlin.uuid.Uuid


internal interface RightPaneVIE : VIE<RightPaneVIE.Model, RightPaneVIE.Intent, VIE.NoSignal> {

    /**
     * @property scope The selected scope, as the store still holds it: what the pane says when it has no [target].
     * @property target The ViewModel the pane shows; `null` when there is none to show.
     */
    data class Model(
        val scope: InspectorScope? = null,
        val target: RightTarget? = null,
        val tab: RightTab = RightTab.Detail,
    ) : VIE.Model

    sealed interface Intent : VIE.Intent {
        data class SelectTab(val tab: RightTab) : Intent
    }
}

/**
 * The ViewModel the right pane shows, as little of it as the pane lays out: an event of its session
 * that changes none of these leaves the pane as it is.
 *
 * @property interventions How many interventions were asked of it: the Composer tab's badge.
 */
internal data class RightTarget(val sessionId: Uuid, val viewModelId: Uuid, val interventions: Int)

/** What the right pane lays out for [selection], which [state] still holds. */
private fun rightPaneModel(state: TimeMachineState, selection: Selection) = RightPaneVIE.Model(
    scope = selection.scope,
    target = selection.target(state)?.let { RightTarget(it.session.id, it.vm.id, it.vm.interventions.size) },
    tab = selection.tab,
)

/**
 * Its model says which panes the right pane composes, so it starts on the store's own: started on a
 * placeholder, the pane would say there is nothing to show before it showed the selected ViewModel.
 */
internal class RightPaneVM(private val inspector: Inspector) :
    RightPaneVIE, PaneViewModel<RightPaneVIE.Model, RightPaneVIE.Intent, VIE.NoSignal>(
        inspector.current().let { (state, selection) -> rightPaneModel(state, selection) },
    ) {

    init {
        val inputs = combine(inspector.store.state, inspector.selection.state) { state, selection -> state to selection.resolvedIn(state) }
        project(inspector.whileShown(Key, inputs) { { (state, selection) -> rightPaneModel(state, selection) } }) { it }
    }

    override suspend fun process(intent: RightPaneVIE.Intent) {
        when (intent) {
            is RightPaneVIE.Intent.SelectTab -> inspector.selection.selectTab(intent.tab)
        }
    }

    companion object {
        const val Key = "right"
    }
}

internal fun Inspector.rightPane() = Pane(RightPaneVM.Key) { RightPaneVM(this) }
