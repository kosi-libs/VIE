package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens


/**
 * [content], which scrolls, under the scrollbar of [adapter]: the bar lies over the content's right
 * edge and takes no room, so nothing moves when it comes or goes.
 */
@Composable
internal fun ScrollbarBox(adapter: ScrollbarAdapter, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        content()
        EdgeScrollbar(adapter)
    }
}

/**
 * The scrollbar of what [adapter] scrolls, over the right edge of the box it shares with it: for a box
 * that holds more than what scrolls, else see [ScrollbarBox]. It shows for as long as the content
 * overflows, in the theme's style: a thumb in a track a little wider, stronger under the pointer.
 */
@Composable
internal fun BoxScope.EdgeScrollbar(adapter: ScrollbarAdapter, modifier: Modifier = Modifier) {
    val inset = (InspectorDimens.scrollbarTrack - InspectorDimens.scrollbarThumb) / 2
    VerticalScrollbar(
        adapter,
        modifier.align(Alignment.CenterEnd).fillMaxHeight().width(InspectorDimens.scrollbarTrack).padding(horizontal = inset, vertical = 2.dp),
    )
}
