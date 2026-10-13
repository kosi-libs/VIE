package org.kodein.arch.vie.timemachine.server.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo
import org.kodein.arch.vie.timemachine.server.store.SessionTimelineEntry
import org.kodein.arch.vie.timemachine.server.store.TimelineEntry
import org.kodein.arch.vie.timemachine.server.ui.common.DiffText
import org.kodein.arch.vie.timemachine.server.ui.common.HorizontalRule
import org.kodein.arch.vie.timemachine.server.ui.common.KindBadge
import org.kodein.arch.vie.timemachine.server.ui.common.LocalCompact
import org.kodein.arch.vie.timemachine.server.ui.common.TextAction
import org.kodein.arch.vie.timemachine.server.ui.common.clock
import org.kodein.arch.vie.timemachine.server.ui.common.colorJsonLine
import org.kodein.arch.vie.timemachine.server.ui.common.copyToClipboard
import org.kodein.arch.vie.timemachine.server.ui.common.formatTime
import org.kodein.arch.vie.timemachine.server.ui.common.kind
import org.kodein.arch.vie.timemachine.server.ui.common.kindContainer
import org.kodein.arch.vie.timemachine.server.ui.common.monoStyle
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.common.role
import org.kodein.arch.vie.timemachine.server.ui.common.splitSerialName
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.ViewModelStatus
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/** The widths of the design's column grid: 16 | 40 | 88 | 16 | 52 | [180] | flex. */
private val SeqWidth = 40.dp
private val TimeWidth = 88.dp
private val SourceWidth = 180.dp

/**
 * The centre pane for [scope], over what the store holds: [TimelineContent] on the pane's model, its
 * selected row read from the window's selection when a key is pressed.
 */
@Composable
internal fun TimelineView(scope: InspectorScope, modifier: Modifier = Modifier, banner: @Composable () -> Unit = {}) {
    val inspector = LocalInspector.current
    val pane = remember(inspector, scope) { inspector.timelinePane(scope) }
    InspectorPane(pane) { model ->
        TimelineContent(scope, model, selectedRow = { inspector.selection.state.value.row }, modifier, banner) { post(it) }
    }
}

/**
 * The centre pane for [scope], as [model] has it: its header, [banner], the filter bar, the column
 * heads and the rows. The keys ↑ ↓ step through the rows, ⏎ expands the selected one, E edits and
 * intervenes on it, F toggles following; [selectedRow] says which that is when one is pressed.
 */
@Composable
private fun TimelineContent(
    scope: InspectorScope,
    model: TimelineVIE.Model,
    selectedRow: () -> TimeMachineSequence?,
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
    post: (TimelineVIE.Intent) -> Unit,
) {
    val projection = model.projection
    val controls = model.controls
    var searching by remember { mutableStateOf(false) }
    Column(
        modifier
            .focusable()
            .onKeyEvent { event ->
                if (searching || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val selected = selectedRow()
                when (event.key) {
                    Key.DirectionDown -> post(TimelineVIE.Intent.Step(1))
                    Key.DirectionUp -> post(TimelineVIE.Intent.Step(-1))
                    Key.Enter -> selected?.let { post(TimelineVIE.Intent.ToggleExpanded(it)) }
                    Key.E -> selected?.let { post(TimelineVIE.Intent.ComposeFrom(it)) }
                    Key.F -> post(if (controls.follow is FollowState.Following) TimelineVIE.Intent.ScrolledAway else TimelineVIE.Intent.Resume)
                    else -> return@onKeyEvent false
                }
                true
            },
    ) {
        projection.header?.let { TimelineHeaderView(it) }
        banner()
        FilterBar(projection, controls, onSearchFocus = { searching = it }) { post(it) }
        ColumnHeads(merged = scope is InspectorScope.Session)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Rows(scope, projection, controls) { post(it) }
        }
    }
}

// ---- Header ----------------------------------------------------------------------------------

