package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTextStyles
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/** The height of a segmented control, with its segments' margin and text, and whether its options have glyphs. */
internal enum class SegmentedControlSize(
    val height: Dp,
    val padding: Dp,
    val glyphs: Boolean,
    val text: (InspectorTextStyles) -> TextStyle,
) {
    /** A choice that heads a form, the composer's kinds: its choice leads with a check, an unavailable option with the block glyph. */
    Regular(InspectorDimens.segmentedControl, padding = 12.dp, glyphs = true, text = InspectorTextStyles::label),

    /** A switch in a header, Tree | Raw: words only, for a header has no room for glyphs. */
    Small(InspectorDimens.segmentedControlSmall, padding = 10.dp, glyphs = false, text = InspectorTextStyles::actionSmall),
}

/**
 * One choice among [options], [selected] the chosen one, each a segment of the same width written
 * [label]; a click on a segment asks [onSelect] for its option. A radio group to accessibility.
 *
 * @param enabled Whether a click can change the choice at all; disabled, the options are drawn as they are.
 * @param optionEnabled Whether an option can be chosen. One that cannot is dimmed, never shown chosen, and
 *   takes no click.
 */
@Composable
internal fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionEnabled: (T) -> Boolean = { true },
    size: SegmentedControlSize = SegmentedControlSize.Regular,
) {
    val scheme = MaterialTheme.colorScheme
    val outline = InspectorTheme.colors.strongOutline
    val style = size.text(InspectorTheme.text)
    Row(
        modifier
            .width(IntrinsicSize.Max)
            .height(size.height)
            .clip(InspectorPillShape)
            .border(InspectorDimens.outline, outline, InspectorPillShape)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) VerticalRule(color = outline)
            val available = optionEnabled(option)
            val on = available && option == selected
            val foreground = when {
                on -> scheme.onPrimaryContainer
                available -> scheme.onSurface
                else -> scheme.outline
            }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) scheme.primaryContainer else Color.Transparent)
                    .then(if (available) Modifier else Modifier.alpha(UnavailableAlpha))
                    .action(enabled = enabled && available, role = Role.RadioButton, selected = on) { onSelect(option) }
                    .padding(horizontal = size.padding),
                horizontalArrangement = Arrangement.spacedBy(SegmentGlyphGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (size.glyphs) when {
                    on -> Symbol(Glyph.Check, size = SegmentGlyph, tint = foreground)
                    !available -> Symbol(Glyph.Block, size = SegmentGlyph, tint = foreground)
                }
                Text(label(option), style = style, color = foreground, maxLines = 1)
            }
        }
    }
}

/** How an option that cannot be chosen is dimmed (the opacities' tokens are not decided yet). */
private const val UnavailableAlpha = 0.55f
private val SegmentGlyph = 15.dp
private val SegmentGlyphGap = 4.dp
