package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.time.Instant


/** [InspectorTheme.fonts]' mono at [size], in [color]. */
@Composable
internal fun monoStyle(size: Float, color: Color, bold: Boolean = false): TextStyle =
    TextStyle(
        fontFamily = InspectorTheme.fonts.mono,
        fontSize = size.sp,
        color = color,
        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
    )

/** `HH:mm:ss`: the design prints seconds in the shell, milliseconds only in the timeline. */
internal fun clock(at: Instant): String = formatTime(at).take(8)

internal fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/**
 * What a click does here: [onClick], with the hand over it, as the [role] it has. Disabled, it keeps
 * its place in the semantics, as an action that cannot be taken, rather than becoming plain content.
 *
 * @param description What a glyph with no text says it is.
 * @param selected Whether it is the one chosen among its siblings; `null` when it is not a choice.
 */
internal fun Modifier.action(
    enabled: Boolean = true,
    role: Role = Role.Button,
    description: String? = null,
    selected: Boolean? = null,
    onClick: () -> Unit,
): Modifier = this
    .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
    .clickable(enabled = enabled, role = role, onClick = onClick)
    .then(
        if (description == null && selected == null) Modifier
        else Modifier.semantics {
            description?.let { contentDescription = it }
            selected?.let { this.selected = it }
        },
    )

/** The presence dot: filled with a 3 dp halo when [live], a hollow ring otherwise. */
@Composable
internal fun LiveDot(live: Boolean, modifier: Modifier = Modifier) {
    val dot = if (live) InspectorTheme.colors.success else Color.Transparent
    val halo = InspectorTheme.colors.successContainer
    val ring = MaterialTheme.colorScheme.outline
    Box(
        modifier
            .size(8.dp)
            .drawBehind { if (live) drawCircle(halo, radius = size.minDimension / 2 + 3.dp.toPx()) }
            .background(dot, CircleShape)
            .then(if (live) Modifier else Modifier.border(1.5.dp, ring, CircleShape)),
    )
}

/** A pill action with an optional leading glyph: "Clear disconnected", "Retry", "Dismiss". */
@Composable
internal fun TextAction(
    label: String,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    height: Int = 26,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(height.dp)
            .background(Color.Transparent, InspectorPillShape)
            .action(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph?.let { Symbol(it, size = 15.dp, tint = color) }
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp), color = color, maxLines = 1)
    }
}

/** A thin full-width rule. */
@Composable
internal fun HorizontalRule(color: Color = MaterialTheme.colorScheme.outlineVariant, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}