@Composable
private fun TimelineHeaderView(header: TimelineHeader) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            when (header) {
                is TimelineHeader.ViewModel -> ViewModelHeader(header)
                is TimelineHeader.Session -> SessionHeader(header)
            }
            HorizontalRule()
        }
        if (header is TimelineHeader.ViewModel) header.info?.let { TypeInfoPopover(it, Modifier.align(Alignment.TopEnd).padding(top = 50.dp, end = 14.dp)) }
    }
}

@Composable
private fun StatusPill(text: String, color: Color, container: Color, square: Boolean = false, dot: Boolean = true) {
    Row(
        Modifier.height(20.dp).background(container, CircleShape).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) Box(Modifier.size(6.dp).background(color, if (square) RoundedCornerShape(1.dp) else CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp), color = color, maxLines = 1)
    }
}

@Composable
private fun IdChip(text: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text, style = monoStyle(11f, scheme.onSurfaceVariant), maxLines = 1,
        modifier = Modifier.background(scheme.surfaceContainerHigh, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun ViewModelHeader(header: TimelineHeader.ViewModel) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val info = header.info
    val state = LocalInspectorViewState.current
    val gone = header.status == ViewModelStatus.Closed || !header.sessionConnected
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    info?.let { splitSerialName(it.typeName).last } ?: header.shortId,
                    style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IdChip(if (info == null) "unknown ViewModel" else header.shortId)
                when {
                    header.status == ViewModelStatus.Closed ->
                        StatusPill(header.closedAt?.let { "closed ${clock(it)}" } ?: "closed · time unknown", scheme.outline, scheme.surfaceContainerHigh, square = true)
                    !header.sessionConnected ->
                        StatusPill("session disconnected", scheme.outline, scheme.surfaceContainerHigh)
                    header.status == ViewModelStatus.Identifying ->
                        StatusPill("identifying…", scheme.onPrimaryContainer, scheme.primaryContainer)
                    else -> StatusPill("alive", colors.success, colors.successContainer)
                }
            }
            val sub = when {
                info != null -> "${info.typeName} · id ${header.id} · ${if (header.startSeen) "started" else "first seen"} ${clock(header.since)}"
                header.status == ViewModelStatus.Closed -> "The app no longer knows this ViewModel, so it has closed — no close time, and its type stays unknown."
                else -> "Entries arrived for a ViewModel the inspector didn't know (its start was missed). Asking the app — usually resolved within moments."
            }
            Text(
                sub, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.outline,
                style = if (info != null) monoStyle(11f, scheme.outline) else MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            )
        }
        if (info != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (kind in VIEObjectKind.entries) {
                    val role = info.role(kind)
                    TypeChip(kind, role, open = state.typeInfoPopover != null) {
                        state.typeInfoPopover = if (state.typeInfoPopover == null) kind else null
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(kind: VIEObjectKind, role: VIETypeInfo, open: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Row(
        Modifier
            .height(InspectorDimens.chip)
            .background(scheme.surfaceContainerLow, RoundedCornerShape(6.dp))
            .border(1.dp, if (open) colors.strongOutline else scheme.outlineVariant, RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(colors.kind(kind), CircleShape))
        Text(
            kind.name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.5.sp, fontWeight = FontWeight.Normal,
                textDecoration = if (role.isSerializable) TextDecoration.None else TextDecoration.LineThrough,
            ),
            color = if (role.isSerializable) scheme.onSurfaceVariant else scheme.outline,
        )
        Symbol(if (role.isSerializable) "data_object" else "text_fields", size = 14.dp, tint = if (role.isSerializable) colors.success else colors.error)
        role.sealedSubTypes?.let { Text(it.size.toString(), style = monoStyle(10.5f, scheme.outline)) }
    }
}

@Composable
private fun TypeInfoPopover(info: org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo, modifier: Modifier) {
    val state = LocalInspectorViewState.current
    if (state.typeInfoPopover == null) return
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Column(
        modifier.width(InspectorDimens.popover).background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Type info", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        for (kind in VIEObjectKind.entries) {
            val role = info.role(kind)
            val parts = splitSerialName(role.typeName)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(kind)
                    Row(Modifier.weight(1f)) {
                        Text(parts.prefix, style = monoStyle(11.5f, scheme.outline), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(parts.last, style = monoStyle(11.5f, scheme.onSurface, bold = true), maxLines = 1)
                    }
                }
                FlowRow(
                    Modifier.padding(start = 60.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val ok = role.isSerializable
                    Text(
                        if (ok) "JSON · can intervene" else "toString only · can't intervene",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = if (ok) colors.success else colors.error,
                        modifier = Modifier.background(if (ok) colors.successContainer else colors.errorContainer, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
                    )
                    role.sealedSubTypes?.forEach { sub ->
                        Text(
                            sub, style = monoStyle(11f, scheme.onSurfaceVariant),
                            modifier = Modifier.border(1.dp, scheme.outlineVariant, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(header: TimelineHeader.Session) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol("lan", size = 22.dp, tint = scheme.onSurfaceVariant)
        Text(header.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = InspectorTheme.fonts.plex), color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        IdChip(header.shortId)
        if (header.connected) StatusPill("connected", colors.success, colors.successContainer)
        else StatusPill("disconnected", scheme.outline, scheme.surfaceContainerHigh)
        Text(
            "Session · ${header.viewModelCount} ViewModel${if (header.viewModelCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = scheme.onSurfaceVariant, maxLines = 1,
        )
    }
}

// ---- Filter bar ------------------------------------------------------------------------------

@Composable
private fun FilterBar(
    projection: TimelineProjection,
    controls: TimelineVIE.Controls,
    onSearchFocus: (Boolean) -> Unit,
    post: (TimelineVIE.Intent) -> Unit,
) {
    val filter = controls.filter
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            for (kind in VIEObjectKind.entries) {
                KindFilterChip(kind, on = kind in filter.kinds, count = projection.kindCounts[kind] ?: 0) { post(TimelineVIE.Intent.ToggleKind(kind)) }
            }
            IntervenedFilterChip(on = filter.intervenedOnly, count = projection.intervenedCount) { post(TimelineVIE.Intent.SetIntervenedOnly(!filter.intervenedOnly)) }
            SearchField(filter.query, onSearchFocus) { post(TimelineVIE.Intent.SetQuery(it)) }
            if (projection.filterActive) FilterSummary(projection.hidden) { post(TimelineVIE.Intent.ClearFilters) }
        }
        FollowToggle(controls.follow) { post(if (controls.follow is FollowState.Following) TimelineVIE.Intent.ScrolledAway else TimelineVIE.Intent.Resume) }
        }
        HorizontalRule()
    }
}

/** A kind's filter chip: lit in the kind's colors, with a check, when the kind is shown; outlined and plain when it is not. */
@Composable
private fun KindFilterChip(kind: VIEObjectKind, on: Boolean, count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    FilterChip(
        kind.name, count, glyph = if (on) "check" else null,
        fg = if (on) colors.kind(kind) else scheme.onSurfaceVariant,
        container = if (on) colors.kindContainer(kind) else Color.Transparent,
        outlined = !on, onClick = onClick,
    )
}

/** The "Intervened only" chip, in the intervened colors when it is on, and with the touch glyph when it is off. */
@Composable
private fun IntervenedFilterChip(on: Boolean, count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    FilterChip(
        "Intervened only", count, glyph = if (on) "check" else "touch_app", glyphColor = colors.intervened,
        fg = if (on) colors.intervened else scheme.onSurfaceVariant,
        container = if (on) colors.intervenedContainer else Color.Transparent,
        outlined = !on, onClick = onClick,
    )
}

/** What the filters hide, and the way to show it again: only there while a filter is on. */
@Composable
private fun FilterSummary(hidden: Int, onClear: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Symbol("filter_alt", size = 15.dp, tint = InspectorTheme.colors.intent)
        Text(
            "$hidden ${if (hidden == 1) "entry" else "entries"} hidden",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = scheme.onSurfaceVariant,
        )
    }
    TextAction("Clear filters", onClick = onClear)
}

@Composable
private fun FilterChip(
    label: String,
    count: Int,
    glyph: String?,
    fg: Color,
    container: Color,
    outlined: Boolean,
    glyphColor: Color = fg,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(7.dp)
    Row(
        Modifier
            .height(InspectorDimens.filterChip)
            .background(container, shape)
            .then(if (outlined) Modifier.border(1.dp, InspectorTheme.colors.strongOutline, shape) else Modifier)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(start = if (glyph != null) 6.dp else 9.dp, end = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph?.let { Symbol(it, size = 15.dp, tint = glyphColor) }
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = fg, maxLines = 1)
        Text(count.toString(), style = monoStyle(10.5f, fg), modifier = Modifier.alpha(0.8f))
    }
}

@Composable
private fun SearchField(query: String, onFocus: (Boolean) -> Unit, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .width(240.dp).height(InspectorDimens.searchField)
            .background(scheme.surfaceContainerLowest, shape)
            .border(1.dp, if (query.isNotEmpty()) scheme.primary else InspectorTheme.colors.strongOutline, shape)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol("search", size = 16.dp, tint = scheme.outline)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search type, toString, JSON", style = monoStyle(12f, scheme.outline), maxLines = 1)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = monoStyle(12f, scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
            )
        }
        if (query.isNotEmpty()) {
            Symbol("close", size = 16.dp, tint = scheme.outline, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onChange("") })
        }
    }
}

