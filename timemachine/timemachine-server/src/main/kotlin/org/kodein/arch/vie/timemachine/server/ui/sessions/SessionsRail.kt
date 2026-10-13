package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.common.monoStyle
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/** What the menu button says it is: it opens the drawer of the sessions. */
internal const val RailMenuDescription = "Sessions"

/**
 * The compact layout's left pane: a button that opens the drawer, a tile for each session and, under
 * the selected one, a tile for each of its ViewModels. [post] is what a click on a tile sends.
 */
@Composable
internal fun SessionsRail(model: SessionsVIE.Model, modifier: Modifier = Modifier, onMenu: () -> Unit, post: (SessionsVIE.Intent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.background(scheme.surfaceContainerLow)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(width = 40.dp, height = 36.dp).clip(RoundedCornerShape(18.dp))
                    .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = onMenu)
                    .semantics { contentDescription = RailMenuDescription },
                contentAlignment = Alignment.Center,
            ) {
                Symbol("menu", size = 20.dp, tint = scheme.onSurfaceVariant)
            }
            for (item in railItems(model)) {
                RailTile(item) { post(SessionsVIE.Intent.SelectScope(item.scope)) }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(scheme.outlineVariant))
    }
}

@Composable
private fun RailTile(item: RailItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(width = 40.dp, height = 36.dp)
            .alpha(if (item.dimmed) 0.55f else 1f)
            .background(if (item.selected) scheme.primaryContainer else scheme.surfaceContainerHigh, shape)
            .clip(shape)
            .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = item.description; selected = item.selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            item.label, maxLines = 1,
            style = monoStyle(11.5f, if (item.selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant, bold = true),
        )
        when (item.dot) {
            RailDot.Filled -> Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(7.dp).background(InspectorTheme.colors.success, CircleShape))
            RailDot.Ring -> Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(7.dp).border(1.5.dp, scheme.outline, CircleShape))
            RailDot.None -> Unit
        }
    }
}
