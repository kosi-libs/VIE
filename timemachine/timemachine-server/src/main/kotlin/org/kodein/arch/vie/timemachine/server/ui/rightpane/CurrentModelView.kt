package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.kodein.arch.vie.timemachine.server.ui.common.HorizontalRule
import org.kodein.arch.vie.timemachine.server.ui.common.JsonTree
import org.kodein.arch.vie.timemachine.server.ui.common.LocalCompact
import org.kodein.arch.vie.timemachine.server.ui.common.RawJson
import org.kodein.arch.vie.timemachine.server.ui.common.TreeRawSwitch
import org.kodein.arch.vie.timemachine.server.ui.common.copyToClipboard
import org.kodein.arch.vie.timemachine.server.ui.common.formatTime
import org.kodein.arch.vie.timemachine.server.ui.common.monoStyle
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol
import kotlin.uuid.Uuid

/** The pinned current model's height, and the compact layout's, which leaves the tabs more room. */
private val PinnedHeight = 300.dp
private val CompactPinnedHeight = 220.dp

/** How long the changed-node highlight holds, then fades. */
private const val HighlightHoldMillis = 1500L
private const val HighlightFadeMillis = 400

/** The pinned current model of ViewModel [viewModelId]: [CurrentModelContent] over what the store holds. */
@Composable
internal fun CurrentModelView(sessionId: Uuid, viewModelId: Uuid) {
    val inspector = LocalInspector.current
    val pane = remember(inspector, sessionId, viewModelId) { inspector.currentModelPane(sessionId, viewModelId) }
    InspectorPane(pane) { model ->
        val canReplace = canReplaceModel(inspector.store.state.value, sessionId, viewModelId, model.projection)
        CurrentModelContent(model, canReplace) { post(it) }
    }
}

/**
 * The pinned current model, as [model] has it: its header, what changed since the Model before, and its
 * JSON. [canReplace] opens Edit & replace. In the compact layout ([LocalCompact]) it is lower, its button
 * says "Edit", and its header ends with the button that folds the right pane away.
 */
@Composable
private fun CurrentModelContent(model: CurrentModelVIE.Model, canReplace: Boolean, post: (CurrentModelVIE.Intent) -> Unit) {
    run {
        val scheme = MaterialTheme.colorScheme
        val projection = model.projection
        val compact = LocalCompact.current
        Column(Modifier.fillMaxWidth().height(if (compact) CompactPinnedHeight else PinnedHeight)) {
            Row(
                Modifier.fillMaxWidth().height(InspectorDimens.paneHeader).padding(start = 14.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol("push_pin", size = 16.dp, tint = InspectorTheme.colors.model)
                Text("Current model", style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp), color = scheme.onSurface, maxLines = 1)
                projection.sequence?.let {
                    Text(
                        "#$it${projection.at?.let { at -> " · " + formatTime(at) }.orEmpty()}",
                        style = monoStyle(11f, scheme.outline), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (projection.json != null) {
                    TreeRawSwitch(model.controls.raw) { post(CurrentModelVIE.Intent.SetRaw(it)) }
                    Symbol(
                        "content_copy", size = 16.dp, tint = scheme.onSurfaceVariant,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { copyToClipboard(projection.json.pretty()) }.padding(5.dp),
                    )
                    EditButton(if (compact) "Edit" else "Edit & replace", canReplace) { post(CurrentModelVIE.Intent.EditAndReplace) }
                }
                if (compact) {
                    val viewState = LocalInspectorViewState.current
                    Symbol(
                        "right_panel_close", size = 18.dp, tint = scheme.onSurfaceVariant,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            .clickable(role = Role.Button) { viewState.rightCollapsed = true }
                            .semantics { contentDescription = HideRightPaneDescription }
                            .padding(4.dp),
                    )
                }
            }
            val fade = remember(projection.sequence) { Animatable(1f) }
            LaunchedEffect(projection.sequence) {
                fade.snapTo(1f)
                delay(HighlightHoldMillis)
                fade.animateTo(0f, tween(HighlightFadeMillis))
            }
            if (projection.changedFrom != null && projection.json != null && projection.changedPaths.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(InspectorTheme.colors.modelContainer, RoundedCornerShape(2.dp)).border(1.dp, InspectorTheme.colors.model, RoundedCornerShape(2.dp)))
                    Text(
                        "highlighted: changed in #${projection.sequence} vs #${projection.changedFrom}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = InspectorTheme.colors.model, maxLines = 1,
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                when {
                    projection.sequence == null -> Text("No model yet.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = scheme.outline, modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp))
                    projection.json == null -> Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(projection.asString.orEmpty(), style = monoStyle(12f, scheme.onSurface))
                        Text(
                            "toString only — this model has no JSON, so it can't be shown as a tree or replaced.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = scheme.outline,
                        )
                    }
                    model.controls.raw -> RawJson(projection.rawText.orEmpty())
                    else -> JsonTree(
                        projection.json, model.controls.expanded,
                        changed = projection.changedPaths, highlight = fade.value,
                    ) { post(CurrentModelVIE.Intent.ToggleNode(it)) }
                }
            }
            HorizontalRule(InspectorTheme.colors.strongOutline)
        }
    }
}

@Composable
private fun EditButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val foreground = if (enabled) scheme.onPrimaryContainer else scheme.outline
    Row(
        Modifier.height(26.dp).background(if (enabled) scheme.primaryContainer else scheme.surfaceContainerHigh, InspectorPillShape)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier)
            .padding(start = 8.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol("edit", size = 14.dp, tint = foreground)
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp), color = foreground, maxLines = 1)
    }
}
