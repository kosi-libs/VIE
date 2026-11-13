package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.VIEViewScope
import org.kodein.arch.vie.timemachine.server.ui.common.LiveDot
import org.kodein.arch.vie.timemachine.server.ui.common.ScrollbarBox
import org.kodein.arch.vie.timemachine.server.ui.common.TextAction
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.common.clock
import org.kodein.arch.vie.timemachine.server.ui.common.viewModelName
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.ViewModelStatus
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/**
 * How the sessions are shown: the left [Pane] of the full layout, or, in the compact one, the [Rail] and
 * the [Drawer] it opens, which is the pane that closes when a scope is picked from it.
 */
internal enum class SessionsPresentation { Pane, Rail, Drawer }

/** The sessions, in [presentation]: [SessionsContent] or [SessionsRail], over what the store holds. */
@Composable
internal fun SessionsView(modifier: Modifier = Modifier, presentation: SessionsPresentation = SessionsPresentation.Pane) {
    val pane = LocalInspector.current.sessionsPane()
    InspectorPane(pane) { model -> SessionsSlot(model, presentation, modifier) }
}

/**
 * [model] in [presentation]. The window's layout and the Inspector window's page both fill their sessions
 * slot with it, so that the three look the same in both. The rail's menu opens the drawer, and a scope
 * picked in the drawer closes it.
 */
@Composable
private fun VIEViewScope<SessionsVIE.Intent, NoSignal>.SessionsSlot(model: SessionsVIE.Model, presentation: SessionsPresentation, modifier: Modifier) {
    val viewState = LocalInspectorViewState.current
    when (presentation) {
        SessionsPresentation.Pane -> SessionsContent(model, modifier)
        SessionsPresentation.Rail -> SessionsRail(model, modifier, onMenu = { viewState.drawerOpen = true }) { post(it) }
        SessionsPresentation.Drawer -> SessionsContent(model, modifier, onScopePicked = { viewState.drawerOpen = false })
    }
}

/** What a session's chevron says a click on it does. */
internal const val CollapseSessionDescription = "Fold the session's ViewModels away"
internal const val ExpandSessionDescription = "Unfold the session's ViewModels"

/** What the left pane shows of [model]. [onScopePicked] is called once a click has picked a scope. */
@Composable
internal fun VIEViewScope<SessionsVIE.Intent, NoSignal>.SessionsContent(model: SessionsVIE.Model, modifier: Modifier = Modifier, onScopePicked: () -> Unit = {}) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().height(InspectorDimens.paneHeader).padding(start = InspectorDimens.paneHorizontalPadding, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sessions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(model.sessions.size.toString(), style = InspectorTheme.text.monoSecondary, color = MaterialTheme.colorScheme.outline)
            Box(Modifier.weight(1f))
            TextAction(
                "Clear disconnected", glyph = Glyph.DeleteSweep, enabled = model.disconnectedSessions > 0,
            ) { post(SessionsVIE.Intent.ClearDisconnected) }
        }
        val list = rememberLazyListState()
        ScrollbarBox(rememberScrollbarAdapter(list), Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 6.dp),
                state = list,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(model.sessions, key = { it.id.toString() }) { session ->
                    SessionBlock(session, model.selected, collapsed = session.id in model.controls.collapsed) { intent ->
                        post(intent)
                        if (intent is SessionsVIE.Intent.SelectScope) onScopePicked()
                    }
                }
            }
        }
    }
}

/**
 * A session: its row, which selects it, and under it its ViewModels and its clear action. [collapsed],
 * only its row is left, which then says how many ViewModels it hides; the row's chevron folds and unfolds.
 */
