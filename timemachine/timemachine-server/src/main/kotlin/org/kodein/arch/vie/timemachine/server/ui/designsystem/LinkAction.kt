package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/**
 * A text that is a link: [label], in [color], does [onClick]. It is at least [InspectorDimens.linkAction]
 * high whatever its line, and has no side margin, so that it lines up with the text around it. Disabled, it
 * is drawn in the outline's colour.
 *
 * @param color The primary colour, or the tint of the band it is written on: "Show" on an intervention's.
 * @param glyph What follows the label, as the arrow of "Jump to #238".
 * @param interactionSource What it reports its hover and press to, and shows them from; see `action`.
 */
@Composable
internal fun LinkAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    glyph: Glyph? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val tint = if (enabled) color else MaterialTheme.colorScheme.outline
    Row(
        modifier
            .heightIn(min = InspectorDimens.linkAction)
            .action(shape = MaterialTheme.shapes.extraSmall, enabled = enabled, interactionSource = interactionSource, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(LinkGlyphGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = InspectorTheme.text.label, color = tint, maxLines = 1)
        glyph?.let { Symbol(it, size = LinkGlyph, tint = tint) }
    }
}

private val LinkGlyph = 15.dp
private val LinkGlyphGap = 4.dp
