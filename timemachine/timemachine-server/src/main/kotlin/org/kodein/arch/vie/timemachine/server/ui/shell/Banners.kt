package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.sp
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.server.ui.common.HorizontalRule
import org.kodein.arch.vie.timemachine.server.ui.common.TextAction
import org.kodein.arch.vie.timemachine.server.ui.common.clock
import org.kodein.arch.vie.timemachine.server.ui.common.shortSessionId
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/** A probe that speaks another protocol was turned away: [onDismiss] hides it until a new session does the same. */
@Composable
internal fun RejectionBanner(notice: RejectionNotice, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val mono = InspectorTheme.fonts.mono
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(colors.errorContainer).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol("block", size = 20.dp, tint = colors.error)
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Rejected a connection from ${notice.name}") }
                        append(" ")
                        withStyle(SpanStyle(fontFamily = mono, fontSize = 11.5.sp, color = scheme.onSurfaceVariant)) { append(shortSessionId(notice.sessionId)) }
                        append(" — ${notice.name} uses protocol ")
                        withStyle(SpanStyle(fontFamily = mono)) { append(notice.protocolVersion.value.toString()) }
                        append(", this inspector speaks ")
                        withStyle(SpanStyle(fontFamily = mono)) { append(TimeMachineConnection.protocolVersion.value.toString()) }
                        append(". Update one of them.")
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                    color = scheme.onSurface,
                )
                Text(
                    buildAnnotatedString {
                        append("Still retrying · ")
                        withStyle(SpanStyle(fontFamily = mono)) { append(notice.attempts.toString()) }
                        append(if (notice.attempts == 1) " attempt · last " else " attempts · last ")
                        withStyle(SpanStyle(fontFamily = mono)) { append(clock(notice.lastAttemptAt)) }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = scheme.onSurfaceVariant,
                )
            }
            TextAction("Dismiss", color = colors.error, height = 28, onClick = onDismiss)
        }
        HorizontalRule()
    }
}

/** Shown over the timeline of a session that went away: what was recorded stays readable. */
@Composable
internal fun DisconnectedBanner(notice: DisconnectedNotice, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol("power_off", size = 18.dp, tint = scheme.outline)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = scheme.onSurface)) {
                        append("${notice.name} disconnected${notice.disconnectedAt?.let { " at ${clock(it)}" }.orEmpty()}.")
                    }
                    append(" The timeline stays readable. If ${notice.name} reconnects with the same id, it picks up here.")
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalRule()
    }
}
