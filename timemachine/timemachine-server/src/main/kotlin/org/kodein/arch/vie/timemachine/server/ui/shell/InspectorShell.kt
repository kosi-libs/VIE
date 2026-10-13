package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.kodein.arch.vie.timemachine.server.ui.common.CompactBelow
import org.kodein.arch.vie.timemachine.server.ui.common.LocalCompact
import org.kodein.arch.vie.timemachine.server.ui.common.ProvideCompact
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.rightpane.RightPane
import org.kodein.arch.vie.timemachine.server.ui.sessions.SessionsPresentation
import org.kodein.arch.vie.timemachine.server.ui.sessions.SessionsVIE
import org.kodein.arch.vie.timemachine.server.ui.sessions.SessionsView
import org.kodein.arch.vie.timemachine.server.ui.sessions.sessionsPane
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol
import org.kodein.arch.vie.timemachine.server.ui.timeline.TimelineView


/**
 * The window's content over what the store holds: the layout, filled with the panes that read it. The
 * rejection banners can be dismissed, and the cannot-listen screen retried.
 */
@Composable
internal fun InspectorShell() {
    val inspector = LocalInspector.current
    val viewState = LocalInspectorViewState.current
    // Edit & intervene opens the Composer: a right pane folded away in the compact layout unfolds to show it.
    LaunchedEffect(inspector, viewState) {
        inspector.selection.state.map { it.composerRequest != null }.distinctUntilChanged().filter { it }.collect { viewState.rightCollapsed = false }
    }
    InspectorPane(inspector.noticesPane()) { notices ->
        InspectorLayout(
            notices = notices,
            onIntent = { post(it) },
            empty = { EmptyScreen() },
            statusBar = { StatusBar() },
            sessions = { presentation, modifier -> SessionsView(presentation, modifier) },
            centre = { CentrePane(notices, it) },
            right = { RightPane(it) },
        )
    }
}

/**
 * The window's layout: the rejection banners, then the sessions, the centre and the right pane (or the
 * empty or cannot-listen screen when there is nothing to browse), then the status bar. Each part comes
 * in as a slot, so that what fills them can be the store's panes, or anything else.
 *
 * Under [CompactBelow] of width the sessions are a rail and a drawer, and the right pane can be folded
 * away; [LocalCompact] says so to whatever the slots draw. The centre and the right pane are placed by
 * either layout as one composition, so that dragging the window's edge across the breakpoint keeps a
 * paused timeline's scroll position and an editor's focus.
 */
