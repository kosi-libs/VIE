package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.designsystem.VerticalRule
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import java.awt.Cursor


/** The cursor over a splitter: an AWT cursor wraps a native one, so it is made once. */
private val ResizeCursor = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))

/** What the splitter between the sessions and the centre pane says it is. */
internal const val ResizeSessionsPaneDescription = "Resize the sessions pane"

/** What the splitter between the centre pane and the right pane says it is. */
internal const val ResizeRightPaneDescription = "Resize the right pane"

/** What an arrow key moves a splitter by. */
private val KeyStep = 16.dp

/**
 * A vertical divider the developer drags: [onDrag] gets the horizontal movement, positive to the right. It takes
 * the keyboard as well: Tab reaches it, and the left and right arrows move it by [KeyStep], as a drag of that size.
 * [description] names it to accessibility.
 */
@Composable
internal fun Splitter(description: String, onDrag: (Dp) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val latest = rememberUpdatedState(onDrag)
    Box(
        modifier
            .width(InspectorDimens.dividerHitArea).fillMaxHeight()
            .pointerHoverIcon(ResizeCursor)
            .semantics { contentDescription = description }
            .onKeyEvent { event ->
                val step = when (event.key) {
                    Key.DirectionLeft -> -KeyStep
                    Key.DirectionRight -> KeyStep
                    else -> return@onKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) latest.value(step)
                true
            }
            .focusable()
            .draggable(rememberDraggableState { with(density) { latest.value(it.toDp()) } }, Orientation.Horizontal),
        contentAlignment = Alignment.Center,
    ) {
        VerticalRule()
        Box(Modifier.size(width = 5.dp, height = 28.dp).background(InspectorTheme.colors.strongOutline, RoundedCornerShape(3.dp)))
    }
}
