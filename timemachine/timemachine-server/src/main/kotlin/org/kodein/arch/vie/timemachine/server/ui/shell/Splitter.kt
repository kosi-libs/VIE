package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import java.awt.Cursor


/** A vertical divider the developer drags: [onDrag] gets the horizontal movement, positive to the right. */
@Composable
internal fun Splitter(onDrag: (Dp) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val latest = rememberUpdatedState(onDrag)
    Box(
        modifier
            .width(InspectorDimens.dividerHitArea).fillMaxHeight()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(rememberDraggableState { with(density) { latest.value(it.toDp()) } }, Orientation.Horizontal),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Box(Modifier.size(width = 5.dp, height = 28.dp).background(InspectorTheme.colors.strongOutline, RoundedCornerShape(3.dp)))
    }
}
