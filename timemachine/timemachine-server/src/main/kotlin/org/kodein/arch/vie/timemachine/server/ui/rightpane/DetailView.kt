package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.VIEViewScope
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.server.ui.common.DiffText
import org.kodein.arch.vie.timemachine.server.ui.common.KindBadge
import org.kodein.arch.vie.timemachine.server.ui.common.ModelDiff
import org.kodein.arch.vie.timemachine.server.ui.common.ScrollbarBox
import org.kodein.arch.vie.timemachine.server.ui.common.TreeRawSwitch
import org.kodein.arch.vie.timemachine.server.ui.common.formatTime
import org.kodein.arch.vie.timemachine.server.ui.common.jsonTree
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.common.rawJson
import org.kodein.arch.vie.timemachine.server.ui.common.rememberJsonLines
import org.kodein.arch.vie.timemachine.server.ui.common.style
import org.kodein.arch.vie.timemachine.server.ui.common.treeLines
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.designsystem.ButtonVariant
import org.kodein.arch.vie.timemachine.server.ui.designsystem.Callout
import org.kodein.arch.vie.timemachine.server.ui.designsystem.CalloutTone
import org.kodein.arch.vie.timemachine.server.ui.designsystem.EmptyState
import org.kodein.arch.vie.timemachine.server.ui.designsystem.InspectorButton
import org.kodein.arch.vie.timemachine.server.ui.designsystem.LinkAction
import org.kodein.arch.vie.timemachine.server.ui.designsystem.SectionLabel
import org.kodein.arch.vie.timemachine.server.ui.designsystem.SerialName
import org.kodein.arch.vie.timemachine.server.ui.designsystem.rememberCopyFeedback
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol

/** The Detail tab: [DetailPaneContent] over what the store holds and the window has selected. */
@Composable
internal fun DetailView() {
    val inspector = LocalInspector.current
    val pane = remember(inspector) { inspector.detailPane() }
    InspectorPane(pane) { model -> DetailPaneContent(model) }
}

/** The Detail tab, as [model] has it: the selected entry, or what to do to select one. */
@Composable
internal fun VIEViewScope<DetailVIE.Intent, NoSignal>.DetailPaneContent(model: DetailVIE.Model) {
    val row = model.projection.row
    val observation = (row?.message as? TimeMachineClientMessage.Observed)?.observation
    if (row == null || observation == null) NoEntrySelected()
    else DetailContent(model, observation, row.sequence) { post(it) }
}

@Composable
private fun NoEntrySelected() {
    EmptyState("No entry selected", Modifier.fillMaxSize(), description = "Click a row, or use ↑ ↓ to step through the timeline.")
}

