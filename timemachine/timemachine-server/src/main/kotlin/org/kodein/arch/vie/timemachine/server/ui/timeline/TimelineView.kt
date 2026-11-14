package org.kodein.arch.vie.timemachine.server.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.VIEViewScope
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo
import org.kodein.arch.vie.timemachine.server.store.SessionTimelineEntry
import org.kodein.arch.vie.timemachine.server.store.TimelineEntry
import org.kodein.arch.vie.timemachine.server.ui.common.DiffText
import org.kodein.arch.vie.timemachine.server.ui.common.EdgeScrollbar
import org.kodein.arch.vie.timemachine.server.ui.common.KindBadge
import org.kodein.arch.vie.timemachine.server.ui.common.LocalCompact
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.common.clock
import org.kodein.arch.vie.timemachine.server.ui.common.formatTime
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.common.rememberJsonLines
import org.kodein.arch.vie.timemachine.server.ui.common.type
import org.kodein.arch.vie.timemachine.server.ui.common.splitSerialName
import org.kodein.arch.vie.timemachine.server.ui.common.style
import org.kodein.arch.vie.timemachine.server.ui.common.viewModelName
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.ViewModelStatus
import org.kodein.arch.vie.timemachine.server.ui.designsystem.ButtonSize
import org.kodein.arch.vie.timemachine.server.ui.designsystem.ButtonVariant
import org.kodein.arch.vie.timemachine.server.ui.designsystem.Chip
import org.kodein.arch.vie.timemachine.server.ui.designsystem.ChipDot
import org.kodein.arch.vie.timemachine.server.ui.designsystem.ChipSize
import org.kodein.arch.vie.timemachine.server.ui.designsystem.ChipSquareDot
import org.kodein.arch.vie.timemachine.server.ui.designsystem.ChipTone
import org.kodein.arch.vie.timemachine.server.ui.designsystem.CopyAction
import org.kodein.arch.vie.timemachine.server.ui.designsystem.EmptyState
import org.kodein.arch.vie.timemachine.server.ui.designsystem.HorizontalRule
import org.kodein.arch.vie.timemachine.server.ui.designsystem.IconAction
import org.kodein.arch.vie.timemachine.server.ui.designsystem.InspectorButton
import org.kodein.arch.vie.timemachine.server.ui.designsystem.LinkAction
import org.kodein.arch.vie.timemachine.server.ui.designsystem.SectionLabel
import org.kodein.arch.vie.timemachine.server.ui.designsystem.SerialName
import org.kodein.arch.vie.timemachine.server.ui.designsystem.ToggleChip
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/** What the glyphs of the timeline say they are. */
internal const val ClearSearchDescription = "Clear the search"
internal const val ExpandRowDescription = "Expand this entry"
internal const val CollapseRowDescription = "Collapse this entry"
internal const val CopyRowDescription = "Copy this entry's JSON"

/** The widths of the design's column grid: 16 | 40 | 88 | 16 | 52 | [180] | flex. */
private val SeqWidth = 40.dp
private val TimeWidth = 88.dp
private val SourceWidth = 180.dp

/** Where a row starts: its chevron's glyph stays centred 14 dp from the edge, in its larger target. */
private val RowStart = 14.dp - InspectorDimens.iconAction / 2

/** Where an expanded row's sections start: 6 dp past the end of the SEQ column. */
private val ExpandedStart = RowStart + InspectorDimens.iconAction + 8.dp + SeqWidth + 6.dp

/**
 * The centre pane for [scope], over what the store holds: [TimelineContent] on the pane's model, its
 * selected row read from the window's selection when a key is pressed.
 */
@Composable
internal fun TimelineView(scope: InspectorScope, modifier: Modifier = Modifier, banner: @Composable () -> Unit = {}) {
    val inspector = LocalInspector.current
    val pane = remember(inspector, scope) { inspector.timelinePane(scope) }
    InspectorPane(pane) { model ->
        TimelineContent(scope, model, selectedRow = { inspector.selection.state.value.row }, modifier, banner)
    }
}

