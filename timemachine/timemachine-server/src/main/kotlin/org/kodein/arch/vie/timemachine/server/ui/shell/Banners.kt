package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.server.ui.common.clock
import org.kodein.arch.vie.timemachine.server.ui.common.shortSessionId
import org.kodein.arch.vie.timemachine.server.ui.designsystem.Banner
import org.kodein.arch.vie.timemachine.server.ui.designsystem.CalloutTone
import org.kodein.arch.vie.timemachine.server.ui.designsystem.LinkAction
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/** A probe that speaks another protocol was turned away: [onDismiss] hides it until a new session does the same. */
@Composable
internal fun RejectionBanner(notice: RejectionNotice, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val mono = InspectorTheme.fonts.mono
    Banner(CalloutTone.Error, Glyph.Block, action = { LinkAction("Dismiss", onClick = onDismiss, color = colors.error) }) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Rejected a connection from ${notice.name}") }
                    append(" ")
                    withStyle(SpanStyle(fontFamily = mono, fontSize = InspectorTheme.text.codeRow.fontSize, color = scheme.onSurfaceVariant)) { append(shortSessionId(notice.sessionId)) }
                    append(" — ${notice.name} uses protocol ")
                    withStyle(SpanStyle(fontFamily = mono)) { append(notice.protocolVersion.toString()) }
                    append(", this inspector speaks ")
                    withStyle(SpanStyle(fontFamily = mono)) { append(TimeMachineConnection.protocolVersion.toString()) }
                    append(". Update one of them.")
                },
                style = InspectorTheme.text.bodyEmphasis,
                color = scheme.onSurface,
            )
            Text(
                buildAnnotatedString {
                    append("Still retrying · ")
                    withStyle(SpanStyle(fontFamily = mono)) { append(notice.attempts.toString()) }
                    append(if (notice.attempts == 1) " attempt · last " else " attempts · last ")
                    withStyle(SpanStyle(fontFamily = mono)) { append(clock(notice.lastAttemptAt)) }
                },
                style = InspectorTheme.text.note, color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Shown over the timeline of a session that went away: what was recorded stays readable. */
@Composable
internal fun DisconnectedBanner(notice: DisconnectedNotice, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Banner(CalloutTone.Neutral, Glyph.PowerOff, modifier) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = scheme.onSurface)) {
                    append("${notice.name} disconnected${notice.disconnectedAt?.let { " at ${clock(it)}" }.orEmpty()}.")
                }
                append(" The timeline stays readable. If ${notice.name} reconnects with the same id, it picks up here.")
            },
            style = InspectorTheme.text.bodyEmphasis,
            color = scheme.onSurfaceVariant,
        )
    }
}
