package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kodein.arch.vie.timemachine.server.ui.common.LiveDot
import org.kodein.arch.vie.timemachine.server.ui.common.TextAction
import org.kodein.arch.vie.timemachine.server.ui.common.clock
import org.kodein.arch.vie.timemachine.server.ui.common.monoStyle
import org.kodein.arch.vie.timemachine.server.ui.common.shortSessionId
import org.kodein.arch.vie.timemachine.server.ui.common.splitSerialName
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.ViewModelStatus
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/**
 * How the sessions are shown: the left [Pane] of the full layout, or, in the compact one, the [Rail] and
 * the [Drawer] it opens, which is the pane that closes when a scope is picked from it.
 */
internal enum class SessionsPresentation { Pane, Rail, Drawer }

/** The sessions, in [presentation]: [SessionsContent] or [SessionsRail], over what the store holds. */
@Composable
internal fun SessionsView(presentation: SessionsPresentation = SessionsPresentation.Pane, modifier: Modifier = Modifier) {
    val pane = LocalInspector.current.sessionsPane()
    InspectorPane(pane) { model -> SessionsSlot(model, presentation, modifier) { post(it) } }
}

/**
 * [model] in [presentation], [post] being what its clicks send. The window's layout and the Inspector
 * window's page both fill their sessions slot with it, so that the three look the same in both. The rail's
 * menu opens the drawer, and a scope picked in the drawer closes it.
 */
@Composable
private fun SessionsSlot(model: SessionsVIE.Model, presentation: SessionsPresentation, modifier: Modifier, post: (SessionsVIE.Intent) -> Unit) {
    val viewState = LocalInspectorViewState.current
    when (presentation) {
        SessionsPresentation.Pane -> SessionsContent(model, modifier, post)
        SessionsPresentation.Rail -> SessionsRail(model, modifier, onMenu = { viewState.drawerOpen = true }, post)
        SessionsPresentation.Drawer -> SessionsContent(model, modifier) { intent ->
            post(intent)
            if (intent is SessionsVIE.Intent.SelectScope) viewState.drawerOpen = false
        }
    }
}

/** What the left pane shows of [model], [post] being what its clicks send. */
@Composable
private fun SessionsContent(model: SessionsVIE.Model, modifier: Modifier = Modifier, post: (SessionsVIE.Intent) -> Unit) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().height(InspectorDimens.paneHeader).padding(start = 14.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sessions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(model.sessions.size.toString(), style = monoStyle(11f, MaterialTheme.colorScheme.outline))
            Box(Modifier.weight(1f))
            TextAction(
                "Clear disconnected", glyph = "delete_sweep", enabled = model.disconnectedSessions > 0,
                color = if (model.disconnectedSessions > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ) { post(SessionsVIE.Intent.ClearDisconnected) }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(model.sessions, key = { it.id.toString() }) { session ->
                SessionBlock(session, model.selected) { post(it) }
            }
        }
    }
}

@Composable
private fun SessionBlock(session: SessionItem, selected: InspectorScope?, post: (SessionsVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val onSession = selected == InspectorScope.Session(session.id)
    Column(
        Modifier.fillMaxWidth().alpha(if (session.connected) 1f else 0.62f).padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth().height(InspectorDimens.sessionItem)
                .background(if (onSession) scheme.primaryContainer else Color0, RoundedCornerShape(8.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { post(SessionsVIE.Intent.SelectScope(InspectorScope.Session(session.id))) }
                .padding(start = 6.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol("expand_more", size = 18.dp, tint = scheme.outline)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (onSession) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(session.shortId, style = monoStyle(10.5f, scheme.outline), maxLines = 1)
                    LiveDot(session.connected)
                }
                Text(
                    if (session.connected) "connected ${clock(session.connectedAt)}"
                    else "${clock(session.connectedAt)} – ${session.disconnectedAt?.let(::clock) ?: "?"} · disconnected",
                    style = monoStyle(10.5f, scheme.outline), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (onSession) {
                Text("all ViewModels", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp), color = scheme.onPrimaryContainer, maxLines = 1)
            }
        }
        session.viewModels.forEach { vm ->
            ViewModelRow(vm, session, selected == InspectorScope.ViewModel(session.id, vm.id)) {
                post(SessionsVIE.Intent.SelectScope(InspectorScope.ViewModel(session.id, vm.id)))
            }
        }
        TextAction(
            session.clearLabel,
            modifier = Modifier.padding(start = 16.dp).align(Alignment.Start),
            glyph = if (session.connected) "clear_all" else "delete_sweep",
            color = if (session.clearEnabled) scheme.primary else scheme.outline,
            enabled = session.clearEnabled,
            height = 24,
        ) { post(SessionsVIE.Intent.ClearSession(session.id)) }
    }
}

@Composable
private fun ViewModelRow(vm: ViewModelItem, session: SessionItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val closed = vm.status == ViewModelStatus.Closed
    val dead = closed || !session.connected
    val name = vm.typeName?.let { splitSerialName(it).last } ?: shortSessionId(vm.id)
    Row(
        Modifier
            .fillMaxWidth().height(InspectorDimens.viewModelItem)
            .alpha(if (closed && session.connected) 0.6f else 1f)
            .background(if (selected) scheme.primaryContainer else Color0, RoundedCornerShape(8.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(start = 30.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    name, style = monoStyle(12.5f, if (selected) scheme.onPrimaryContainer else scheme.onSurface, bold = true),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (vm.typeName != null) Text(vm.shortId, style = monoStyle(10.5f, scheme.outline), maxLines = 1)
            }
            Text(vm.meta(session), style = MaterialTheme.typography.bodySmall, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Sparkline(vm.sparkline, dead)
            Text(if (vm.rowCount == 1) "1 entry" else "${vm.rowCount} entries", style = monoStyle(10.5f, scheme.outline), maxLines = 1)
        }
    }
}

private fun ViewModelItem.meta(session: SessionItem): String {
    val unknown = if (typeName == null) "Unknown · " else ""
    val state = when {
        status == ViewModelStatus.Closed -> closedAt?.let { "closed ${clock(it)}" } ?: "closed · time unknown"
        !session.connected -> "session disconnected"
        status == ViewModelStatus.Identifying -> "asking ${session.name} for its type…"
        else -> "alive · ${if (startSeen) "started" else "first seen"} ${clock(since)}"
    }
    return unknown + state
}

/** Ten bars of 3 s, oldest first: dim when the ViewModel or its session is gone. */
@Composable
private fun Sparkline(counts: List<Int>, dead: Boolean) {
    val color = if (dead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
    Row(Modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
        counts.forEach { count ->
            Box(
                Modifier.width(2.dp).height((2 + minOf(count, 6) * 1.6f).dp)
                    .alpha(if (dead) 0.5f else 0.9f).background(color, RoundedCornerShape(1.dp)),
            )
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color.Transparent