@Composable
private fun SessionBlock(session: SessionItem, selected: InspectorScope?, collapsed: Boolean, post: (SessionsVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val onSession = selected == InspectorScope.Session(session.id)
    Column(
        Modifier.fillMaxWidth().alpha(if (session.connected) 1f else 0.62f).padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth().height(InspectorDimens.sessionItem)
                .background(if (onSession) scheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
                .action { post(SessionsVIE.Intent.SelectScope(InspectorScope.Session(session.id))) }
                .padding(start = 6.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(
                if (collapsed) Glyph.ChevronRight else Glyph.ExpandMore, size = 18.dp, tint = scheme.outline,
                modifier = Modifier.action(description = if (collapsed) ExpandSessionDescription else CollapseSessionDescription) {
                    post(SessionsVIE.Intent.ToggleSession(session.id))
                },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (onSession) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(session.shortId, style = InspectorTheme.text.monoMeta, color = scheme.outline, maxLines = 1)
                    LiveDot(session.connected)
                }
                val since =
                    if (session.connected) "connected ${clock(session.connectedAt)}"
                    else "${clock(session.connectedAt)} – ${session.disconnectedAt?.let(::clock) ?: "?"} · disconnected"
                val count = session.viewModels.size
                Text(
                    if (collapsed) "$since · $count ${if (count == 1) "ViewModel" else "ViewModels"}" else since,
                    style = InspectorTheme.text.monoMeta, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (onSession) {
                Text("all ViewModels", style = InspectorTheme.text.tag, color = scheme.onPrimaryContainer, maxLines = 1)
            }
        }
        if (!collapsed) {
            session.viewModels.forEach { vm ->
                ViewModelRow(vm, session, selected == InspectorScope.ViewModel(session.id, vm.id)) {
                    post(SessionsVIE.Intent.SelectScope(InspectorScope.ViewModel(session.id, vm.id)))
                }
            }
            TextAction(
                session.clearLabel,
                modifier = Modifier.padding(start = 16.dp).align(Alignment.Start),
                glyph = if (session.connected) Glyph.ClearAll else Glyph.DeleteSweep,
                enabled = session.clearEnabled,
                height = 24.dp,
            ) { post(SessionsVIE.Intent.ClearSession(session.id)) }
        }
    }
}

@Composable
private fun ViewModelRow(vm: ViewModelItem, session: SessionItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val closed = vm.status == ViewModelStatus.Closed
    val name = viewModelName(vm.typeName, vm.id)
    Row(
        Modifier
            .fillMaxWidth().height(InspectorDimens.viewModelItem)
            .alpha(if (closed && session.connected) 0.6f else 1f)
            .background(if (selected) scheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
            .action(onClick = onClick)
            .padding(start = 30.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    name, style = InspectorTheme.text.monoTitle, color = if (selected) scheme.onPrimaryContainer else scheme.onSurface, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (vm.typeName != null) Text(vm.shortId, style = InspectorTheme.text.monoMeta, color = scheme.outline, maxLines = 1)
            }
            Text(vm.meta(session), style = MaterialTheme.typography.bodySmall, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Sparkline(vm.sparkline, dead = vm.status.gone)
            Text(if (vm.rowCount == 1) "1 entry" else "${vm.rowCount} entries", style = InspectorTheme.text.monoMeta, color = scheme.outline, maxLines = 1)
        }
    }
}

/**
 * What the row says under the name: the status and, for an alive ViewModel, when it started or was first
 * seen. A ViewModel being identified says who is asked for its type, rather than the status's own label.
 */
private fun ViewModelItem.meta(session: SessionItem): String {
    val unknown = if (typeName == null) "Unknown · " else ""
    val started = if (status == ViewModelStatus.Alive) " · ${if (startSeen) "started" else "first seen"} ${clock(since)}" else ""
    val state = if (status == ViewModelStatus.Identifying) "asking ${session.name} for its type…" else status.label(closedAt) + started
    return unknown + state
}

/** The activity sparkline: bars of [SparklineBar] apart by [SparklineGap], at most [SparklineHeight] high. */
private val SparklineBar = 2.dp
private val SparklineGap = 1.dp
private val SparklineHeight = 12.dp
private val SparklineCorner = 1.dp

/**
 * Ten bars of 3 s, oldest first: dim when the ViewModel or its session is gone. One node draws them all,
 * each bar and gap rounded to whole pixels as a row of boxes would place them, under one layer for their
 * opacity.
 */
@Composable
private fun Sparkline(counts: List<Int>, dead: Boolean) {
    val color = if (dead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .layout { measurable, _ ->
                val width = counts.size * SparklineBar.roundToPx() + (counts.size - 1).coerceAtLeast(0) * SparklineGap.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, SparklineHeight.roundToPx()))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .alpha(if (dead) 0.5f else 0.9f),
    ) {
        val bar = SparklineBar.roundToPx()
        val step = bar + SparklineGap.roundToPx()
        val corner = CornerRadius(SparklineCorner.toPx())
        counts.forEachIndexed { index, count ->
            val height = (2 + minOf(count, 6) * 1.6f).dp.roundToPx()
            drawRoundRect(
                color,
                topLeft = Offset((index * step).toFloat(), size.height - height),
                size = Size(bar.toFloat(), height.toFloat()),
                cornerRadius = corner,
            )
        }
    }
}