/**
 * The centre pane for [scope], as [model] has it: its header, [banner], the filter bar, the column
 * heads and the rows. The keys ↑ ↓ step through the rows, ⏎ expands the selected one, E edits and
 * intervenes on it, F toggles following; [selectedRow] says which that is when one is pressed.
 * They are the pane's wherever the focus is within it, except in the search field: a control that has
 * the focus is activated with the space bar.
 */
@Composable
internal fun VIEViewScope<TimelineVIE.Intent, NoSignal>.TimelineContent(
    scope: InspectorScope,
    model: TimelineVIE.Model,
    selectedRow: () -> TimeMachineSequence?,
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
) {
    val projection = model.projection
    val controls = model.controls
    var searching by remember { mutableStateOf(false) }
    Column(
        modifier
            .focusable()
            // Before what has the focus sees them: a row, a chip or any other clickable takes the focus when
            // it is clicked with the mouse, and would then keep Enter for itself. The search field types them.
            .onPreviewKeyEvent { event ->
                if (searching || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val selected = selectedRow()
                when (event.key) {
                    Key.DirectionDown -> post(TimelineVIE.Intent.Step(1))
                    Key.DirectionUp -> post(TimelineVIE.Intent.Step(-1))
                    Key.Enter -> selected?.let { post(TimelineVIE.Intent.ToggleExpanded(it)) }
                    Key.E -> selected?.let { post(TimelineVIE.Intent.ComposeFrom(it)) }
                    Key.F -> post(if (controls.follow is FollowState.Following) TimelineVIE.Intent.ScrolledAway else TimelineVIE.Intent.Resume)
                    else -> return@onPreviewKeyEvent false
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

/**
 * Where the timeline's header sits: the pane's margins, and [InspectorDimens.timelineHeaderVerticalPadding]
 * above and below it, the rule under it counted in the space below.
 */
private val TimelineHeaderFrame: Modifier = Modifier
    .fillMaxWidth()
    .padding(
        start = InspectorDimens.paneHorizontalPadding,
        end = InspectorDimens.paneHorizontalPadding,
        top = InspectorDimens.timelineHeaderVerticalPadding,
        bottom = InspectorDimens.timelineHeaderVerticalPadding - InspectorDimens.rule,
    )

@Composable
private fun TimelineHeaderView(header: TimelineHeader) {
    Column(Modifier.fillMaxWidth()) {
        when (header) {
            is TimelineHeader.ViewModel -> ViewModelHeader(header, TimelineHeaderFrame)
            is TimelineHeader.Session -> SessionHeader(header, TimelineHeaderFrame)
        }
        HorizontalRule()
    }
}

/** A status of the header, after its dot: [dot] square for a ViewModel that closed. */
@Composable
private fun StatusChip(label: String, tone: ChipTone, dot: Shape = CircleShape) {
    Chip(label, tone, leading = { tint -> ChipDot(tint, dot) })
}

@Composable
private fun ViewModelHeader(header: TimelineHeader.ViewModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val info = header.info
    // Whether the type info is open: one panel for the three kinds, whichever chip opened it. It is this
    // header's own: the one of another ViewModel starts closed.
    var typeInfoOpen by remember { mutableStateOf(false) }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    viewModelName(info?.typeName, header.id),
                    style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Chip(if (info == null) "unknown ViewModel" else header.shortId, ChipTone.Neutral, mono = true)
                val label = header.status.label(header.closedAt)
                when (header.status) {
                    ViewModelStatus.Closed -> StatusChip(label, ChipTone.Neutral, ChipSquareDot)
                    ViewModelStatus.SessionDisconnected -> StatusChip(label, ChipTone.Neutral)
                    ViewModelStatus.Identifying -> StatusChip(label, ChipTone.Primary)
                    ViewModelStatus.Alive -> StatusChip(label, ChipTone.Success)
                }
            }
            val sub = when {
                info != null -> "${info.typeName} · id ${header.id} · ${if (header.startSeen) "started" else "first seen"} ${clock(header.since)}"
                header.status == ViewModelStatus.Closed -> "The app no longer knows this ViewModel, so it has closed — no close time, and its type stays unknown."
                else -> "Entries arrived for a ViewModel the inspector didn't know (its start was missed). Asking the app — usually resolved within moments."
            }
            Text(
                sub, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.outline,
                style = if (info != null) InspectorTheme.text.monoSecondary else InspectorTheme.text.body,
            )
        }
        if (info != null) {
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (kind in VIEObjectKind.entries) {
                        val typeInfo = info.type(kind)
                        TypeChip(kind, typeInfo) { typeInfoOpen = !typeInfoOpen }
                    }
                }
                if (typeInfoOpen) {
                    // Under the chips, its end on theirs, over whatever is below: it takes no room in the header.
                    // It has the keyboard while it is open: Escape closes it, as a click outside does.
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = with(LocalDensity.current) { IntOffset(0, (InspectorDimens.chipMedium + TypeInfoGap).roundToPx()) },
                        onDismissRequest = { typeInfoOpen = false },
                        properties = PopupProperties(focusable = true),
                        onKeyEvent = { event ->
                            val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                            if (escape) typeInfoOpen = false
                            escape
                        },
                    ) { TypeInfoPopover(info) }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(kind: VIEObjectKind, typeInfo: VIETypeInfo, onClick: () -> Unit) {
    val colors = InspectorTheme.colors
    Chip(
        kind.name, ChipTone.Neutral, outlined = true, size = ChipSize.Medium,
        leading = { ChipDot(kind.style().color(colors)) },
        trailing = {
            Symbol(
                if (typeInfo.isSerializable) Glyph.DataObject else Glyph.TextFields, size = 14.dp,
                tint = if (typeInfo.isSerializable) colors.success else colors.error,
            )
            typeInfo.sealedSubTypes?.let { Text(it.size.toString(), style = InspectorTheme.text.monoMeta, color = MaterialTheme.colorScheme.outline) }
        },
        onClick = onClick,
    )
}

/** How far under the chips the type info opens. */
private val TypeInfoGap = 16.dp

@Composable
private fun TypeInfoPopover(info: VIEViewModelInfo) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.width(InspectorDimens.popover).background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Type info", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        for (kind in VIEObjectKind.entries) {
            val typeInfo = info.type(kind)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(kind)
                    SerialName(typeInfo.typeName, InspectorTheme.text.codeRow, Modifier.weight(1f))
                }
                FlowRow(
                    Modifier.padding(start = 60.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val ok = typeInfo.isSerializable
                    Chip(if (ok) "JSON · can intervene" else "toString only · can't intervene", if (ok) ChipTone.Success else ChipTone.Error)
                    typeInfo.sealedSubTypes?.forEach { sub -> Chip(sub, ChipTone.Neutral, outlined = true, mono = true) }
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(header: TimelineHeader.Session, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(Glyph.Lan, size = 22.dp, tint = scheme.onSurfaceVariant)
        Text(header.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = InspectorTheme.fonts.plex), color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Chip(header.shortId, ChipTone.Neutral, mono = true)
        if (header.connected) StatusChip("connected", ChipTone.Success) else StatusChip("disconnected", ChipTone.Neutral)
        Text(
            "Session · ${header.viewModelCount} ViewModel${if (header.viewModelCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1,
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
        Row(Modifier.fillMaxWidth().padding(horizontal = InspectorDimens.paneHorizontalPadding, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                for (kind in VIEObjectKind.entries) {
                    ToggleChip(
                        kind.name, selected = kind in filter.kinds, onClick = { post(TimelineVIE.Intent.ToggleKind(kind)) },
                        tone = ChipTone.Kind(kind.style()), count = projection.kindCounts[kind] ?: 0,
                    )
                }
                ToggleChip(
                    "Intervened only", selected = filter.intervenedOnly, onClick = { post(TimelineVIE.Intent.SetIntervenedOnly(!filter.intervenedOnly)) },
                    tone = ChipTone.Intervened, count = projection.intervenedCount, glyph = Glyph.TouchApp,
                )
                SearchField(filter.query, onSearchFocus) { post(TimelineVIE.Intent.SetQuery(it)) }
                if (projection.filterActive) FilterSummary(projection.hidden) { post(TimelineVIE.Intent.ClearFilters) }
            }
            FollowToggle(controls.follow, projection.newCount) { post(if (controls.follow is FollowState.Following) TimelineVIE.Intent.ScrolledAway else TimelineVIE.Intent.Resume) }
        }
        HorizontalRule()
    }
}

/** What the filters hide, and the way to show it again: only there while a filter is on. */
@Composable
private fun FilterSummary(hidden: Int, onClear: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Symbol(Glyph.FilterAlt, size = 15.dp, tint = InspectorTheme.colors.intent)
            Text(
                "$hidden ${if (hidden == 1) "entry" else "entries"} hidden",
                style = InspectorTheme.text.note, color = scheme.onSurfaceVariant,
            )
        }
        InspectorButton("Clear filters", onClick = onClear, variant = ButtonVariant.Text)
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
        Symbol(Glyph.Search, size = 16.dp, tint = scheme.outline)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search type, toString, JSON", style = InspectorTheme.text.code, color = scheme.outline, maxLines = 1)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = InspectorTheme.text.code.copy(color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
            )
        }
        if (query.isNotEmpty()) {
            IconAction(Glyph.Close, ClearSearchDescription, onClick = { onChange("") }, tint = scheme.outline)
        }
    }
}

@Composable
private fun FollowToggle(follow: FollowState, newCount: Int, onClick: () -> Unit) {
    val following = follow is FollowState.Following
    InspectorButton(
        if (follow is FollowState.Paused) "Paused · $newCount new" else "Following",
        onClick = onClick,
        glyph = if (following) Glyph.VerticalAlignBottom else Glyph.Pause,
        variant = if (following) ButtonVariant.Tonal else ButtonVariant.Outlined,
    )
}

@Composable
private fun ColumnHeads(merged: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(22.dp).padding(start = RowStart, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(InspectorDimens.iconAction))
            SectionLabel("SEQ", Modifier.width(SeqWidth), textAlign = TextAlign.End)
            SectionLabel("TIME", Modifier.width(TimeWidth))
            Box(Modifier.width(16.dp))
            SectionLabel("KIND", Modifier.width(InspectorDimens.kindBadgeWidth), textAlign = TextAlign.Center)
            if (merged) SectionLabel("VIEWMODEL", Modifier.width(SourceWidth))
            SectionLabel("TYPE · CHANGE / TOSTRING", Modifier.weight(1f))
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
    val items = projection.items
    val state = rememberLazyListState()
    val merged = scope is InspectorScope.Session
    val noMatch = projection.hidden > 0 && items.none { it.observation != null }
    val noEntries = projection.hidden == 0 && items.none { it.observation != null } && !merged &&
        (projection.header as? TimelineHeader.ViewModel)?.info != null

    // Following: the end stays in view as rows arrive. The end is the last thing the list is given, which
    // is the no-entries item when there is one.
    // It goes there again each time the list's own height changes: under the header that comes with the
    // first projection, a banner or a filter bar that wraps, the list is shortened from its end.
    // The position is asked for the next measure, not forced: this can run within a layout pass, and
    // before the list has been measured at its new height.
    val following = controls.follow is FollowState.Following
    val lastEmitted = lazyRowIndex(items.lastIndex, noMatch) + if (noEntries) 1 else 0
    LaunchedEffect(state, following, items.lastOrNull()?.key, lastEmitted) {
        if (!following || items.isEmpty()) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.viewportSize }.collect { state.requestScrollToItem(lastEmitted) }
    }
    // The developer moving away from the end pauses following; coming back to it resumes.
    // It collects for as long as the list is there: what it posts to is read when it posts.
    val currentPost by rememberUpdatedState(post)
    LaunchedEffect(state) {
        var wasMoving = false
        snapshotFlow { state.isScrollInProgress to state.canScrollForward }.distinctUntilChanged().collect { (moving, forward) ->
            // Only a scroll that ends at the end resumes: a list that fits never scrolled, so it keeps a pause.
            if (moving && forward) currentPost(TimelineVIE.Intent.ScrolledAway)
            else if (!moving && !forward && wasMoving) currentPost(TimelineVIE.Intent.Resume)
            wasMoving = moving
        }
    }
    // A newly selected row scrolls into view, whole, when the filters leave it in the list: once the list
    // is measured, for until then no row is in view, and a scroll that ends at the end would resume.
    LaunchedEffect(projection.selectedRow) {
        val selected = projection.selectedRow ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.sequence == selected }
        if (index < 0) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        val layout = state.layoutInfo
        val shown = layout.visibleItemsInfo.firstOrNull { it.key == items[index].key }
        // A row out of view comes to the top; one that an edge cuts moves by what it lacks, and no more.
        if (shown == null) state.animateScrollToItem(lazyRowIndex(index, noMatch))
        else {
            val distance = revealDistance(shown.offset, shown.size, layout.viewportStartOffset, layout.viewportEndOffset)
            if (distance != 0) state.animateScrollBy(distance.toFloat())
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            if (noMatch) item(key = "no-match", contentType = RowContent.NoMatch) { NoMatch(projection.hidden) { post(TimelineVIE.Intent.ClearFilters) } }
            itemsIndexed(items, key = { _, item -> item.key }, contentType = { _, item -> item.content() }) { _, item ->
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
            if (noEntries) item(key = "no-entries", contentType = RowContent.NoEntries) { NoEntries() }
        }
        EdgeScrollbar(rememberScrollbarAdapter(state))
        if (projection.selectedHidden) {
            InspectorButton(
                "selected row is hidden by filters — show",
                onClick = { post(TimelineVIE.Intent.ShowSelectedRow) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                glyph = Glyph.FilterAltOff, variant = ButtonVariant.Tonal,
            )
        }
        if (controls.follow is FollowState.Paused && projection.newCount > 0) {
            InspectorButton(
                "${projection.newCount} new",
                onClick = { post(TimelineVIE.Intent.Resume) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                glyph = Glyph.ArrowDownward, variant = ButtonVariant.Tonal, size = ButtonSize.Large,
            )
        }
    }
}

@Composable
private fun NoMatch(hidden: Int, onClear: () -> Unit) {
    EmptyState(
        "No entry matches the filters", Modifier.fillMaxWidth(),
        glyph = Glyph.FilterAltOff,
        description = "$hidden ${if (hidden == 1) "entry" else "entries"} hidden",
        action = { LinkAction("Clear filters", onClick = onClear) },
    )
}

@Composable
private fun NoEntries() {
    EmptyState("No observations yet", Modifier.fillMaxWidth(), description = "Use the app — its Models, Intents and Signals appear here.")
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
    val kindStyle = observation.kind.style()
    Column {
        Row(
            Modifier
                .fillMaxWidth().height(InspectorDimens.timelineRow)
                .background(if (selected) colors.selectedRow else if (intervened) colors.intervenedRow else Color.Transparent)
                .then(if (selected) Modifier.border(BorderStroke(1.dp, scheme.primary)) else Modifier)
                .action { post(TimelineVIE.Intent.SelectRow(sequence)) }
                .padding(start = RowStart, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                if (expanded) Glyph.ExpandMore else Glyph.ChevronRight,
                description = if (expanded) CollapseRowDescription else ExpandRowDescription,
                onClick = { post(TimelineVIE.Intent.ToggleExpanded(sequence)) },
                tint = scheme.outline,
            )
            Text(
                "#$sequence", textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(SeqWidth),
                style = InspectorTheme.text.codeRow, color = if (intervened) colors.intervened else if (selected) scheme.onSurface else scheme.outline, fontWeight = if (intervened) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(formatTime(observation.at), style = InspectorTheme.text.codeRow, color = scheme.outline, maxLines = 1, modifier = Modifier.width(TimeWidth))
            Symbol(kindStyle.direction.glyph, size = 16.dp, tint = kindStyle.color(colors), mirrored = kindStyle.direction.mirrored)
            KindBadge(observation.kind)
            if (merged) {
                item.source?.let { source ->
                    val viewModelId = (item.entry as? SessionTimelineEntry.Scoped)?.viewModelId
                    Box(Modifier.width(SourceWidth)) {
                        Chip(
                            source, ChipTone.Neutral, mono = true,
                            onClick = viewModelId?.let { id -> { post(TimelineVIE.Intent.OpenViewModel(id, sequence)) } },
                        )
                    }
                } ?: Box(Modifier.width(SourceWidth))
            }
            if (observation.kind != VIEObjectKind.Model) {
                Text(
                    splitSerialName(observation.typeName).last, style = InspectorTheme.text.code, color = scheme.onSurface, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp),
                )
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val diff = item.diff
                if (observation.kind == VIEObjectKind.Model && diff == null) Chip("initial", ChipTone.Neutral)
                if (diff != null && query.isEmpty()) diff.ops.take(MaxDiffOps).forEach { DiffText(it) }
                if (diff == null || query.isNotEmpty()) {
                    Text(
                        highlight(item.snippet ?: observation.asString, query, scheme.onPrimaryContainer, scheme.primaryContainer),
                        style = InspectorTheme.text.codeRow, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                }
            }
            if (observation.asJson == null) {
                // Too big to send whole, which is not the same as not serializable: the row says which.
                Chip(if (observation.truncated) "too large" else "toString only", ChipTone.Neutral, outlined = true, mono = true)
            }
            item.interventionNumber?.let { number ->
                Chip("intervened #$number", ChipTone.Intervened, leading = { tint -> Symbol(Glyph.TouchApp, size = 13.dp, tint = tint) })
            }
            // The compact layout has no room for them: E, the Detail tab and the pinned model's Edit reach the same places.
            if (selected && item.canIntervene && !LocalCompact.current) RowActions(observation) { post(TimelineVIE.Intent.ComposeFrom(sequence)) }
        }
        HorizontalRule(color = colors.rowLine)
        if (expanded) ExpandedRow(observation)
    }
}

/** A Model row shows this many differences before it runs out of room. */
private const val MaxDiffOps = 6

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
private fun RowActions(observation: VIEObservation, onEdit: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        InspectorButton("Edit & intervene", onClick = onEdit, glyph = Glyph.EditNote, size = ButtonSize.Small)
        observation.asJson?.let { json ->
            CopyAction({ json.pretty() }, CopyRowDescription)
        }
    }
}

/** How many lines of its JSON an expanded row shows. */
private const val MaxExpandedLines = 40

@Composable
private fun ExpandedRow(observation: VIEObservation) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainerLowest).padding(start = ExpandedStart, end = 14.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SectionLabel("TOSTRING")
        Text(observation.asString, style = InspectorTheme.text.codeRow, color = scheme.onSurface)
        observation.asJson?.let { json ->
            SectionLabel("JSON", Modifier.padding(top = 4.dp))
            // The row is one item of the list, composed whole: a long document is cut, and Detail has it all.
            val (shown, more) = remember(json) {
                val lines = json.pretty().lines()
                lines.take(MaxExpandedLines).joinToString("\n") to (lines.size - MaxExpandedLines).coerceAtLeast(0)
            }
            rememberJsonLines(shown).forEachIndexed { index, line ->
                Row(Modifier.height(18.dp)) {
                    Text((index + 1).toString(), style = InspectorTheme.text.codeRow, color = scheme.outline, textAlign = TextAlign.End, modifier = Modifier.width(32.dp).padding(end = 10.dp), maxLines = 1)
                    Text(line, style = InspectorTheme.text.codeRow, color = colors.jsonKey, softWrap = false, maxLines = 1)
                }
            }
            if (more > 0) {
                Text(
                    "… $more more ${if (more == 1) "line" else "lines"} — see Detail",
                    style = InspectorTheme.text.codeRow, color = scheme.outline, maxLines = 1, modifier = Modifier.padding(start = 32.dp),
                )
            }
        }
    }
}

