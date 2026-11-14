package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import java.awt.datatransfer.StringSelection
import kotlin.time.Instant


/** `HH:mm:ss`: the design prints seconds in the shell, milliseconds only in the timeline. */
internal fun clock(at: Instant): String = formatTime(at).take(8)

/**
 * What copies a text to the clipboard: the window's own, which Compose provides, so that a view that
 * copies can be composed where there is no desktop, and given another clipboard.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text: String -> scope.launch { clipboard.setClipEntry(ClipEntry(StringSelection(text))) } }
    }
}

/**
 * What a click does here: [onClick], with the hand over it, as the [role] it has. Disabled, it keeps
 * its place in the semantics, as an action that cannot be taken, rather than becoming plain content.
 *
 * The ripple that shows hover, press and keyboard focus is drawn in the theme's content colour (see
 * `InspectorTheme`); a filled button provides its own `RippleConfiguration` (see `InspectorButton`).
 *
 * @param shape The element's shape. What shows the pointer over the element or pressing it is clipped to
 *   it, and so are the element's content and where a click lands. The default rectangle needs no clip:
 *   the indication keeps to the element's bounds. A component of the design system always passes its own.
 * @param description What a glyph with no text says it is.
 * @param selected Whether it is the one chosen among its siblings; `null` when it is not a choice.
 * @param interactionSource What the element reports its hover and press to, and shows them from; `null`
 *   for one of its own. A test or the gallery gives one, to show a state with no pointer.
 */
internal fun Modifier.action(
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    role: Role = Role.Button,
    description: String? = null,
    selected: Boolean? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = this
    .then(if (shape == RectangleShape) Modifier else Modifier.clip(shape))
    .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
    .clickable(enabled = enabled, role = role, interactionSource = interactionSource, onClick = onClick)
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
