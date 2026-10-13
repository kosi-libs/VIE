package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing and sizes from the design's token table (§07) and component inventory (§06). */
@Immutable
object InspectorDimens {
    val unit: Dp = 4.dp

    val timelineRow: Dp = 28.dp
    /** A tree node and an editor line. */
    val codeLine: Dp = 19.dp
    val treeIndent: Dp = 14.dp
    /** A bookend row and the soft markers. */
    val softMarker: Dp = 26.dp
    /** The Gap marker and the markers pinned to the top. */
    val topMarker: Dp = 30.dp
    val chip: Dp = 24.dp
    val filterChip: Dp = 26.dp
    val searchField: Dp = 28.dp

    val paneHorizontalPadding: Dp = 14.dp
    val paneVerticalPadding: Dp = 11.dp

    val titleBar: Dp = 30.dp
    val statusBar: Dp = 26.dp
    val paneHeader: Dp = 40.dp
    val sessionItem: Dp = 42.dp
    val viewModelItem: Dp = 40.dp
    val navigationRail: Dp = 56.dp
    val tabRow: Dp = 38.dp
    val sendButton: Dp = 34.dp

    val dividerHitArea: Dp = 8.dp
    val minSessionsPane: Dp = 200.dp
    val minRightPane: Dp = 320.dp
    val rightPane: Dp = 420.dp
    val compactRightPane: Dp = 360.dp
    val popover: Dp = 400.dp

    val kindBadgeWidth: Dp = 52.dp
    val kindBadgeHeight: Dp = 18.dp
}

/** Badge and code chip 4, filter chip and field 6, nav item, card and editor 8, popover and menu 12. */
val InspectorShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** Buttons and pills are fully rounded. */
val InspectorPillShape = CircleShape
