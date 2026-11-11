package org.kodein.arch.vie.timemachine.server.ui.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens


/**
 * The window's view-only state. It has no domain rule, so no ViewModel and no contract: the status
 * bar's theme toggle and the dividers write it, and it reads no store.
 */
internal class InspectorViewState {

    var dark: Boolean by mutableStateOf(false)

    var sessionsWidth: Dp by mutableStateOf(DefaultSessionsWidth)

    var rightWidth: Dp by mutableStateOf(InspectorDimens.rightPane)

    /** The right pane's width in the compact layout, which remembers its own: the design draws it narrower. */
    var compactRightWidth: Dp by mutableStateOf(InspectorDimens.compactRightPane)

    /** Whether the compact layout's sessions drawer is open. */
    var drawerOpen: Boolean by mutableStateOf(false)

    /** Whether the right pane is folded away. Only the compact layout folds it; the full one always shows it. */
    var rightCollapsed: Boolean by mutableStateOf(false)

    companion object {
        val DefaultSessionsWidth: Dp = 260.dp
    }
}

internal val LocalInspectorViewState = staticCompositionLocalOf<InspectorViewState> { error("No InspectorViewState provided") }
