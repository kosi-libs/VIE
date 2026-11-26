package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.common.KindStyle
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorChipShape
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTextStyles
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/** What a chip says by its colours: nothing in particular, a success, an error, an intervention, the primary choice, or a kind. */
@Immutable
internal sealed interface ChipTone {
    data object Neutral : ChipTone
    data object Success : ChipTone
    data object Error : ChipTone
    data object Intervened : ChipTone
    data object Primary : ChipTone

    /** The colours of a kind of VIE object: its colour on its container. */
    data class Kind(val style: KindStyle) : ChipTone
}

/** The colour of a chip's text and glyphs in this tone. */
@Composable
@ReadOnlyComposable
internal fun ChipTone.contentColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    return when (this) {
        ChipTone.Neutral -> scheme.onSurfaceVariant
        ChipTone.Success -> colors.success
        ChipTone.Error -> colors.error
        ChipTone.Intervened -> colors.intervened
        ChipTone.Primary -> scheme.onPrimaryContainer
        is ChipTone.Kind -> style.color(colors)
    }
}

/** The fill of a chip in this tone. */
@Composable
@ReadOnlyComposable
internal fun ChipTone.containerColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    return when (this) {
        ChipTone.Neutral -> scheme.surfaceContainerHigh
        ChipTone.Success -> colors.successContainer
        ChipTone.Error -> colors.errorContainer
        ChipTone.Intervened -> colors.intervenedContainer
        ChipTone.Primary -> scheme.primaryContainer
        is ChipTone.Kind -> style.container(colors)
    }
}

/** The height of a chip, with its margin, the gap between its parts, and its text in Plex and in mono. */
internal enum class ChipSize(
    val height: Dp,
    val padding: Dp,
    val gap: Dp,
    val text: (InspectorTextStyles) -> TextStyle,
    val mono: (InspectorTextStyles) -> TextStyle,
) {
    /** A badge beside a name or in a row: an id, a status, a kind, "initial". */
    Small(InspectorDimens.chipSmall, padding = 6.dp, gap = 4.dp, text = InspectorTextStyles::badge, mono = InspectorTextStyles::monoSecondary),

    /** A chip that stands on its own: a type of the timeline's header, an address to copy. */
    Medium(InspectorDimens.chipMedium, padding = 8.dp, gap = 5.dp, text = InspectorTextStyles::action, mono = InspectorTextStyles::code),
}

/**
 * A chip: [text] in the colours of [tone], on its tint or, [outlined], in an outline, centred in it
 * whatever its width. Given [onClick], it is a button.
 *
 * @param leading What comes before the text, given the tone's colour: a dot, a glyph.
 * @param mono Whether the text is data, set in JetBrains Mono: an id, a source, a command.
 * @param trailing What comes after the text: a type chip's glyph and count.
 * @param interactionSource What it reports its hover and press to, when it is a button; see `action`.
 */
@Composable
internal fun Chip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    leading: (@Composable (tint: Color) -> Unit)? = null,
    mono: Boolean = false,
    size: ChipSize = ChipSize.Small,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val content = tone.contentColor()
    val styles = InspectorTheme.text
    Row(
        modifier
            .height(size.height)
            .then(
                if (outlined) Modifier.border(InspectorDimens.outline, InspectorTheme.colors.strongOutline, InspectorChipShape)
                else Modifier.background(tone.containerColor(), InspectorChipShape),
            )
            .then(if (onClick == null) Modifier else Modifier.action(shape = InspectorChipShape, interactionSource = interactionSource, onClick = onClick))
            .padding(horizontal = size.padding),
        horizontalArrangement = Arrangement.spacedBy(size.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(content)
        Text(
            text, style = if (mono) size.mono(styles) else size.text(styles), color = content,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        trailing?.invoke()
    }
}

/** The dot a chip leads with: a status's, a kind's; [shape] a circle, or [ChipSquareDot] for what is over. */
@Composable
internal fun ChipDot(color: Color, shape: Shape = CircleShape) {
    Box(Modifier.size(ChipDotSize).background(color, shape))
}

/** The dot of a closed ViewModel's status. */
internal val ChipSquareDot: Shape = RoundedCornerShape(1.dp)

private val ChipDotSize = 6.dp