/**
 * What a row of the list is made of. A row that scrolls into view is composed in the nodes of one of the
 * same content that scrolled out, rather than from nothing.
 */
private enum class RowContent { Observation, Bookend, Gap, Divider, PossiblyIncomplete, Evicted, HistoryUnavailable, NoMatch, NoEntries }

private fun TimelineItem.content(): RowContent = when (val entry = entry) {
    is SessionTimelineEntry.Divider -> RowContent.Divider
    is SessionTimelineEntry.Shared -> RowContent.PossiblyIncomplete
    is SessionTimelineEntry.Scoped -> when (val e = entry.entry) {
        TimelineEntry.HistoryUnavailable -> RowContent.HistoryUnavailable
        is TimelineEntry.Evicted -> RowContent.Evicted
        is TimelineEntry.PossiblyIncomplete -> RowContent.PossiblyIncomplete
        is TimelineEntry.Message -> when (e.row.message) {
            is TimeMachineClientMessage.Observed -> RowContent.Observation
            is TimeMachineClientMessage.ObservationsDropped -> RowContent.Gap
            is TimeMachineClientMessage.ViewModelStarted, is TimeMachineClientMessage.ViewModelClosed -> RowContent.Bookend
        }
    }
}

// ---- Markers and bookends --------------------------------------------------------------------

