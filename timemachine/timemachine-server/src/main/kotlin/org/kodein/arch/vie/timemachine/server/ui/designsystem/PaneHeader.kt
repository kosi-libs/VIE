package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/**
 * The header of a pane, across it and [InspectorDimens.paneHeader] high: a [glyph] if any, the pane's
 * [title], what it counts or where it stands if anything, then what can be done to the whole pane, at its end.
 *
 * @param count What the title counts, or the position the pane shows: `3` sessions, `#208 · 12:00:01.123`.
 *   It gives way first when the header runs out of room.
 * @param glyphTint The colour of [glyph]: what the pane is about, as the Model's colour for the pinned model.
 * @param actions The pane's actions, left to right, laid out in the header's row.
 */
@Composable
internal fun PaneHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    glyph: Glyph? = null,
    glyphTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actions: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(InspectorDimens.paneHeader)
            .padding(start = InspectorDimens.paneHorizontalPadding, end = InspectorDimens.paneHeaderEndPadding),
        horizontalArrangement = Arrangement.spacedBy(InspectorDimens.paneHeaderGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph?.let { Symbol(it, size = InspectorDimens.paneHeaderGlyph, tint = glyphTint) }
        Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1)
        count?.let {
            // It and the spacer share the free room by half: it fills its half, so that whatever it leaves empty
            // is not left at the end of the row, which would pull the actions off the header's end.
            Text(
                it, style = InspectorTheme.text.monoSecondary, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}
