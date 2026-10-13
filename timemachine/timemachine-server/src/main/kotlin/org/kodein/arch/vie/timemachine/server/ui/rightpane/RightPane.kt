package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kodein.arch.vie.timemachine.server.store.ViewModelRecord
import org.kodein.arch.vie.timemachine.server.ui.common.HorizontalRule
import org.kodein.arch.vie.timemachine.server.ui.common.monoStyle
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import org.kodein.arch.vie.timemachine.server.ui.core.Target
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import kotlin.uuid.Uuid

/** What the button that folds the right pane away says it is; the shell has the one that unfolds it. */
internal const val HideRightPaneDescription = "Hide the right pane"

/**
 * The right pane of a selected ViewModel over what the store and the selection hold: its current model,
 * pinned, then the Detail and Composer tabs. With nothing to show it says what will appear.
 */
@Composable
internal fun RightPane(modifier: Modifier = Modifier) {
    val inspector = LocalInspector.current
    val selection by inspector.selection.state.collectAsState()
    val state by inspector.store.state.collectAsState()
    val resolved = selection.resolvedIn(state)
    val target = resolved.target(state)
    RightPaneLayout(
        scope = resolved.scope,
        target = target,
        tab = resolved.tab,
        onSelectTab = { inspector.selection.selectTab(it) },
        currentModel = { sessionId, viewModelId -> CurrentModelView(sessionId, viewModelId) },
        detail = { DetailView() },
        composer = { sessionId, viewModelId -> ComposerView(sessionId, viewModelId) },
        modifier = modifier,
    )
}

/**
 * The right pane's layout: the pinned current model of [target], the tabs, and the tab chosen, each a
 * slot; or what says there is nothing to show for [scope].
 */
@Composable
private fun RightPaneLayout(
    scope: InspectorScope?,
    target: Target?,
    tab: RightTab,
    onSelectTab: (RightTab) -> Unit,
    currentModel: @Composable (sessionId: Uuid, viewModelId: Uuid) -> Unit,
    detail: @Composable () -> Unit,
    composer: @Composable (sessionId: Uuid, viewModelId: Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (target == null) {
            Text(
                rightEmptyText(scope),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 40.dp),
            )
            return@Column
        }
        val sessionId = target.session.id
        val viewModelId = target.vm.id
        currentModel(sessionId, viewModelId)
        RightTabs(tab, target.vm, onSelectTab)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == RightTab.Composer) composer(sessionId, viewModelId) else detail()
        }
    }
}

private fun rightEmptyText(scope: InspectorScope?): String =
    if (scope is InspectorScope.Session) "Select a row — the current model and details of its ViewModel appear here."
    else "The current model and entry details appear here."

@Composable
private fun RightTabs(tab: RightTab, vm: ViewModelRecord, onSelect: (RightTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(InspectorDimens.tabRow).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (entry in RightTab.entries) {
            val on = tab == entry
            val badge = if (entry == RightTab.Composer) vm.interventions.size.takeIf { it > 0 }?.toString() else null
            Box(
                Modifier.fillMaxHeight().pointerHoverIcon(PointerIcon.Hand).clickable { onSelect(entry) }
                    .drawBehind {
                        if (on) drawRoundRect(
                            scheme.primary,
                            topLeft = Offset(10.dp.toPx(), size.height - 3.dp.toPx()),
                            size = Size(size.width - 20.dp.toPx(), 3.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (entry == RightTab.Detail) "Detail" else "Intervene",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                        color = if (on) scheme.primary else scheme.onSurfaceVariant,
                    )
                    badge?.let {
                        Text(
                            it, style = monoStyle(10f, scheme.onSurfaceVariant),
                            modifier = Modifier.background(scheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp),
                        )
                    }
                }
            }
        }
    }
    HorizontalRule()
}
