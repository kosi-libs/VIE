package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/**
 * What a callout says by its colours: [container] tints it, [accent] colours its glyph, and [title] a line
 * that names an outcome. The rest of what it says keeps the panes' text colours.
 */
internal enum class CalloutTone {
    /** A state to know, neither good nor bad: a block, a wait, a disconnection, an outcome the app left open. */
    Neutral,

    /** Why something is as it is: a kind that cannot be sent. */
    Information,

    /** What was asked was done. */
    Success,

    /** What was asked failed, or cannot be: a refused intervention, a rejected probe, a JSON that does not parse. */
    Error,

    /** What may not do what is meant: an intervention that timed out, a signal no view may collect. */
    Warning,

    /** What the inspector did, not the app. */
    Intervened;

    /** The tint behind the callout. */
    val container: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            Neutral, Information -> MaterialTheme.colorScheme.surfaceContainerHigh
            Success -> InspectorTheme.colors.successContainer
            Error -> InspectorTheme.colors.errorContainer
            Warning -> InspectorTheme.colors.timeoutContainer
            Intervened -> InspectorTheme.colors.intervenedContainer
        }

    /** The colour of the glyph, and of an action that belongs to the tone. */
    val accent: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.outline
            Information -> MaterialTheme.colorScheme.primary
            Success -> InspectorTheme.colors.success
            Error -> InspectorTheme.colors.error
            Warning -> InspectorTheme.colors.timeout
            Intervened -> InspectorTheme.colors.intervened
        }

    /** The colour of a line that names an outcome: the accent, but for the tones that name none. */
    val title: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            Neutral, Information -> MaterialTheme.colorScheme.onSurface
            Success, Error, Warning, Intervened -> accent
        }
}

/**
 * A tinted note in a rounded box: a [glyph] in the [tone]'s accent, what it says, and an [action] at its
 * end if it has one. [content] is a column of lines, [InspectorDimens.calloutLineGap] apart: the caller
 * writes them in the text roles it needs, and colours a line that names an outcome in [CalloutTone.title].
 */
@Composable
internal fun Callout(
    tone: CalloutTone,
    glyph: Glyph,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Callout(tone, leading = { CalloutGlyph(glyph, tone) }, modifier = modifier, action = action, content = content)
}

/** A [Callout] that leads with [leading] where its glyph would be: a progress indicator, while it waits. */
@Composable
internal fun Callout(
    tone: CalloutTone,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CalloutRow(
        modifier
            .background(tone.container, MaterialTheme.shapes.medium)
            .padding(horizontal = InspectorDimens.calloutHorizontalPadding, vertical = InspectorDimens.calloutVerticalPadding),
        leading, action, content,
    )
}

/**
 * The row of a callout: [leading] and the lines of [content] side by side at its top, and the [action] in
 * the middle of its height. The lines are at least as high as the glyph and centred in that height, so
 * that a single line stands level with it; when the action is the tallest, glyph and line stand level
 * with the action.
 */
@Composable
private fun CalloutRow(
    modifier: Modifier,
    leading: @Composable () -> Unit,
    action: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(InspectorDimens.calloutGap), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(InspectorDimens.calloutGap), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(InspectorDimens.calloutGlyph), contentAlignment = Alignment.Center) { leading() }
            Column(
                Modifier.weight(1f).heightIn(min = InspectorDimens.calloutGlyph),
                verticalArrangement = Arrangement.spacedBy(InspectorDimens.calloutLineGap, Alignment.CenterVertically),
                content = content,
            )
        }
        action?.invoke()
    }
}

/** A callout's glyph, in its tone's accent. */
@Composable
private fun CalloutGlyph(glyph: Glyph, tone: CalloutTone) {
    Symbol(glyph, size = InspectorDimens.calloutGlyph, tint = tone.accent)
}
