package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.ui.common.HorizontalRule
import org.kodein.arch.vie.timemachine.server.ui.common.LocalCompact
import org.kodein.arch.vie.timemachine.server.ui.common.monoStyle
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/** The bottom bar of the window: [StatusBarContent] over what the store holds, its theme toggle the window's. */
@Composable
internal fun StatusBar() {
    val viewState = LocalInspectorViewState.current
    InspectorPane(LocalInspector.current.statusBarPane()) { status ->
        StatusBarContent(status, dark = viewState.dark, onToggleTheme = { viewState.dark = !viewState.dark })
    }
}

/**
 * The bottom bar: where the inspector listens, the security reminder, the key hints, the sessions and the
 * theme. In the compact layout ([LocalCompact]) the reminder is shortened and the key hints are left out.
 */
@Composable
private fun StatusBarContent(status: StatusBarVIE.Model, dark: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val compact = LocalCompact.current
    Column(modifier.fillMaxWidth().background(scheme.surfaceDim)) {
        HorizontalRule()
        Row(
            Modifier.fillMaxWidth().height(InspectorDimens.statusBar).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Listening(status)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Symbol("lock_open", size = 14.dp, tint = scheme.outline)
                Text(if (compact) CompactSecurityReminder else SecurityReminder, style = StatusText, color = scheme.outline, maxLines = 1)
            }
            Box(Modifier.weight(1f))
            if (status.sessions > 0 && !compact) {
                Text("↑↓ step · ⏎ expand · E edit & intervene · F follow", style = monoStyle(10.5f, scheme.outline), maxLines = 1)
            }
            Text(
                if (status.sessions == 0) "No sessions" else "${status.connectedSessions} of ${status.sessions} sessions connected",
                style = StatusText, color = scheme.onSurfaceVariant, maxLines = 1,
            )
            Symbol(
                if (dark) "dark_mode" else "light_mode", size = 15.dp, tint = scheme.outline,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onToggleTheme),
            )
        }
    }
}

@Composable
private fun Listening(status: StatusBarVIE.Model) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(if (status.listener is ListenerState.Failed) colors.error else colors.success, CircleShape))
        when (status.listener) {
            ListenerState.Starting -> Text("Starting…", style = StatusText, color = scheme.onSurfaceVariant)
            is ListenerState.Failed ->
                Text("Not listening — port ${TimeMachineConnection.port} is in use", style = StatusText, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            is ListenerState.Listening -> {
                Text("Listening on", style = StatusText, color = scheme.onSurfaceVariant, maxLines = 1)
                Text(status.address.orEmpty(), style = monoStyle(11f, scheme.onSurface), maxLines = 1)
                Text("· protocol ${status.protocolVersion}", style = StatusText, color = scheme.outline, maxLines = 1)
            }
        }
    }
}

internal const val SecurityReminder = "Unencrypted · unauthenticated · all network interfaces"

/** What the compact layout keeps of [SecurityReminder]. */
internal const val CompactSecurityReminder = "Unencrypted"

private val StatusText @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
