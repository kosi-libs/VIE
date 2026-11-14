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
    val timelineRow: Dp = 28.dp
    /** A tree node and an editor line. */
    val codeLine: Dp = 19.dp
    val treeIndent: Dp = 14.dp
    /** A bookend row and the soft markers. */
    val softMarker: Dp = 26.dp
    /** The Gap marker and the markers pinned to the top. */
    val topMarker: Dp = 30.dp
    val filterChip: Dp = 26.dp
    val searchField: Dp = 28.dp

    /** The least size of a glyph button: whatever its glyph's size, a click lands in it, and its hover fills it. */
    val iconAction: Dp = 26.dp

    /** The least height of a text link, "Show" or "Resend": as high as the smallest button. */
    val linkAction: Dp = 22.dp

    /** What a pane keeps between its edges and its headers, bars and tab contents. */
    val paneHorizontalPadding: Dp = 14.dp

    val statusBar: Dp = 26.dp
    val paneHeader: Dp = 40.dp
    val sessionItem: Dp = 42.dp
    val viewModelItem: Dp = 40.dp
    val navigationRail: Dp = 56.dp
    val tabRow: Dp = 38.dp

    /** The heights of a button: in a row (`ButtonSize.Small`), in a header or a bar (`Medium`), for a main action (`Large`). */
    val buttonSmall: Dp = 22.dp
    val buttonMedium: Dp = 26.dp
    val buttonLarge: Dp = 34.dp

    /** The heights of a chip: a badge beside a name or in a row (`ChipSize.Small`), and a chip that stands on its own (`Medium`). */
    val chipSmall: Dp = 18.dp
    val chipMedium: Dp = 24.dp

    /** A scrollbar's thumb, and the track it lies in over the edge of what scrolls. */
    val scrollbarThumb: Dp = 6.dp
    val scrollbarTrack: Dp = 8.dp

    /** The thickness of a rule: between panes, rows and segments, and on each side of a marker's label. */
    val rule: Dp = 1.dp

    /** The thickness of an outline: an outlined button's, chip's or control's. */
    val outline: Dp = 1.dp

    val dividerHitArea: Dp = 8.dp
    val minSessionsPane: Dp = 200.dp
    val minRightPane: Dp = 320.dp
    val rightPane: Dp = 420.dp
    val compactRightPane: Dp = 360.dp
    val popover: Dp = 400.dp

    /** The width of a kind's badge, and of the timeline's KIND column. */
    val kindBadgeWidth: Dp = 52.dp
}

/**
 * The theme's corner radii, by slot of Material's shape scale.
 *
 * - `extraSmall`, 4 dp: read by Material's menus (the composer's type menu), by a highlighted JSON tree line and by a text link's hover.
 * - `small`, 6 dp: read by the composer's type field.
 * - `medium`, 8 dp: read by the session rows and the ViewModel rows.
 * - `large` and `extraLarge`, 12 dp: read by nothing.
 *
 * The views write their other radii by hand, mostly at these values: the search field and the composer's
 * result message at 6, cards, notes and the JSON editor at 8, the type info popover at 12.
 * Some have no slot: the filter chips at 7, the rail's tiles and the probe snippet's card at 10.
 * Buttons and pills are [InspectorPillShape]. Chips and badges are [InspectorChipShape]: 4 dp like
 * `extraSmall`, but their own, so that a menu's corners can change with no chip following.
 */
val InspectorShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** Buttons and pills are fully rounded. */
val InspectorPillShape = CircleShape

/** Chips and badges: an id, a status, a kind, a type. */
val InspectorChipShape = RoundedCornerShape(4.dp)
