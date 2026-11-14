package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/** The glyph a specimen of each tone leads with, as the views give it. */
private fun CalloutTone.specimenGlyph(): Glyph = when (this) {
    CalloutTone.Neutral -> Glyph.DoNotDisturbOn
    CalloutTone.Information -> Glyph.Info
    CalloutTone.Success -> Glyph.CheckCircle
    CalloutTone.Error -> Glyph.Error
    CalloutTone.Warning -> Glyph.Warning
    CalloutTone.Intervened -> Glyph.TouchApp
}

/** A row of the callout scene: a tone, led by its glyph or, [waiting], by a progress indicator. */
private class CalloutSpecimen(val tone: CalloutTone, val waiting: Boolean = false) {
    val label: String get() = tone.name.lowercase() + if (waiting) " · waiting" else ""
}

/** What a specimen says: one line, one line and an action, or a title over lines. */
private enum class CalloutLines(val label: String) { Line("one line"), Action("one line · action"), Lines("title · lines") }

/** [Callout] in each tone, with one line, with an action, and with a title over lines; then waiting. */
internal val CalloutScene: GalleryScene = galleryMatrix(
    name = "callout",
    rows = CalloutTone.entries.map { CalloutSpecimen(it) } + CalloutSpecimen(CalloutTone.Neutral, waiting = true),
    columns = CalloutLines.entries,
    rowLabel = { it.label },
    columnLabel = { it.label },
    cellWidth = 360.dp,
) { specimen, lines ->
    val content: @Composable ColumnScope.() -> Unit = { SpecimenLines(specimen.tone, lines) }
    val action: (@Composable () -> Unit)? =
        if (lines == CalloutLines.Action) { { LinkAction("Show", onClick = {}, color = InspectorTheme.colors.intervened) } } else null
    if (specimen.waiting) Callout(specimen.tone, leading = { WaitingIndicator() }, action = action, content = content)
    else Callout(specimen.tone, specimen.tone.specimenGlyph(), action = action, content = content)
}

/** The indicator of an intervention that waits for its outcome, as the composer draws it. */
@Composable
private fun WaitingIndicator() {
    CircularProgressIndicator(Modifier.size(14.dp), color = MaterialTheme.colorScheme.primary, trackColor = InspectorTheme.colors.strongOutline, strokeWidth = 2.dp)
}

@Composable
private fun SpecimenLines(tone: CalloutTone, lines: CalloutLines) {
    val scheme = MaterialTheme.colorScheme
    when (lines) {
        CalloutLines.Line, CalloutLines.Action ->
            Text("The session is disconnected: nothing can be sent.", style = InspectorTheme.text.bodyEmphasis, color = scheme.onSurface)
        CalloutLines.Lines -> {
            Text("Timed out · nothing after 5 s", style = InspectorTheme.text.labelStrong, color = tone.title)
            Text("Neither an answer nor the row arrived. The outcome is unknown.", style = InspectorTheme.text.body, color = scheme.onSurfaceVariant)
            Text("A late row turns this into success.", style = InspectorTheme.text.note, color = scheme.outline)
        }
    }
}
