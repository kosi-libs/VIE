package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.uuid.Uuid
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.VIEViewScope
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.store.InterventionStatus
import org.kodein.arch.vie.timemachine.server.ui.common.ScrollbarBox
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.common.colorJsonLine
import org.kodein.arch.vie.timemachine.server.ui.common.formatDelay
import org.kodein.arch.vie.timemachine.server.ui.common.formatTime
import org.kodein.arch.vie.timemachine.server.ui.common.splitSerialName
import org.kodein.arch.vie.timemachine.server.ui.common.style
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.designsystem.ButtonSize
import org.kodein.arch.vie.timemachine.server.ui.designsystem.HorizontalRule
import org.kodein.arch.vie.timemachine.server.ui.designsystem.IconAction
import org.kodein.arch.vie.timemachine.server.ui.designsystem.InspectorButton
import org.kodein.arch.vie.timemachine.server.ui.designsystem.LinkAction
import org.kodein.arch.vie.timemachine.server.ui.designsystem.VerticalRule
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorColors
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


private val EditorHeight = 200.dp

/**
 * The Intervene tab: what to send (kind, type, JSON), the send verb, the result card of the latest
 * intervention sent from here and the recent ones.
 */
@Composable
internal fun ComposerView(sessionId: Uuid, viewModelId: Uuid) {
    val inspector = LocalInspector.current
    val pane = remember(inspector, sessionId, viewModelId) { inspector.composerPane(sessionId, viewModelId) }
    InspectorPane(pane) { model ->
        ComposerContent(model)
    }
}

/** The Intervene tab, as [model] has it, its editor [editorHeight] high. */
@Composable
internal fun VIEViewScope<ComposerVIE.Intent, NoSignal>.ComposerContent(model: ComposerVIE.Model, editorHeight: Dp = EditorHeight) {
    val scheme = MaterialTheme.colorScheme
    val projection = model.projection
    val target = projection.target
    val blocked = projection.blockText != null
    val scroll = rememberScrollState()
    ScrollbarBox(rememberScrollbarAdapter(scroll), Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(start = InspectorDimens.paneHorizontalPadding, end = InspectorDimens.paneHorizontalPadding, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            projection.blockText?.let { text ->
                Row(
                    Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Symbol(Glyph.DoNotDisturbOn, size = 18.dp, tint = scheme.outline)
                    Text(text, style = InspectorTheme.text.bodyEmphasis, color = scheme.onSurface, modifier = Modifier.weight(1f))
                }
            }
            if (target != null) {
                Column(
                    Modifier.alpha(if (blocked) 0.45f else 1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Target", style = InspectorTheme.text.body, color = scheme.outline, modifier = Modifier.width(44.dp))
                        Text(projection.name, style = InspectorTheme.text.code, color = scheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(projection.shortId, style = InspectorTheme.text.monoSecondary, color = scheme.outline, maxLines = 1)
                    }
                    KindSegments(target.kind, target.enabledKinds, blocked) { post(ComposerVIE.Intent.SetKind(it)) }
                    projection.unserializable?.let { (kind, typeName) -> KindReason(kind, typeName) }
                    TypeField(target.type.orEmpty(), target.types, model.controls.typeMenuOpen, blocked) { post(it) }
                    JsonSection(model, editorHeight, blocked) { post(it) }
                    SendRow(target.kind, projection.canSend && !blocked) { post(ComposerVIE.Intent.Send) }
                }
                projection.current?.let { ResultCard(it, projection, ::post) }
                RecentInterventions(projection.history, projection.olderInterventions, ::post)
            }
        }
    }
}

// ---- Kind and type ---------------------------------------------------------------------------

@Composable
private fun KindSegments(kind: VIEObjectKind, enabled: Set<VIEObjectKind>, blocked: Boolean, onSelect: (VIEObjectKind) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(32.dp).clip(InspectorPillShape).border(1.dp, InspectorTheme.colors.strongOutline, InspectorPillShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VIEObjectKind.entries.forEachIndexed { index, entry ->
            val ok = entry in enabled
            val on = entry == kind && ok
            if (index > 0) VerticalRule(color = InspectorTheme.colors.strongOutline)
            val foreground = if (on) scheme.onPrimaryContainer else if (ok) scheme.onSurface else scheme.outline
            Row(
                Modifier.weight(1f).fillMaxHeight().background(if (on) scheme.primaryContainer else Color.Transparent)
                    .alpha(if (ok) 1f else 0.55f)
                    .action(enabled = ok && !blocked, role = Role.RadioButton, selected = on) { onSelect(entry) },
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) Symbol(Glyph.Check, size = 15.dp, tint = foreground) else if (!ok) Symbol(Glyph.Block, size = 15.dp, tint = foreground)
                Text(entry.name, style = InspectorTheme.text.label, color = foreground, maxLines = 1)
            }
        }
    }
}

