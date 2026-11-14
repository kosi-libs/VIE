package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/**
 * What a pane, a list or a section shows when it has nothing to: a [glyph] if any, a [title], a
 * [description] if any and an [action] if any, centred one under the other, within the empty state's
 * margins. It stacks at the top of what it is given: a caller that wants it in the middle of its pane says so.
 *
 * @param description What to do about it, or why: "Click a row, or use ↑ ↓ to step through the timeline."
 * @param action What undoes the emptiness, when something can: the filters' "Clear filters".
 */
@Composable
internal fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.padding(horizontal = InspectorDimens.emptyStateHorizontalPadding, vertical = InspectorDimens.emptyStateVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(InspectorDimens.emptyStateGap),
    ) {
        glyph?.let { Symbol(it, size = InspectorDimens.emptyStateGlyph, tint = scheme.outline) }
        Text(title, style = InspectorTheme.text.emptyTitle, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        description?.let { Text(it, style = InspectorTheme.text.body, color = scheme.outline, textAlign = TextAlign.Center) }
        action?.invoke()
    }
}
