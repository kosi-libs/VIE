package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.VIEViewScope
import org.kodein.arch.vie.timemachine.server.ui.common.LocalCompact
import org.kodein.arch.vie.timemachine.server.ui.common.ScrollbarBox
import org.kodein.arch.vie.timemachine.server.ui.common.TreeRawSwitch
import org.kodein.arch.vie.timemachine.server.ui.common.formatTime
import org.kodein.arch.vie.timemachine.server.ui.common.jsonTree
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.common.rawJson
import org.kodein.arch.vie.timemachine.server.ui.common.rememberJsonLines
import org.kodein.arch.vie.timemachine.server.ui.common.treeLines
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.designsystem.ButtonVariant
import org.kodein.arch.vie.timemachine.server.ui.designsystem.CopyAction
import org.kodein.arch.vie.timemachine.server.ui.designsystem.HorizontalRule
import org.kodein.arch.vie.timemachine.server.ui.designsystem.IconAction
import org.kodein.arch.vie.timemachine.server.ui.designsystem.InspectorButton
import org.kodein.arch.vie.timemachine.server.ui.designsystem.PaneHeader
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme

/** The pinned current model's height, and the compact layout's, which leaves the tabs more room. */
private val PinnedHeight = 300.dp
private val CompactPinnedHeight = 220.dp

/** What the copy glyph of the pinned model's header says it is. */
internal const val CopyModelDescription = "Copy the current model's JSON"

/** How long the changed-node highlight holds, then fades. */
private const val HighlightHoldMillis = 1500L
private const val HighlightFadeMillis = 400

/** The pinned current model of ViewModel [viewModelId]: [CurrentModelContent] over what the store holds. */
@Composable
internal fun CurrentModelView(sessionId: Uuid, viewModelId: Uuid) {
    val inspector = LocalInspector.current
    val pane = remember(inspector, sessionId, viewModelId) { inspector.currentModelPane(sessionId, viewModelId) }
    InspectorPane(pane) { model -> CurrentModelContent(model) }
}

/**
 * The pinned current model, as [model] has it: its header, what changed since the Model before, and its
 * JSON. In the compact layout ([LocalCompact]) it is lower, its button says "Edit", and its header ends
 * with the button that folds the right pane away.
 */
@Composable
internal fun VIEViewScope<CurrentModelVIE.Intent, NoSignal>.CurrentModelContent(model: CurrentModelVIE.Model) {
    val scheme = MaterialTheme.colorScheme
    val projection = model.projection
    val compact = LocalCompact.current
    Column(Modifier.fillMaxWidth().height(if (compact) CompactPinnedHeight else PinnedHeight)) {
        PaneHeader(
            "Current model",
            count = projection.sequence?.let { "#$it${projection.at?.let { at -> " · " + formatTime(at) }.orEmpty()}" },
            glyph = Glyph.PushPin, glyphTint = InspectorTheme.colors.model,
        ) {
            if (projection.json != null) {
                TreeRawSwitch(model.controls.raw) { post(CurrentModelVIE.Intent.SetRaw(it)) }
                CopyAction({ projection.json.pretty() }, CopyModelDescription)
                InspectorButton(
                    if (compact) "Edit" else "Edit & replace",
                    onClick = { post(CurrentModelVIE.Intent.EditAndReplace) },
                    glyph = Glyph.Edit, variant = ButtonVariant.Tonal, enabled = projection.canReplace,
                )
            }
            if (compact) {
                val viewState = LocalInspectorViewState.current
                IconAction(Glyph.RightPanelClose, HideRightPaneDescription, onClick = { viewState.rightCollapsed = true }, size = 18.dp)
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
                Modifier.fillMaxWidth().padding(start = InspectorDimens.paneHorizontalPadding, end = InspectorDimens.paneHorizontalPadding, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(InspectorTheme.colors.modelContainer, RoundedCornerShape(2.dp)).border(1.dp, InspectorTheme.colors.model, RoundedCornerShape(2.dp)))
                Text(
                    "highlighted: changed in #${projection.sequence} vs #${projection.changedFrom}",
                    style = MaterialTheme.typography.bodySmall, color = InspectorTheme.colors.model, maxLines = 1,
                )
            }
        }
        // The lines of the one that shows, the tree or the text: the other has none.
        val json = projection.json
        val raw = model.controls.raw
        val expanded = model.controls.expanded
        val treeLines = remember(json, expanded, raw) { if (json == null || raw) emptyList() else treeLines(json, expanded) }
        val textLines = rememberJsonLines(if (raw) projection.rawText.orEmpty() else "")
        val list = rememberLazyListState()
        ScrollbarBox(rememberScrollbarAdapter(list), Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                when {
                    projection.sequence == null -> item {
                        Text("No model yet.", style = InspectorTheme.text.body, color = scheme.outline, modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp))
                    }
                    json == null -> item {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(projection.asString.orEmpty(), style = InspectorTheme.text.code, color = scheme.onSurface)
                            Text(
                                if (projection.truncated) TooLargeNote
                                else "toString only — this model has no JSON, so it can't be shown as a tree or replaced.",
                                style = InspectorTheme.text.note, color = scheme.outline,
                            )
                        }
                    }
                    raw -> rawJson(textLines)
                    else -> jsonTree(treeLines, changed = projection.changedPaths, highlight = { fade.value }) { post(CurrentModelVIE.Intent.ToggleNode(it)) }
                }
            }
        }
        HorizontalRule(color = InspectorTheme.colors.strongOutline)
    }
}
