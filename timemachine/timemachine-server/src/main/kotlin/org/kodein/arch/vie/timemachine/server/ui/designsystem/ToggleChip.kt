package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/**
 * A filter that is on or off: [label] and its [count], a click asks [onClick] to turn it the other way.
 * On, it is filled in [tone] and leads with a check; off, it is outlined, and leads with [glyph] if any,
 * in the tone's colour. A checkbox to accessibility, which says whether it is on.
 *
 * @param interactionSource What it reports its hover and press to, and shows them from; see `action`.
 */
@Composable
internal fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tone: ChipTone,
    modifier: Modifier = Modifier,
    count: Int? = null,
    glyph: Glyph? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val shape = MaterialTheme.shapes.small
    val accent = tone.contentColor()
    val text = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val leading = if (selected) Glyph.Check else glyph
    Row(
        modifier
            .height(InspectorDimens.toggleChip)
            .then(
                if (selected) Modifier.background(tone.containerColor(), shape)
                else Modifier.border(InspectorDimens.outline, InspectorTheme.colors.strongOutline, shape),
            )
            .action(shape = shape, role = Role.Checkbox, selected = selected, interactionSource = interactionSource, onClick = onClick)
            .padding(start = if (leading != null) ToggleStartWithGlyph else ToggleEnd, end = ToggleEnd),
        horizontalArrangement = Arrangement.spacedBy(ToggleGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { Symbol(it, size = ToggleGlyph, tint = accent) }
        Text(label, style = InspectorTheme.text.label, color = text, maxLines = 1)
        count?.let { Text(it.toString(), style = InspectorTheme.text.monoMeta, color = text, modifier = Modifier.alpha(CountAlpha)) }
    }
}

private val ToggleStartWithGlyph = 6.dp
private val ToggleEnd = 9.dp
private val ToggleGap = 4.dp
private val ToggleGlyph = 15.dp

/** How the count stands back from the label (the opacities' tokens are not decided yet). */
private const val CountAlpha = 0.8f
