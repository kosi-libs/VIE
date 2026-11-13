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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.common.formatTime
import org.kodein.arch.vie.timemachine.server.ui.common.jsonTree
import org.kodein.arch.vie.timemachine.server.ui.common.kind
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.common.rawJson
import org.kodein.arch.vie.timemachine.server.ui.common.rememberCopy
import org.kodein.arch.vie.timemachine.server.ui.common.rememberJsonLines
import org.kodein.arch.vie.timemachine.server.ui.common.splitSerialName
import org.kodein.arch.vie.timemachine.server.ui.common.treeLines
import org.kodein.arch.vie.timemachine.server.ui.common.verb
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
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
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("No entry selected", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Text("Click a row, or use ↑ ↓ to step through the timeline.", style = InspectorTheme.text.body, color = scheme.outline, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DetailContent(model: DetailVIE.Model, observation: VIEObservation, sequence: TimeMachineSequence, post: (DetailVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val projection = model.projection
    val kind = observation.kind
    val label = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.06.sp)
    // The lines of the one that shows, the tree or the text: the other has none.
    val json = observation.asJson
    val raw = model.controls.raw
    val expanded = model.controls.expanded
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
            projection.diff?.let { diff -> item(key = "changes", contentType = "changes") { DetailChanges(diff, label, Modifier.padding(top = SectionGap)) } }
            item(key = "toString", contentType = "toString") {
                Column(Modifier.padding(top = SectionGap), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TOSTRING", style = label, color = scheme.outline)
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
                        Text(if (kind == VIEObjectKind.Model) "FULL MODEL" else "JSON", style = label, color = scheme.outline, modifier = Modifier.weight(1f))
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
    val copy = rememberCopy()
    val kind = observation.kind
    val name = splitSerialName(observation.typeName)
    val json = observation.asJson
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KindBadge(kind)
            Symbol(if (kind == VIEObjectKind.Intent) "login" else "logout", size = 16.dp, tint = colors.kind(kind), mirrored = kind != VIEObjectKind.Intent)
            Text(
                if (kind == VIEObjectKind.Intent) "into the ViewModel" else "out of the ViewModel",
                style = InspectorTheme.text.body, color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text("#$sequence · ${formatTime(observation.at)}", style = InspectorTheme.text.codeRow, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = scheme.outline)) { append(name.prefix) }
                withStyle(SpanStyle(color = scheme.onSurface, fontWeight = FontWeight.SemiBold)) { append(name.last) }
            },
            style = InspectorTheme.text.code, color = scheme.onSurface,
        )
        projection.interventionNumber?.let { number ->
            Row(
                Modifier.fillMaxWidth().background(colors.intervenedContainer, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol("touch_app", size = 16.dp, tint = colors.intervened)
                Text(
                    buildAnnotatedString {
                        append("Sent by the inspector — ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("intervention #$number") }
                        projection.interventionKind?.let { append(" · ${verb(it)}") }
                    },
                    style = InspectorTheme.text.body, color = scheme.onSurface, modifier = Modifier.weight(1f),
                )
                Text(
                    "Show", style = InspectorTheme.text.label, color = colors.intervened,
                    modifier = Modifier.action { post(DetailVIE.Intent.ShowInterventions) },
                )
            }
        }
        if (json != null) {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (projection.canIntervene) PillButton("Edit & intervene", "edit_note", filled = true) { post(DetailVIE.Intent.EditAndIntervene) }
                PillButton("Copy JSON", "content_copy", filled = false) { copy(json.pretty()) }
            }
        }
    }
}

/** What differs from the Model observed before. */
@Composable
private fun DetailChanges(diff: ModelDiff, label: TextStyle, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("CHANGES VS #${diff.previous}", style = label, color = scheme.outline)
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

@Composable
private fun PillButton(label: String, glyph: String, filled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val foreground = if (filled) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Row(
        Modifier.height(28.dp)
            .then(if (filled) Modifier.background(scheme.primaryContainer, InspectorPillShape) else Modifier.border(1.dp, InspectorTheme.colors.strongOutline, InspectorPillShape))
            .clip(InspectorPillShape).action(onClick = onClick).padding(start = 9.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(glyph, size = if (filled) 16.dp else 15.dp, tint = foreground)
        Text(label, style = InspectorTheme.text.label, color = foreground, maxLines = 1)
    }
}

/** What the Detail tab and the pinned model say of an observation the app sent without its JSON because it was too large. */
internal const val TooLargeNote: String =
    "Too large — the app sent this without its JSON, so it can't be shown as a tree or replaced. " +
        "Its toString may be cut too. The limit is TimeMachineConfig.maxObservationLength."