@Composable
private fun FollowToggle(follow: FollowState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val following = follow is FollowState.Following
    Row(
        Modifier
            .height(InspectorDimens.filterChip)
            .then(if (following) Modifier.background(scheme.primaryContainer, InspectorPillShape) else Modifier.border(1.dp, InspectorTheme.colors.strongOutline, InspectorPillShape))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (following) scheme.onPrimaryContainer else scheme.onSurfaceVariant
        Symbol(if (following) "vertical_align_bottom" else "pause", size = 15.dp, tint = color)
        Text(
            if (follow is FollowState.Paused) "Paused · ${follow.newCount} new" else "Following",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp), color = color, maxLines = 1,
        )
    }
}

@Composable
private fun ColumnHeads(merged: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
    Column {
        Row(
            Modifier.fillMaxWidth().height(22.dp).padding(start = 6.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(16.dp))
            Text("SEQ", style = style, color = scheme.outline, textAlign = TextAlign.End, modifier = Modifier.width(SeqWidth))
            Text("TIME", style = style, color = scheme.outline, modifier = Modifier.width(TimeWidth))
            Box(Modifier.width(16.dp))
            Text("KIND", style = style, color = scheme.outline, textAlign = TextAlign.Center, modifier = Modifier.width(InspectorDimens.kindBadgeWidth))
            if (merged) Text("VIEWMODEL", style = style, color = scheme.outline, modifier = Modifier.width(SourceWidth))
            Text("TYPE · CHANGE / TOSTRING", style = style, color = scheme.outline, modifier = Modifier.weight(1f))
        }
        HorizontalRule()
    }
}

