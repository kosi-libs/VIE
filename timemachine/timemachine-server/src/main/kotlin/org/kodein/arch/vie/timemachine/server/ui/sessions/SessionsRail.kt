package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.designsystem.VerticalRule
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
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
    val items = remember(model) { railItems(model) }
    Row(modifier.background(scheme.surfaceContainerLow)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(width = 40.dp, height = 36.dp)
                    .action(shape = InspectorPillShape, description = RailMenuDescription, onClick = onMenu),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(Glyph.Menu, size = 20.dp, tint = scheme.onSurfaceVariant)
            }
            for (item in items) {
                RailTile(item) { post(SessionsVIE.Intent.SelectScope(item.scope)) }
            }
        }
        VerticalRule()
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
            .action(shape = shape, description = item.description, selected = item.selected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            item.label, maxLines = 1,
            style = InspectorTheme.text.codeRow, color = if (item.selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
        )
        when (item.dot) {
            RailDot.Filled -> Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(7.dp).background(InspectorTheme.colors.success, CircleShape))
            RailDot.Ring -> Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(7.dp).border(1.5.dp, scheme.outline, CircleShape))
            RailDot.None -> Unit
        }
    }
}