@Composable
private fun DetailContent(model: DetailVIE.Model, observation: VIEObservation, sequence: TimeMachineSequence, post: (DetailVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val projection = model.projection
    val kind = observation.kind
    // The lines of the one that shows, the tree or the text: the other has none.
    val json = observation.asJson
    val raw = model.controls.jsonView.raw
    val expanded = model.controls.jsonView.expanded
    val treeLines = remember(json, expanded, raw) { if (json == null || raw) emptyList() else treeLines(json, expanded) }
    val textLines = rememberJsonLines(if (raw) projection.rawText.orEmpty() else "")
    // One lazy list for the whole tab: its sections are an item each, and the JSON an item per line, so
    // that a large document only composes the lines in view.
    val list = rememberLazyListState()
    ScrollbarBox(rememberScrollbarAdapter(list), Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(start = InspectorDimens.paneHorizontalPadding, end = InspectorDimens.paneHorizontalPadding, top = 12.dp, bottom = 16.dp),
        ) {
            item(key = "header", contentType = "header") { DetailHeader(observation, sequence, projection, post) }
            projection.diff?.let { diff -> item(key = "changes", contentType = "changes") { DetailChanges(diff, Modifier.padding(top = SectionGap)) } }
            item(key = "toString", contentType = "toString") {
                Column(Modifier.padding(top = SectionGap), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("TOSTRING")
                    Text(observation.asString, style = InspectorTheme.text.codeRow.copy(lineHeight = 17.sp), color = scheme.onSurface)
                }
            }
            if (json == null && observation.truncated) {
                item(key = "too-large", contentType = "too-large") {
                    Text(
                        TooLargeNote, style = InspectorTheme.text.note, color = scheme.outline,
                        modifier = Modifier.padding(top = SectionGap),
                    )
                }
            }
            if (json != null) {
                item(key = "json-title", contentType = "json-title") {
                    Row(Modifier.padding(top = SectionGap, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(if (kind == VIEObjectKind.Model) "FULL MODEL" else "JSON", Modifier.weight(1f))
                        TreeRawSwitch(raw) { post(DetailVIE.Intent.SetRaw(it)) }
                    }
                }
                if (raw) rawJson(textLines, framed = true)
                else jsonTree(treeLines, framed = true) { post(DetailVIE.Intent.ToggleNode(it)) }
            }
        }
    }
}

/** What the selected row is: its kind and direction, its type, whether the inspector sent it, and what can be done with it. */
@Composable
private fun DetailHeader(observation: VIEObservation, sequence: TimeMachineSequence, projection: DetailProjection, post: (DetailVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val copied = rememberCopyFeedback()
    val kind = observation.kind
    val kindStyle = kind.style()
    val json = observation.asJson
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KindBadge(kind)
            Symbol(kindStyle.direction.glyph, size = 16.dp, tint = kindStyle.color(colors), mirrored = kindStyle.direction.mirrored)
            Text(kindStyle.direction.label, style = InspectorTheme.text.body, color = scheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("#$sequence · ${formatTime(observation.at)}", style = InspectorTheme.text.codeRow, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        SerialName(observation.typeName, InspectorTheme.text.code, maxLines = Int.MAX_VALUE)
        projection.interventionNumber?.let { number ->
            Callout(
                CalloutTone.Intervened, Glyph.TouchApp, Modifier.fillMaxWidth(),
                action = { LinkAction("Show", onClick = { post(DetailVIE.Intent.ShowInterventions) }, color = colors.intervened) },
            ) {
                Text(
                    buildAnnotatedString {
                        append("Sent by the inspector — ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("intervention #$number") }
                        projection.interventionKind?.let { append(" · ${it.style().verb}") }
                    },
                    style = InspectorTheme.text.body, color = scheme.onSurface,
                )
            }
        }
        if (json != null) {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (projection.canIntervene) {
                    InspectorButton("Edit & intervene", onClick = { post(DetailVIE.Intent.EditAndIntervene) }, glyph = Glyph.EditNote, variant = ButtonVariant.Tonal)
                }
                InspectorButton("Copy JSON", onClick = { copied.copy(json.pretty()) }, glyph = copied.glyph, variant = ButtonVariant.Outlined)
            }
        }
    }
}

/** What differs from the Model observed before. */
@Composable
private fun DetailChanges(diff: ModelDiff, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("CHANGES VS #${diff.previous}")
        Column(
            Modifier.fillMaxWidth().background(scheme.surfaceContainer, RoundedCornerShape(8.dp))
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(8.dp)).padding(vertical = 6.dp),
        ) {
            if (diff.ops.isEmpty()) Text("No differences.", style = InspectorTheme.text.body, color = scheme.outline, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
            diff.ops.forEach { Box(Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) { DiffText(it) } }
        }
    }
}

/** What separates two sections of the tab. */
private val SectionGap = 14.dp

/** What the Detail tab and the pinned model say of an observation the app sent without its JSON because it was too large. */
internal const val TooLargeNote: String =
    "Too large — the app sent this without its JSON, so it can't be shown as a tree or replaced. " +
        "Its toString may be cut too. The limit is TimeMachineConfig.maxObservationLength."