@Composable
private fun KindReason(kind: VIEObjectKind, typeName: String) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Symbol(Glyph.Info, size = 15.dp, tint = scheme.outline)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = scheme.onSurface, fontFamily = InspectorTheme.fonts.mono)) { append(typeName) }
                append(" isn’t serializable — ${kind.name}s of this ViewModel only exist as toString, so they can’t be ${kind.style().participle}.")
            },
            style = InspectorTheme.text.note, color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TypeField(type: String, types: List<String>, open: Boolean, blocked: Boolean, post: (ComposerVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val name = splitSerialName(type)
    val choosable = types.size > 1 && !blocked
    // The field's width, for its menu: written when the field is laid out and read when the menu is
    // measured, so a field that is resized, as a dragged splitter does at each frame, composes nothing.
    var width by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).onSizeChanged { width = it.width }) {
        Row(
            Modifier.fillMaxWidth().height(36.dp)
                .border(if (open) 2.dp else 1.dp, if (open) scheme.primary else InspectorTheme.colors.strongOutline, shape)
                .action(shape = shape, enabled = choosable) { post(ComposerVIE.Intent.SetTypeMenu(!open)) }
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f)) {
                Text(name.prefix, style = InspectorTheme.text.code, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(name.last, style = InspectorTheme.text.code, color = scheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Symbol(if (open) Glyph.ArrowDropUp else Glyph.ArrowDropDown, size = 18.dp, tint = scheme.onSurfaceVariant)
        }
        Text(
            "Type", style = MaterialTheme.typography.labelSmall, color = if (open) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.offset(x = 10.dp, y = (-8).dp).background(scheme.surfaceContainerLow).padding(horizontal = 4.dp),
        )
        DropdownMenu(
            expanded = open && choosable,
            onDismissRequest = { post(ComposerVIE.Intent.SetTypeMenu(false)) },
            modifier = Modifier
                .layout { measurable, constraints ->
                    val fixed = constraints.constrainWidth(width)
                    val placeable = measurable.measure(constraints.copy(minWidth = fixed, maxWidth = fixed))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .background(scheme.surfaceContainerHighest),
        ) {
            for (option in types) {
                val parts = splitSerialName(option)
                Row(
                    Modifier.fillMaxWidth().height(32.dp).background(if (option == type) scheme.surfaceContainerHighest else Color.Transparent)
                        .action { post(ComposerVIE.Intent.SetType(option)) }.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Symbol(Glyph.Check, size = 15.dp, tint = if (option == type) scheme.primary else Color.Transparent)
                    Text(parts.prefix, style = InspectorTheme.text.code, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(parts.last, style = InspectorTheme.text.code, color = scheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

// ---- JSON editor -----------------------------------------------------------------------------

@Composable
private fun JsonSection(model: ComposerVIE.Model, editorHeight: Dp, blocked: Boolean, post: (ComposerVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val projection = model.projection
    val target = projection.target ?: return
    val invalid = projection.parse as? JsonParse.Invalid
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("JSON", style = InspectorTheme.text.action, color = scheme.onSurface)
            Text(
                prefillNote(target.type.orEmpty(), target.kind, target.prefill?.source, edited = model.controls.draft != null),
                style = InspectorTheme.text.note, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            for ((label, intent) in listOf("Format" to ComposerVIE.Intent.Format, "Reset" to ComposerVIE.Intent.Reset)) {
                LinkAction(label, onClick = { post(intent) }, enabled = !blocked)
            }
        }
        JsonEditorBox(model.editorText, invalid, !blocked, editorHeight) { post(ComposerVIE.Intent.SetText(it)) }
        target.signalNote?.let { note ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Symbol(Glyph.Warning, size = 15.dp, tint = colors.timeout)
                Text(note.text, style = InspectorTheme.text.note, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** The editor in its frame: outlined in the error color when the parser rejects [text], with its message under it. */
@Composable
private fun JsonEditorBox(text: String, invalid: JsonParse.Invalid?, enabled: Boolean, editorHeight: Dp, onText: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(scheme.surfaceContainer)
            .border(1.dp, if (invalid != null) colors.error else colors.strongOutline, RoundedCornerShape(8.dp)),
    ) {
        JsonEditor(text, invalid?.line, enabled, Modifier.fillMaxWidth().height(editorHeight), onText)
        invalid?.let {
            Row(
                Modifier.fillMaxWidth().background(colors.errorContainer).border(1.dp, colors.error).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top,
            ) {
                Symbol(Glyph.Error, size = 15.dp, tint = colors.error)
                Text("Line ${it.line}, col ${it.column}: ${it.message}. Fix it to send.", style = InspectorTheme.text.codeRow, color = scheme.onSurface)
            }
        }
    }
}

private fun prefillNote(type: String, kind: VIEObjectKind, source: PrefillSource?, edited: Boolean): String {
    val short = splitSerialName(type).last
    return when {
        edited -> "edited" + (source as? PrefillSource.Observed)?.let { " · based on #${it.sequence}" }.orEmpty()
        source is PrefillSource.Observed -> "pre-filled from #${source.sequence}, the latest observed $short"
        source is PrefillSource.SealedTemplate -> "no $short observed yet — starting from its #type"
        else -> kind.style().unobservedNote(short)
    }
}

private class JsonColoring(private val colors: InspectorColors) : VisualTransformation {
    override fun filter(text: AnnotatedString) =
        TransformedText(colorJsonLine(text.text, colors), OffsetMapping.Identity)
}

/**
 * A monospace editor with a line gutter. A line the parser rejected sits on the error tint with a
 * wavy underline.
 *
 * [text] is the controls', which [onText] sets as it is typed: the editor keeps no copy of it, so it
 * never waits for a projection, and a text replaced from outside (a reset, a format, another kind or
 * type, a request) is simply the text it shows next, the caret staying where it was.
 */
@Composable
private fun JsonEditor(text: String, errorLine: Int?, enabled: Boolean, modifier: Modifier, onText: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val lineHeight = with(LocalDensity.current) { InspectorDimens.codeLine.toSp() }
    val lines = text.count { it == '\n' } + 1
    val coloring = remember(colors) { JsonColoring(colors) }
    val scroll = rememberScrollState()
    ScrollbarBox(rememberScrollbarAdapter(scroll), modifier) {
        Row(
            Modifier.fillMaxWidth().verticalScroll(scroll).padding(vertical = 6.dp).drawBehind {
                if (errorLine != null) {
                    val top = (errorLine - 1) * InspectorDimens.codeLine.toPx()
                    val bottom = top + InspectorDimens.codeLine.toPx()
                    drawRect(colors.errorContainer, Offset(0f, top), Size(size.width, bottom - top))
                    val y = bottom - 3.dp.toPx()
                    val step = 3.dp.toPx()
                    val wave = Path().apply {
                        moveTo(40.dp.toPx(), y)
                        var x = 40.dp.toPx()
                        var up = true
                        while (x < size.width) { x += step; lineTo(x, if (up) y - 1.5.dp.toPx() else y + 1.5.dp.toPx()); up = !up }
                    }
                    drawPath(wave, colors.error, style = Stroke(1.dp.toPx()))
                }
            },
        ) {
            // One text for all the numbers, a line each, as high as the editor's: a long draft adds no node.
            val errorColor = colors.error
            val numbers = remember(lines, errorLine, errorColor) {
                buildAnnotatedString {
                    for (line in 1..lines) {
                        if (line > 1) append('\n')
                        if (line == errorLine) withStyle(SpanStyle(color = errorColor)) { append(line.toString()) } else append(line.toString())
                    }
                }
            }
            Text(
                numbers, style = InspectorTheme.text.code.copy(lineHeight = lineHeight), color = scheme.outline, textAlign = TextAlign.End,
                softWrap = false, modifier = Modifier.width(34.dp).padding(end = 12.dp),
            )
            BasicTextField(
                value = text,
                onValueChange = onText,
                enabled = enabled,
                textStyle = InspectorTheme.text.code.copy(color = scheme.onSurfaceVariant, lineHeight = lineHeight),
                cursorBrush = SolidColor(scheme.primary),
                visualTransformation = coloring,
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 10.dp),
            )
        }
    }
}

// ---- Send and result -------------------------------------------------------------------------

@Composable
private fun SendRow(kind: VIEObjectKind, enabled: Boolean, onSend: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val kindStyle = kind.style()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        InspectorButton(kindStyle.verb, onClick = onSend, glyph = kindStyle.sendGlyph, size = ButtonSize.Large, enabled = enabled)
        Text(kindStyle.effect, style = InspectorTheme.text.note, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

/** How an intervention's outcome looks: the card tint, the title colour, and its glyph. */
private data class Tone(val background: Color, val title: Color, val glyph: Glyph?, val glyphColor: Color)

@Composable
private fun toneOf(status: InterventionStatus): Tone {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    return when (status) {
        InterventionStatus.Pending -> Tone(scheme.surfaceContainerHigh, scheme.onSurface, null, scheme.primary)
        is InterventionStatus.Succeeded -> Tone(colors.successContainer, colors.success, Glyph.CheckCircle, colors.success)
        InterventionStatus.AcceptedNotObserved -> Tone(colors.notObserved, scheme.onSurface, Glyph.VisibilityOff, colors.success)
        is InterventionStatus.Failed -> Tone(colors.errorContainer, colors.error, Glyph.Error, colors.error)
        InterventionStatus.TimedOut -> Tone(colors.timeoutContainer, colors.timeout, Glyph.HourglassBottom, colors.timeout)
        InterventionStatus.NotSent -> Tone(scheme.surfaceContainerHigh, scheme.onSurface, Glyph.CancelScheduleSend, colors.notSent)
    }
}

private fun VIEInterventionResult.Reason.plain(): String = when (this) {
    VIEInterventionResult.Reason.UnknownViewModel -> "Unknown ViewModel"
    VIEInterventionResult.Reason.ClosedViewModel -> "ViewModel closed"
    VIEInterventionResult.Reason.NotSerializable -> "Not serializable"
    VIEInterventionResult.Reason.UnknownType -> "Unknown type"
    VIEInterventionResult.Reason.BadJson -> "Bad JSON"
    VIEInterventionResult.Reason.ProbeFailure -> "Probe failure"
}

@Composable
private fun ResultCard(view: InterventionView, projection: ComposerProjection, post: (ComposerVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tone = toneOf(view.status)
    val app = projection.sessionName
    val type = splitSerialName(view.typeName).last
    val status = view.status
    val timeout = formatDelay(projection.timeout)
    val title: String
    val body: String
    var reasons: List<String> = emptyList()
    var foot: String? = null
    var message: String? = null
    when (status) {
        InterventionStatus.Pending -> {
            title = "Sent — waiting for $app…"
            body = "${view.kind.style().verb} · $type · sent ${formatTime(view.requestedAt)} · times out after $timeout"
        }
        is InterventionStatus.Succeeded -> {
            title = "Success · observed as #${status.sequence}"
            body = "$app accepted it and the intervened row arrived in the timeline."
        }
        InterventionStatus.AcceptedNotObserved -> {
            title = "Accepted, not observed"
            body = "$app accepted it, but no intervened row arrived within $timeout. Possible reasons:"
            reasons = listOfNotNull(
                if (view.kind == VIEObjectKind.Model) "The app’s next update superseded it, or it equalled the current model." else null,
                "It is still waiting behind the ViewModel’s own work.",
                "Its row was lost in a gap.",
                "The ViewModel closed before it entered.",
            )
            foot = "A row that arrives later turns this into success."
        }
        is InterventionStatus.Failed -> {
            title = "Failed · ${status.failure.reason.plain()}"
            body = when (status.failure.reason) {
                VIEInterventionResult.Reason.BadJson -> "$app couldn’t read this JSON as $type."
                VIEInterventionResult.Reason.UnknownType -> "$app doesn’t know a type with this name, so nothing was posted."
                VIEInterventionResult.Reason.UnknownViewModel -> "$app no longer knows this ViewModel, so nothing was posted."
                VIEInterventionResult.Reason.ClosedViewModel -> "The ViewModel closed before the request reached it."
                VIEInterventionResult.Reason.NotSerializable -> "The ${view.kind.name} of this ViewModel isn’t serializable, so nothing was posted."
                VIEInterventionResult.Reason.ProbeFailure -> "$app failed while handling this request. It may or may not have been applied; its output says why."
            }
            message = status.failure.message ?: view.typeName.takeIf { status.failure.reason == VIEInterventionResult.Reason.UnknownType }
        }
        InterventionStatus.TimedOut -> {
            title = "Timed out · nothing after $timeout"
            body = "Neither an answer nor the row arrived. The outcome is unknown — $app may still apply it."
            foot = "A late answer turns this into failure or accepted; a late row, into success."
        }
        InterventionStatus.NotSent -> {
            title = "Not sent"
            body = "$app disconnected before the request left the inspector. Nothing reached the app."
        }
    }
    Row(
        Modifier.fillMaxWidth().background(tone.background, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
    ) {
        if (tone.glyph == null) {
            CircularProgressIndicator(Modifier.padding(top = 1.dp).size(14.dp), color = scheme.primary, trackColor = InspectorTheme.colors.strongOutline, strokeWidth = 2.dp)
        } else Symbol(tone.glyph, size = 18.dp, tint = tone.glyphColor)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = tone.title)
            Text(body, style = InspectorTheme.text.body, color = scheme.onSurfaceVariant)
            if (reasons.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                reasons.forEach { reason ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("–", style = InspectorTheme.text.note, color = scheme.outline)
                        Text(reason, style = InspectorTheme.text.note, color = scheme.onSurfaceVariant)
                    }
                }
            }
            foot?.let { Text(it, style = InspectorTheme.text.note, color = scheme.outline) }
            if (status is InterventionStatus.Succeeded) {
                if (view.rowRetained) LinkAction(
                    "Jump to #${status.sequence}", onClick = { post(ComposerVIE.Intent.SelectRow(status.sequence)) }, glyph = Glyph.ArrowForward,
                ) else Text("Row #${status.sequence} is evicted", style = InspectorTheme.text.note, color = scheme.outline)
            }
            message?.let {
                Text(
                    it, style = InspectorTheme.text.monoSecondary.copy(lineHeight = 16.sp), color = scheme.onSurface,
                    modifier = Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            if (status == InterventionStatus.TimedOut) LinkAction("Resend", onClick = { post(ComposerVIE.Intent.Resend(view.requestId)) })
        }
    }
}

// ---- Recent interventions --------------------------------------------------------------------

@Composable
private fun RecentInterventions(history: List<InterventionView>, older: Int, post: (ComposerVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "RECENT INTERVENTIONS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.06.sp), color = scheme.outline,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (history.isEmpty()) Text("None yet for this ViewModel.", style = InspectorTheme.text.body, color = scheme.outline)
        history.forEach { view ->
            val tone = toneOf(view.status)
            Row(
                Modifier.fillMaxWidth().height(34.dp).action { post(ComposerVIE.Intent.ShowResult(view.requestId)) }
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tone.glyph == null) CircularProgressIndicator(Modifier.size(14.dp), color = scheme.primary, trackColor = InspectorTheme.colors.strongOutline, strokeWidth = 2.dp)
                else Symbol(tone.glyph, size = 17.dp, tint = tone.glyphColor)
                Text("#${view.number}", style = InspectorTheme.text.monoSecondary, color = scheme.outline, modifier = Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        buildAnnotatedString {
                            append(view.kind.style().verb + " ")
                            withStyle(SpanStyle(fontFamily = InspectorTheme.fonts.mono, fontSize = InspectorTheme.text.codeRow.fontSize, color = scheme.onSurfaceVariant)) { append(splitSerialName(view.typeName).last) }
                        },
                        style = InspectorTheme.text.body, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${statusText(view)} · ${formatTime(view.requestedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (view.status is InterventionStatus.Failed) InspectorTheme.colors.error else scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconAction(
                    Glyph.EditNote, "Put this intervention in the editor",
                    onClick = { post(ComposerVIE.Intent.LoadFromHistory(view.requestId)) }, size = 17.dp,
                )
            }
            HorizontalRule(color = InspectorTheme.colors.rowLine)
        }
        // The timeline still has the rows of the older ones that were observed.
        if (older > 0) Text("$older older", style = InspectorTheme.text.body, color = scheme.outline, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }
}

private fun statusText(view: InterventionView): String = when (val status = view.status) {
    InterventionStatus.Pending -> "pending"
    is InterventionStatus.Succeeded -> "observed as #${status.sequence}" + if (view.rowRetained) "" else " · row evicted"
    InterventionStatus.AcceptedNotObserved -> "accepted, not observed"
    is InterventionStatus.Failed -> "failed · ${status.failure.reason.plain()}"
    InterventionStatus.TimedOut -> "timed out"
    InterventionStatus.NotSent -> "not sent"
}
