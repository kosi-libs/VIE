package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/**
 * A glyph that is a button: a click does [onClick], and it says it is [description]. Whatever the [size] of
 * its [glyph], it takes at least [InspectorDimens.iconAction] square, the glyph centred, and what shows the
 * pointer over it or pressing it fills that circle.
 *
 * @param tint The glyph's colour: the outline's for a chevron or a toggle, the default for an action.
 * @param interactionSource What it reports its hover and press to, and shows them from; see `action`.
 */
@Composable
internal fun IconAction(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    interactionSource: MutableInteractionSource? = null,
) {
    Box(
        modifier
            .sizeIn(minWidth = InspectorDimens.iconAction, minHeight = InspectorDimens.iconAction)
            .action(shape = InspectorPillShape, description = description, interactionSource = interactionSource, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(glyph, size = size, tint = tint)
    }
}
