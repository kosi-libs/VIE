package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import org.kodein.arch.vie.timemachine.server.ui.designsystem.EmptyState
import org.kodein.arch.vie.timemachine.server.ui.designsystem.TabRow
import kotlin.uuid.Uuid

/** What the button that folds the right pane away says it is; the shell has the one that unfolds it. */
internal const val HideRightPaneDescription = "Hide the right pane"

/**
 * The right pane of a selected ViewModel over what its pane projects of the store and the selection:
 * its current model, pinned, then the Detail and Composer tabs. With nothing to show it says what will
 * appear.
 */
@Composable
internal fun RightPane(modifier: Modifier = Modifier) {
    val inspector = LocalInspector.current
    val pane = remember(inspector) { inspector.rightPane() }
    InspectorPane(pane) { model ->
        RightPaneLayout(
            scope = model.scope,
            target = model.target,
            tab = model.tab,
            onSelectTab = { post(RightPaneVIE.Intent.SelectTab(it)) },
            currentModel = { sessionId, viewModelId -> CurrentModelView(sessionId, viewModelId) },
            detail = { DetailView() },
            composer = { sessionId, viewModelId -> ComposerView(sessionId, viewModelId) },
            modifier = modifier,
        )
    }
}

/**
 * The right pane's layout: the pinned current model of [target], the tabs, and the tab chosen, each a
 * slot; or what says there is nothing to show for [scope].
 */
@Composable
private fun RightPaneLayout(
    scope: InspectorScope?,
    target: RightTarget?,
    tab: RightTab,
    onSelectTab: (RightTab) -> Unit,
    currentModel: @Composable (sessionId: Uuid, viewModelId: Uuid) -> Unit,
    detail: @Composable () -> Unit,
    composer: @Composable (sessionId: Uuid, viewModelId: Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (target == null) {
            EmptyState(rightEmptyText(scope), Modifier.fillMaxWidth())
            return@Column
        }
        currentModel(target.sessionId, target.viewModelId)
        TabRow(
            RightTab.entries, selected = tab, onSelect = onSelectTab,
            label = { when (it) { RightTab.Detail -> "Detail"; RightTab.Composer -> "Intervene" } },
            badge = { when (it) { RightTab.Detail -> null; RightTab.Composer -> target.interventions.takeIf { n -> n > 0 }?.toString() } },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == RightTab.Composer) composer(target.sessionId, target.viewModelId) else detail()
        }
    }
}

private fun rightEmptyText(scope: InspectorScope?): String =
    if (scope is InspectorScope.Session) "Select a row — the current model and details of its ViewModel appear here."
    else "The current model and entry details appear here."