// ---- Rows ------------------------------------------------------------------------------------

@Composable
private fun Rows(
    scope: InspectorScope,
    projection: TimelineProjection,
    controls: TimelineVIE.Controls,
    post: (TimelineVIE.Intent) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val items = projection.items
    val state = rememberLazyListState()
    val merged = scope is InspectorScope.Session
    val noMatch = projection.hidden > 0 && items.none { it.observation != null }
    val noEntries = projection.hidden == 0 && items.none { it.observation != null } && !merged &&
        (projection.header as? TimelineHeader.ViewModel)?.info != null

    // Following: the end stays in view as rows arrive.
    val following = controls.follow is FollowState.Following
    LaunchedEffect(following, items.lastOrNull()?.key, items.size) {
        if (following && items.isNotEmpty()) state.scrollToItem(items.lastIndex)
    }
    // The developer moving away from the end pauses following; coming back to it resumes.
    LaunchedEffect(state) {
        var wasMoving = false
        snapshotFlow { state.isScrollInProgress to state.canScrollForward }.distinctUntilChanged().collect { (moving, forward) ->
            // Only a scroll that ends at the end resumes: a list that fits never scrolled, so it keeps a pause.
            if (moving && forward) post(TimelineVIE.Intent.ScrolledAway)
            else if (!moving && !forward && wasMoving) post(TimelineVIE.Intent.Resume)
            wasMoving = moving
        }
    }
    // A newly selected row scrolls into view, when the filters leave it in the list: once the list is
    // measured, for until then no row is in view, and a scroll that ends at the end would resume.
    LaunchedEffect(projection.selectedRow) {
        val selected = projection.selectedRow ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.sequence == selected }
        if (index < 0) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        val visible = state.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (!visible) state.animateScrollToItem(index)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            if (noMatch) item(key = "no-match") { NoMatch(projection.hidden) { post(TimelineVIE.Intent.ClearFilters) } }
            itemsIndexed(items, key = { _, item -> item.key }) { _, item ->
                val sequence = item.sequence
                when {
                    item.observation != null && sequence != null ->
                        ObservationRow(
                            item, merged = merged,
                            selected = sequence == projection.selectedRow, expanded = sequence in controls.expanded,
                            query = controls.filter.query, post = post,
                        )
                    else -> MarkerRow(item)
                }
            }
            if (noEntries) item(key = "no-entries") { NoEntries() }
        }
        if (projection.selectedHidden) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                    .background(scheme.surfaceContainerHighest, InspectorPillShape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { post(TimelineVIE.Intent.ShowSelectedRow) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol("filter_alt_off", size = 15.dp, tint = scheme.onSurfaceVariant)
                Text("selected row is hidden by filters — ", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = scheme.onSurfaceVariant)
                Text("show", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = scheme.primary)
            }
        }
        (controls.follow as? FollowState.Paused)?.takeIf { it.newCount > 0 }?.let { paused ->
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                    .height(32.dp).background(scheme.primaryContainer, CircleShape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { post(TimelineVIE.Intent.Resume) }
                    .padding(start = 10.dp, end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol("arrow_downward", size = 18.dp, tint = scheme.onPrimaryContainer)
                Text("${paused.newCount} new", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun NoMatch(hidden: Int, onClear: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Symbol("filter_alt_off", size = 26.dp, tint = scheme.outline)
        Text("No entry matches the filters", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp), color = scheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$hidden ${if (hidden == 1) "entry" else "entries"} hidden · ", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = scheme.outline)
            Text(
                "Clear filters", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = scheme.primary,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClear),
            )
        }
    }
}

@Composable
private fun NoEntries() {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("No observations yet", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp), color = scheme.onSurfaceVariant)
        Text("Use the app — its Models, Intents and Signals appear here.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = scheme.outline)
    }
}

