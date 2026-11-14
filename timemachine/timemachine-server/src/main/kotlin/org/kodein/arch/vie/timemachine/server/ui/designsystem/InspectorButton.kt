package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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


/** How much a button stands out, from the main action of its place to an action that is only its words. */
internal enum class ButtonVariant {
    /** The main action of its place, in the primary colour: Send, Retry, a row's "Edit & intervene". */
    Filled,

    /** An action that stands out less, on the primary container: "Edit & replace", "Following", "3 new". */
    Tonal,

    /** A secondary action, outlined: "Copy JSON", "Paused". */
    Outlined,

    /** An action that is its glyph and words only: "Clear disconnected", "Clear filters". */
    Text,
}

/**
 * The height of a button, with the size of its glyph, its margins and its text.
 *
 * @property start The margin before the glyph; with no glyph, the label starts after [end].
 * @property end The margin after the label.
 */
internal enum class ButtonSize(
    val height: Dp,
    val glyph: Dp,
    val start: Dp,
    val end: Dp,
    val gap: Dp,
    val text: (InspectorTextStyles) -> TextStyle,
) {
    /** In a row of the timeline. */
    Small(InspectorDimens.buttonSmall, glyph = 14.dp, start = 6.dp, end = 8.dp, gap = 4.dp, text = InspectorTextStyles::actionSmall),

    /** In a header, a bar or a list. */
    Medium(InspectorDimens.buttonMedium, glyph = 15.dp, start = 8.dp, end = 10.dp, gap = 4.dp, text = InspectorTextStyles::action),

    /** The main action of a form or a screen. */
    Large(InspectorDimens.buttonLarge, glyph = 18.dp, start = 14.dp, end = 18.dp, gap = 6.dp, text = InspectorTextStyles::actionLarge),
}

/**
 * A pill button: [label], after [glyph] if any, does [onClick]. Under the pointer, pressed or focused, a filled
 * button darkens its fill with a ripple of its own, and the others take the theme's content colour (see
 * `InspectorTheme`). Disabled, whatever its [variant], its glyph and label are in the outline's colour, on the highest container
 * for a filled or tonal one, in an outline variant for an outlined one: it keeps its place and its shape, and
 * reads as out of reach.
 *
 * @param interactionSource What it reports its hover and press to, and shows them from; see `action`.
 */
@Composable
internal fun InspectorButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    variant: ButtonVariant = ButtonVariant.Filled,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val content = if (enabled) variant.content(scheme) else scheme.outline
    // One disabled fill for every button that has one: the closest of the theme's surfaces to Material's.
    val disabledFill = scheme.surfaceContainerHighest
    val decoration = when (variant) {
        ButtonVariant.Filled -> Modifier.background(if (enabled) scheme.primary else disabledFill, InspectorPillShape)
        ButtonVariant.Tonal -> Modifier.background(if (enabled) scheme.primaryContainer else disabledFill, InspectorPillShape)
        ButtonVariant.Outlined -> Modifier.border(
            InspectorDimens.outline, if (enabled) InspectorTheme.colors.strongOutline else scheme.outlineVariant, InspectorPillShape,
        )
        ButtonVariant.Text -> Modifier
    }
    // Provided whatever the variant, so that a button whose variant changes keeps its node, and what it shows.
    val ripple = if (variant == ButtonVariant.Filled) FilledRipple else LocalRippleConfiguration.current
    CompositionLocalProvider(LocalRippleConfiguration provides ripple) {
        Row(
            modifier
                .height(size.height)
                .then(decoration)
                .action(shape = InspectorPillShape, enabled = enabled, interactionSource = interactionSource, onClick = onClick)
                .padding(start = if (glyph != null) size.start else size.end, end = size.end),
            horizontalArrangement = Arrangement.spacedBy(size.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            glyph?.let { Symbol(it, size = size.glyph, tint = content) }
            Text(label, style = size.text(InspectorTheme.text), color = content, maxLines = 1)
        }
    }
}

/** What an enabled button of this variant draws its glyph and label in. */
private fun ButtonVariant.content(scheme: ColorScheme): Color = when (this) {
    ButtonVariant.Filled -> scheme.onPrimary
    ButtonVariant.Tonal -> scheme.onPrimaryContainer
    ButtonVariant.Outlined -> scheme.onSurfaceVariant
    ButtonVariant.Text -> scheme.primary
}

/**
 * What shows the pointer over a filled button, pressing it or the focus on it (see `InspectorTheme`): black, as Material
 * drew it everywhere before the theme provided a content colour. It darkens the primary fill by 8 to 22 levels of 255
 * under the pointer. The dark theme's content colour, nearly as light as that fill, would lighten it by only 1 to 10
 * levels, and by 1 to 3 on two of its three channels.
 */
private val FilledRipple = RippleConfiguration(color = Color.Black)