@Composable
private fun MarkerRow(item: TimelineItem) {
    when (val entry = item.entry) {
        is SessionTimelineEntry.Divider -> DividerRow(entry)
        is SessionTimelineEntry.Shared -> PossiblyIncompleteRow(entry.entry.range, shared = true)
        is SessionTimelineEntry.Scoped -> when (val e = entry.entry) {
            TimelineEntry.HistoryUnavailable -> HistoryUnavailableRow()
            is TimelineEntry.Evicted -> EvictedRow(e.count)
            is TimelineEntry.PossiblyIncomplete -> PossiblyIncompleteRow(e.range, shared = false)
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
private fun Bookend(sequence: TimeMachineSequence, at: Instant, started: Boolean, label: String?) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().height(InspectorDimens.softMarker).padding(start = RowStart, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(InspectorDimens.iconAction))
            Text("#$sequence", style = InspectorTheme.text.codeRow, color = scheme.outline, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(SeqWidth))
            Text(formatTime(at), style = InspectorTheme.text.codeRow, color = scheme.outline, maxLines = 1, modifier = Modifier.width(TimeWidth))
            Symbol(if (started) Glyph.PlayCircle else Glyph.StopCircle, size = 16.dp, tint = if (started) colors.success else scheme.outline)
            Text(
                "${label ?: "ViewModel"} ${if (started) "started" else "closed"}",
                style = InspectorTheme.text.label, color = scheme.onSurfaceVariant, maxLines = 1,
            )
            HorizontalRule(Modifier.weight(1f))
        }
        HorizontalRule(color = colors.rowLine)
    }
}