@Composable
private fun ObservationRow(
    item: TimelineItem,
    merged: Boolean,
    selected: Boolean,
    expanded: Boolean,
    query: String,
    post: (TimelineVIE.Intent) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val observation = item.observation ?: return
    val sequence = item.sequence ?: return
    val intervened = observation.interventionId != null
    val kindColor = colors.kind(observation.kind)
    Column {
        Row(
            Modifier
                .fillMaxWidth().height(InspectorDimens.timelineRow)
                .background(if (selected) colors.selectedRow else if (intervened) colors.intervenedRow else Color.Transparent)
                .then(if (selected) Modifier.border(BorderStroke(1.dp, scheme.primary)) else Modifier)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { post(TimelineVIE.Intent.SelectRow(sequence)) }
                .padding(start = 6.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(
                if (expanded) "expand_more" else "chevron_right", size = 16.dp, tint = scheme.outline,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { post(TimelineVIE.Intent.ToggleExpanded(sequence)) },
            )
            Text(
                "#$sequence", textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(SeqWidth),
                style = monoStyle(11.5f, if (intervened) colors.intervened else if (selected) scheme.onSurface else scheme.outline, bold = intervened),
            )
            Text(formatTime(observation.at), style = monoStyle(11.5f, scheme.outline), maxLines = 1, modifier = Modifier.width(TimeWidth))
            Symbol(if (observation.kind == VIEObjectKind.Intent) "login" else "logout", size = 16.dp, tint = kindColor, mirrored = observation.kind != VIEObjectKind.Intent)
            KindBadge(observation.kind)
            if (merged) {
                item.source?.let { source ->
                    val viewModelId = (item.entry as? SessionTimelineEntry.Scoped)?.viewModelId
                    Text(
                        source, style = monoStyle(11f, if (selected) scheme.onSurface else scheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(SourceWidth).height(18.dp)
                            .background(scheme.surfaceContainerHigh, RoundedCornerShape(4.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(enabled = viewModelId != null) { viewModelId?.let { post(TimelineVIE.Intent.OpenViewModel(it, sequence)) } }
                            .padding(horizontal = 6.dp),
                    )
                } ?: Box(Modifier.width(SourceWidth))
            }
            if (observation.kind != VIEObjectKind.Model) {
                Text(
                    splitSerialName(observation.typeName).last, style = monoStyle(12f, scheme.onSurface, bold = true),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp),
                )
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val diff = item.diff
                if (observation.kind == VIEObjectKind.Model && diff == null) InitialChip()
                if (diff != null && query.isEmpty()) diff.ops.take(MaxDiffOps).forEach { DiffText(it) }
                if (diff == null || query.isNotEmpty()) {
                    Text(
                        highlight(item.snippet ?: observation.asString, query, scheme.onPrimaryContainer, scheme.primaryContainer),
                        style = monoStyle(11.5f, scheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                }
            }
            if (observation.asJson == null) {
                Text(
                    "toString only", style = monoStyle(10.5f, scheme.outline).copy(fontSize = 10.5.sp), maxLines = 1,
                    modifier = Modifier.border(1.dp, colors.strongOutline, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
                )
            }
            item.interventionNumber?.let { number -> InterventionChip(number) }
            // The compact layout has no room for them: E, the Detail tab and the pinned model's Edit reach the same places.
            if (selected && item.canIntervene && !LocalCompact.current) RowActions(observation) { post(TimelineVIE.Intent.ComposeFrom(sequence)) }
        }
        HorizontalRule(colors.rowLine)
        if (expanded) ExpandedRow(observation)
    }
}

/** A Model row shows this many differences before it runs out of room. */
private const val MaxDiffOps = 6

@Composable
private fun InitialChip() {
    val scheme = MaterialTheme.colorScheme
    Text(
        "initial", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp), color = scheme.onSurfaceVariant, maxLines = 1,
        modifier = Modifier.background(scheme.surfaceContainerHigh, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private fun highlight(text: String, query: String, color: Color, background: Color) = buildAnnotatedString {
    val hit = if (query.isEmpty()) -1 else text.indexOf(query, ignoreCase = true)
    if (hit < 0) append(text)
    else {
        append(text.substring(0, hit))
        withStyle(SpanStyle(color = color, background = background)) { append(text.substring(hit, hit + query.length)) }
        append(text.substring(hit + query.length))
    }
}

@Composable
private fun InterventionChip(number: Int) {
    val colors = InspectorTheme.colors
    Row(
        Modifier.height(18.dp).background(colors.intervenedContainer, RoundedCornerShape(9.dp)).padding(start = 4.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol("touch_app", size = 13.dp, tint = colors.intervened)
        Text("intervened #$number", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = colors.intervened, maxLines = 1)
    }
}

@Composable
private fun RowActions(observation: VIEObservation, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.height(22.dp).background(scheme.primary, InspectorPillShape)
            .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onEdit).padding(start = 6.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol("edit_note", size = 14.dp, tint = scheme.onPrimary)
        Text("Edit & intervene", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = scheme.onPrimary, maxLines = 1)
    }
    observation.asJson?.let { json ->
        Symbol("content_copy", size = 16.dp, tint = scheme.onSurfaceVariant, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { copyToClipboard(json.pretty()) })
    }
}

@Composable
private fun ExpandedRow(observation: VIEObservation) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val label = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.06.sp)
    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainerLowest).padding(start = 76.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("TOSTRING", style = label, color = scheme.outline)
        Text(observation.asString, style = monoStyle(11.5f, scheme.onSurface))
        observation.asJson?.let { json ->
            Text("JSON", style = label, color = scheme.outline, modifier = Modifier.padding(top = 4.dp))
            json.pretty().lines().forEachIndexed { index, line ->
                Row(Modifier.height(18.dp)) {
                    Text((index + 1).toString(), style = monoStyle(11.5f, scheme.outline), textAlign = TextAlign.End, modifier = Modifier.width(32.dp).padding(end = 10.dp), maxLines = 1)
                    Text(colorJsonLine(line, colors), style = monoStyle(11.5f, colors.jsonKey), softWrap = false, maxLines = 1)
                }
            }
        }
    }
}

// ---- Markers and bookends --------------------------------------------------------------------

@Composable
private fun MarkerRow(item: TimelineItem) {
    when (val entry = item.entry) {
        is SessionTimelineEntry.Divider -> DividerRow(entry)
        is SessionTimelineEntry.Shared -> PossiblyIncompleteRow(entry.entry.range)
        is SessionTimelineEntry.Scoped -> when (val e = entry.entry) {
            TimelineEntry.HistoryUnavailable -> HistoryUnavailableRow()
            is TimelineEntry.Evicted -> EvictedRow(e.count)
            is TimelineEntry.PossiblyIncomplete -> PossiblyIncompleteRow(e.range)
            is TimelineEntry.Message -> when (val message = e.row.message) {
                is TimeMachineClientMessage.ViewModelStarted -> Bookend(e.row.sequence, message.at, started = true, label = item.source)
                is TimeMachineClientMessage.ViewModelClosed -> Bookend(e.row.sequence, message.at, started = false, label = item.source)
                is TimeMachineClientMessage.ObservationsDropped -> GapRow(message.count, e.row.sequence, item.source)
                else -> Unit
            }
        }
    }
}

@Composable
private fun Bookend(sequence: TimeMachineSequence, at: kotlin.time.Instant, started: Boolean, label: String?) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().height(InspectorDimens.softMarker).padding(start = 6.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(16.dp))
            Text("#$sequence", style = monoStyle(11.5f, scheme.outline), textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(SeqWidth))
            Text(formatTime(at), style = monoStyle(11.5f, scheme.outline), maxLines = 1, modifier = Modifier.width(TimeWidth))
            Symbol(if (started) "play_circle" else "stop_circle", size = 16.dp, tint = if (started) colors.success else scheme.outline)
            Text(
                "${label ?: "ViewModel"} ${if (started) "started" else "closed"}",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = scheme.onSurfaceVariant, maxLines = 1,
            )
            Box(Modifier.weight(1f).height(1.dp).background(scheme.outlineVariant))
        }
        HorizontalRule(colors.rowLine)
    }
}

@Composable
private fun DividerRow(divider: SessionTimelineEntry.Divider) {
    val scheme = MaterialTheme.colorScheme
    val (glyph, text) = when (divider.kind) {
        SessionTimelineEntry.Divider.Kind.Connected -> "link" to "connected"
        SessionTimelineEntry.Divider.Kind.Reconnected -> "sync" to "reconnected"
        SessionTimelineEntry.Divider.Kind.Disconnected -> "power_off" to "disconnected"
    }
    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLowest)) {
        HorizontalRule()
        Row(
            Modifier.fillMaxWidth().height(28.dp).padding(start = 22.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(glyph, size = 16.dp, tint = scheme.primary)
            Text("session $text at ${formatTime(divider.at)}", style = monoStyle(11.5f, scheme.onSurface), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HorizontalRule()
    }
}

@Composable
private fun GapRow(count: Int, sequence: TimeMachineSequence, label: String?) {
    val colors = InspectorTheme.colors
    Column(
        Modifier.fillMaxWidth().clipToBounds().drawBehind {
            // 135° stripes of 6 dp, as the design's repeating gradient.
            val stripe = 6.dp.toPx()
            drawRect(colors.gapContainer)
            var x = -size.height
            while (x < size.width) {
                drawLine(colors.gapStripe, androidx.compose.ui.geometry.Offset(x, size.height), androidx.compose.ui.geometry.Offset(x + size.height, 0f), strokeWidth = stripe)
                x += stripe * 2
            }
        },
    ) {
        HorizontalRule(colors.gap)
        Row(
            Modifier.fillMaxWidth().height(InspectorDimens.topMarker).padding(start = 28.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol("link_off", size = 17.dp, tint = colors.gap)
            Text("Gap", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = colors.gap)
            Text(
                "$count ${if (count == 1) "entry" else "entries"} dropped${label?.let { " · $it" }.orEmpty()}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text("#$sequence", style = monoStyle(11f, colors.gap))
        }
        HorizontalRule(colors.gap)
    }
}

@Composable
private fun PossiblyIncompleteRow(range: ClosedRange<TimeMachineSequence>) {
    val colors = InspectorTheme.colors
    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
    Row(
        Modifier.fillMaxWidth().height(InspectorDimens.softMarker)
            .drawBehind {
                drawLine(colors.possiblyIncomplete, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                drawLine(colors.possiblyIncomplete, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            }
            .padding(start = 28.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol("help", size = 16.dp, tint = colors.possiblyIncomplete)
        Text("Possibly incomplete", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = colors.possiblyIncomplete)
        Text(
            "entries were lost in this session here; some may belong to this ViewModel",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(if (range.start == range.endInclusive) "#${range.start}" else "#${range.start}–#${range.endInclusive}", style = monoStyle(11f, colors.possiblyIncomplete))
    }
}

@Composable
private fun EvictedRow(count: Int) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Row(
        Modifier.fillMaxWidth().height(InspectorDimens.topMarker).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.strongOutline))
        Row(
            Modifier.height(22.dp).background(scheme.surfaceContainerHigh, InspectorPillShape).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol("inventory_2", size = 15.dp, tint = colors.evicted)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = InspectorTheme.fonts.mono, fontWeight = FontWeight.Medium)) { append(count.toString()) }
                    append(" older entries evicted · timeline size limit")
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = colors.evicted, maxLines = 1,
            )
        }
        Box(Modifier.weight(1f).height(1.dp).background(colors.strongOutline))
    }
}

@Composable
private fun HistoryUnavailableRow() {
    val colors = InspectorTheme.colors
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(colors.historyUnavailableContainer, Color.Transparent)))) {
        Row(
            Modifier.fillMaxWidth().height(InspectorDimens.topMarker).padding(start = 28.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol("history_toggle_off", size = 17.dp, tint = colors.historyUnavailable)
            Text("Earlier history unavailable", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = colors.historyUnavailable, maxLines = 1)
            Text(
                "— the inspector was restarted while the app kept running",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalRule(colors.rowLine)
    }
}
