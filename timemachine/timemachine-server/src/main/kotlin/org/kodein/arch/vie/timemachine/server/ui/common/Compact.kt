package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/**
 * The width under which the layout is compact: the sessions become a rail and a drawer, and the right
 * pane can fold away. Not in `InspectorDimens`, which lists what the design draws.
 */
internal val CompactBelow: Dp = 1200.dp

internal fun isCompact(width: Dp): Boolean = width < CompactBelow

/**
 * Whether the part being drawn is in the compact layout. [ProvideCompact] derives it from the width it
 * is given, so a part shown on its own is full unless whoever shows it says otherwise.
 */
internal val LocalCompact = compositionLocalOf { false }

/**
 * [content] over the width this is given: it says whether that is [isCompact], and provides it as
 * [LocalCompact]. The window's layout is given the window's width.
 */
@Composable
internal fun ProvideCompact(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable BoxWithConstraintsScope.(compact: Boolean) -> Unit) {
    BoxWithConstraints(modifier) {
        val compact = isCompact(maxWidth)
        CompositionLocalProvider(LocalCompact provides compact) { content(compact) }
    }
}