@Composable
private fun DividerRow(divider: SessionTimelineEntry.Divider) {
    val scheme = MaterialTheme.colorScheme
    val (glyph, text) = when (divider.kind) {
        SessionTimelineEntry.Divider.Kind.Connected -> Glyph.Link to "connected"
        SessionTimelineEntry.Divider.Kind.Reconnected -> Glyph.Sync to "reconnected"
        SessionTimelineEntry.Divider.Kind.Disconnected -> Glyph.PowerOff to "disconnected"
    }
    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLowest)) {
        HorizontalRule()
        Row(
            Modifier.fillMaxWidth().height(28.dp).padding(start = 22.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(glyph, size = 16.dp, tint = scheme.primary)
            Text("session $text at ${formatTime(divider.at)}", style = InspectorTheme.text.codeRow, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                drawLine(colors.gapStripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stripe)
                x += stripe * 2
            }
        },
    ) {
        HorizontalRule(color = colors.gap)
        Row(
            Modifier.fillMaxWidth().height(InspectorDimens.topMarker).padding(start = 28.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(Glyph.LinkOff, size = 17.dp, tint = colors.gap)
            Text("Gap", style = InspectorTheme.text.label.copy(fontWeight = FontWeight.SemiBold), color = colors.gap)
            Text(
                "$count ${if (count == 1) "entry" else "entries"} dropped${label?.let { " · $it" }.orEmpty()}",
                style = InspectorTheme.text.body, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text("#$sequence", style = InspectorTheme.text.monoSecondary, color = colors.gap)
        }
        HorizontalRule(color = colors.gap)
    }
}

/**
 * The session's sequences [range] never arrived. In a ViewModel's timeline some may be that ViewModel's;
 * in a session's, where the row is [shared] by the ViewModels alive then, they may be any of theirs.
 */
@Composable
private fun PossiblyIncompleteRow(range: ClosedRange<TimeMachineSequence>, shared: Boolean) {
    val colors = InspectorTheme.colors
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
    Row(
        Modifier.fillMaxWidth().height(InspectorDimens.softMarker)
            .drawBehind {
                drawLine(colors.possiblyIncomplete, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                drawLine(colors.possiblyIncomplete, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            }
            .padding(start = 28.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(Glyph.Help, size = 16.dp, tint = colors.possiblyIncomplete)
        Text("Possibly incomplete", style = InspectorTheme.text.label.copy(fontWeight = FontWeight.SemiBold), color = colors.possiblyIncomplete)
        Text(
            if (shared) "entries were lost in this session here; they may belong to any of its ViewModels"
            else "entries were lost in this session here; some may belong to this ViewModel",
            style = InspectorTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(if (range.start == range.endInclusive) "#${range.start}" else "#${range.start}–#${range.endInclusive}", style = InspectorTheme.text.monoSecondary, color = colors.possiblyIncomplete)
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
        HorizontalRule(Modifier.weight(1f), color = colors.strongOutline)
        Row(
            Modifier.height(22.dp).background(scheme.surfaceContainerHigh, InspectorPillShape).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(Glyph.Inventory2, size = 15.dp, tint = colors.evicted)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = InspectorTheme.fonts.mono, fontWeight = FontWeight.Medium)) { append(count.toString()) }
                    append(" older ${if (count == 1) "entry" else "entries"} evicted · timeline size limit")
                },
                style = InspectorTheme.text.note, color = colors.evicted, maxLines = 1,
            )
        }
        HorizontalRule(Modifier.weight(1f), color = colors.strongOutline)
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
            Symbol(Glyph.HistoryToggleOff, size = 17.dp, tint = colors.historyUnavailable)
            Text("Earlier history unavailable", style = InspectorTheme.text.label.copy(fontWeight = FontWeight.SemiBold), color = colors.historyUnavailable, maxLines = 1)
            Text(
                "— the inspector was restarted while the app kept running",
                style = InspectorTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalRule(color = colors.rowLine)
    }
}
