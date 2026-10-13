package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.server.ui.common.DiffText
import org.kodein.arch.vie.timemachine.server.ui.common.JsonTree
import org.kodein.arch.vie.timemachine.server.ui.common.KindBadge
import org.kodein.arch.vie.timemachine.server.ui.common.RawJson
import org.kodein.arch.vie.timemachine.server.ui.common.TreeRawSwitch
import org.kodein.arch.vie.timemachine.server.ui.common.copyToClipboard
import org.kodein.arch.vie.timemachine.server.ui.common.formatTime
import org.kodein.arch.vie.timemachine.server.ui.common.kind
import org.kodein.arch.vie.timemachine.server.ui.common.monoStyle
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.common.splitSerialName
import org.kodein.arch.vie.timemachine.server.ui.common.verb
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol

/** The Detail tab: [DetailPaneContent] over what the store holds and the window has selected. */
@Composable
internal fun DetailView() {
    val inspector = LocalInspector.current
    val pane = remember(inspector) { inspector.detailPane() }
    InspectorPane(pane) { model -> DetailPaneContent(model) { post(it) } }
}

/** The Detail tab, as [model] has it: the selected entry, or what to do to select one. */
@Composable
private fun DetailPaneContent(model: DetailVIE.Model, post: (DetailVIE.Intent) -> Unit) {
    val row = model.projection.row
    val observation = (row?.message as? TimeMachineClientMessage.Observed)?.observation
    if (row == null || observation == null) NoEntrySelected()
    else DetailContent(model, observation, row.sequence, post)
}

@Composable
private fun NoEntrySelected() {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("No entry selected", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = scheme.onSurfaceVariant)
        Text("Click a row, or use ↑ ↓ to step through the timeline.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = scheme.outline, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DetailContent(model: DetailVIE.Model, observation: VIEObservation, sequence: TimeMachineSequence, post: (DetailVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val projection = model.projection
    run {
        val kind = observation.kind
        val name = splitSerialName(observation.typeName)
        val label = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.06.sp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(kind)
                    Symbol(if (kind == VIEObjectKind.Intent) "login" else "logout", size = 16.dp, tint = colors.kind(kind), mirrored = kind != VIEObjectKind.Intent)
                    Text(
                        if (kind == VIEObjectKind.Intent) "into the ViewModel" else "out of the ViewModel",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("#$sequence · ${formatTime(observation.at)}", style = monoStyle(11.5f, scheme.onSurfaceVariant), maxLines = 1)
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = scheme.outline)) { append(name.prefix) }
                        withStyle(SpanStyle(color = scheme.onSurface, fontWeight = FontWeight.SemiBold)) { append(name.last) }
                    },
                    style = monoStyle(12f, scheme.onSurface),
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
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = scheme.onSurface, modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Show", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = colors.intervened,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { post(DetailVIE.Intent.ShowInterventions) },
                        )
                    }
                }
                observation.asJson?.let { json ->
                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (projection.canIntervene) PillButton("Edit & intervene", "edit_note", filled = true) { post(DetailVIE.Intent.EditAndIntervene) }
                        PillButton("Copy JSON", "content_copy", filled = false) { copyToClipboard(json.pretty()) }
                    }
                }
            }
            projection.diff?.let { diff ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CHANGES VS #${diff.previous}", style = label, color = scheme.outline)
                    Column(
                        Modifier.fillMaxWidth().background(scheme.surfaceContainer, RoundedCornerShape(8.dp))
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(8.dp)).padding(vertical = 6.dp),
                    ) {
                        if (diff.ops.isEmpty()) Text("No differences.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = scheme.outline, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        diff.ops.forEach { Box(Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) { DiffText(it) } }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TOSTRING", style = label, color = scheme.outline)
                Text(observation.asString, style = monoStyle(11.5f, scheme.onSurface).copy(lineHeight = 17.sp))
            }
            observation.asJson?.let { json ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (kind == VIEObjectKind.Model) "FULL MODEL" else "JSON", style = label, color = scheme.outline, modifier = Modifier.weight(1f))
                        TreeRawSwitch(model.controls.raw) { post(DetailVIE.Intent.SetRaw(it)) }
                    }
                    Box(
                        Modifier.fillMaxWidth().background(scheme.surfaceContainer, RoundedCornerShape(8.dp))
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(8.dp)).padding(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        if (model.controls.raw) RawJson(projection.rawText.orEmpty())
                        else JsonTree(json, model.controls.expanded) { post(DetailVIE.Intent.ToggleNode(it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, glyph: String, filled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val foreground = if (filled) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Row(
        Modifier.height(28.dp)
            .then(if (filled) Modifier.background(scheme.primaryContainer, InspectorPillShape) else Modifier.border(1.dp, InspectorTheme.colors.strongOutline, InspectorPillShape))
            .clip(InspectorPillShape).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(start = 9.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(glyph, size = if (filled) 16.dp else 15.dp, tint = foreground)
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = foreground, maxLines = 1)
    }
}