@Composable
private fun InspectorLayout(
    notices: NoticesVIE.Model,
    onIntent: (NoticesVIE.Intent) -> Unit,
    empty: @Composable () -> Unit,
    statusBar: @Composable () -> Unit,
    sessions: @Composable (SessionsPresentation, Modifier) -> Unit,
    centre: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewState = LocalInspectorViewState.current
    ProvideCompact(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { compact ->
        val total = maxWidth
        // Leaving the compact layout closes its drawer, so that coming back does not find it open.
        LaunchedEffect(compact) { if (!compact) viewState.drawerOpen = false }
        val currentCentre by rememberUpdatedState(centre)
        val currentRight by rememberUpdatedState(right)
        val centrePane = remember { movableContentOf<Modifier> { currentCentre(it) } }
        val rightPane = remember { movableContentOf<Modifier> { currentRight(it) } }
        Column(Modifier.fillMaxSize()) {
            notices.rejections.forEach { rejection -> RejectionBanner(rejection) { onIntent(NoticesVIE.Intent.DismissRejection(rejection.sessionId)) } }
            Column(Modifier.weight(1f).fillMaxSize()) {
                when (val screen = notices.screen) {
                    NoticesVIE.Screen.Empty -> empty()
                    is NoticesVIE.Screen.CannotListen -> CannotListenScreen(screen.message) { onIntent(NoticesVIE.Intent.Retry) }
                    NoticesVIE.Screen.None ->
                        if (compact) CompactPanes(total, sessions, centrePane, rightPane)
                        else PanesLayout(total, sessions, centrePane, rightPane)
                }
            }
            statusBar()
        }
    }
}

/** What the centre pane keeps for itself, so the sessions and the right pane can be dragged. */
private val MinCentre = 300.dp

/** The three panes side by side, the dividers between them dragged within what each may take. */
@Composable
private fun PanesLayout(
    total: Dp,
    sessionsPane: @Composable (SessionsPresentation, Modifier) -> Unit,
    centrePane: @Composable (Modifier) -> Unit,
    rightPane: @Composable (Modifier) -> Unit,
) {
    val viewState = LocalInspectorViewState.current
    val sessions = viewState.sessionsWidth.coerceIn(InspectorDimens.minSessionsPane, widest(total - viewState.rightWidth - MinCentre, InspectorDimens.minSessionsPane))
    val right = viewState.rightWidth.coerceIn(InspectorDimens.minRightPane, widest(total - sessions - MinCentre, InspectorDimens.minRightPane))
    Row(Modifier.fillMaxSize()) {
        sessionsPane(SessionsPresentation.Pane, Modifier.width(sessions).fillMaxHeight())
        Splitter(onDrag = { viewState.sessionsWidth = (sessions + it).coerceIn(InspectorDimens.minSessionsPane, widest(total - right - MinCentre, InspectorDimens.minSessionsPane)) })
        centrePane(Modifier.weight(1f).fillMaxHeight())
        Splitter(onDrag = { viewState.rightWidth = (right - it).coerceIn(InspectorDimens.minRightPane, widest(total - sessions - MinCentre, InspectorDimens.minRightPane)) })
        rightPane(Modifier.width(right).fillMaxHeight())
    }
}

/**
 * The compact layout's panes: the sessions as a rail, the centre pane, and the right pane, which has a
 * width of its own and can be folded away. The sessions' drawer comes in over all of it.
 */
@Composable
private fun CompactPanes(
    total: Dp,
    sessionsPane: @Composable (SessionsPresentation, Modifier) -> Unit,
    centrePane: @Composable (Modifier) -> Unit,
    rightPane: @Composable (Modifier) -> Unit,
) {
    val viewState = LocalInspectorViewState.current
    val rail = InspectorDimens.navigationRail
    // The sheet takes the keyboard while it is open, so the centre pane, whose keys step through the rows,
    // is given it back when the sheet closes. There is nothing to give it to when no timeline is shown.
    val centreFocus = remember { FocusRequester() }
    var wasOpen by remember { mutableStateOf(viewState.drawerOpen) }
    LaunchedEffect(viewState.drawerOpen) {
        if (wasOpen && !viewState.drawerOpen) runCatching { centreFocus.requestFocus() }
        wasOpen = viewState.drawerOpen
    }
    ModalSheet(
        open = viewState.drawerOpen,
        onDismiss = { viewState.drawerOpen = false },
        sheetWidth = viewState.sessionsWidth.coerceAtLeast(InspectorDimens.minSessionsPane),
        sheet = { sessionsPane(SessionsPresentation.Drawer, Modifier.fillMaxSize()) },
    ) {
        Row(Modifier.fillMaxSize()) {
            sessionsPane(SessionsPresentation.Rail, Modifier.width(rail).fillMaxHeight())
            centrePane(Modifier.weight(1f).fillMaxHeight().focusRequester(centreFocus))
            if (viewState.rightCollapsed) {
                CollapsedRightPane { viewState.rightCollapsed = false }
            } else {
                val room = widest(total - rail - MinCentre, InspectorDimens.minRightPane)
                val right = viewState.compactRightWidth.coerceIn(InspectorDimens.minRightPane, room)
                Splitter(onDrag = { viewState.compactRightWidth = (right - it).coerceIn(InspectorDimens.minRightPane, room) })
                rightPane(Modifier.width(right).fillMaxHeight())
            }
        }
    }
}

private val CollapsedWidth = 36.dp

/** What the button that unfolds the right pane says it is; [HideRightPaneDescription] is the one that folds it away. */
internal const val ShowRightPaneDescription = "Show the right pane"

/** What a folded right pane leaves: a strip, on the pane's surface, with the way to unfold it level with the pane's header. */
@Composable
private fun CollapsedRightPane(onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.width(CollapsedWidth).fillMaxHeight().background(scheme.surfaceContainerLow)) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(scheme.outlineVariant))
        Box(Modifier.weight(1f).height(InspectorDimens.paneHeader), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = onOpen)
                    .semantics { contentDescription = ShowRightPaneDescription },
                contentAlignment = Alignment.Center,
            ) {
                Symbol("right_panel_open", size = 18.dp, tint = scheme.onSurfaceVariant)
            }
        }
    }
}

/** [max], but never under [min]: a narrow window overflows rather than inverting the range. */
private fun widest(max: Dp, min: Dp): Dp = if (max < min) min else max

/** The centre pane over what the store and the selection hold: the timeline of the selected scope. */
@Composable
private fun CentrePane(notices: NoticesVIE.Model, modifier: Modifier) {
    val selection by LocalInspector.current.selection.state.collectAsState()
    val state by LocalInspector.current.store.state.collectAsState()
    CentreLayout(
        scope = selection.resolvedIn(state).scope,
        modifier = modifier,
        noViewModel = { NoViewModel(it) },
        timeline = { scope, slot -> TimelineView(scope, slot) { notices.disconnected?.let { DisconnectedBanner(it) } } },
    )
}

/** The centre pane's layout: the timeline of [scope], or what says there is none to show. */
@Composable
private fun CentreLayout(
    scope: InspectorScope?,
    modifier: Modifier,
    noViewModel: @Composable (Modifier) -> Unit,
    timeline: @Composable (InspectorScope, Modifier) -> Unit,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (scope == null) noViewModel(Modifier.weight(1f).fillMaxSize())
        else timeline(scope, Modifier.weight(1f).fillMaxSize())
    }
}

@Composable
private fun NoViewModel(modifier: Modifier) {
    InspectorPane(LocalInspector.current.sessionsPane()) { model -> NoViewModelContent(model, modifier) }
}

/** No ViewModel is selected: what to do, and how many the connected session has. */
@Composable
private fun NoViewModelContent(model: SessionsVIE.Model, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val session = model.sessions.firstOrNull { it.connected } ?: model.sessions.firstOrNull()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Symbol("view_timeline", size = 28.dp, tint = scheme.outline)
        Text("Select a ViewModel to see its timeline", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp), color = scheme.onSurfaceVariant)
        session?.let {
            val count = it.viewModels.size
            Text("${it.name} has $count ViewModel${if (count == 1) "" else "s"} · pick one on the left", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = scheme.outline)
        }
    }
}
