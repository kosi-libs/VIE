package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorColors
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme

@Composable
internal fun KindBadge(kind: VIEObjectKind) {
    val colors = InspectorTheme.colors
    val kindStyle = kind.style()
    Text(
        kind.name,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.02.sp), color = kindStyle.color(colors), textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(InspectorDimens.kindBadgeWidth).height(InspectorDimens.kindBadgeHeight)
            .background(kindStyle.container(colors), RoundedCornerShape(4.dp)).wrapLine(),
    )
}

/** Centres a single line vertically in a fixed-height badge. */
private fun Modifier.wrapLine(): Modifier = this.padding(top = 1.dp)

/**
 * How the inspector shows a kind of VIE object: its colours, its words and its glyphs. [VIEObjectKind.style]
 * gives the one of a kind. This file is the only place that tells the kinds apart: a new kind, or a new
 * glyph for one, is a change here only.
 *
 * @property color The kind's colour: its badge, the dot of its type chip, the direction glyph of its rows.
 * @property container The tint behind the kind's badge and its lit filter chip.
 * @property type The type info of a ViewModel's objects of this kind; [VIEViewModelInfo.type] reads it.
 * @property verb The label of the button that sends an intervention of this kind: "Replace model".
 * @property participle What an intervention does to an object of this kind, as in "can't be replaced".
 * @property sendGlyph The glyph of the send button.
 * @property effect What sending does, written beside the send button.
 * @property unobservedName What the editor's note calls an object of this kind when none was observed:
 *   the kind itself for a Model, whose type is the ViewModel's one Model type; `null` for the others,
 *   which the note names by their type.
 * @property direction Whether an object of this kind goes into the ViewModel or comes out of it.
 */
internal enum class KindStyle(
    val color: (InspectorColors) -> Color,
    val container: (InspectorColors) -> Color,
    val type: (VIEViewModelInfo) -> VIETypeInfo,
    val verb: String,
    val participle: String,
    val sendGlyph: Glyph,
    val effect: String,
    private val unobservedName: String?,
    val direction: Direction,
) {
    Model(
        color = InspectorColors::model, container = InspectorColors::modelContainer, type = VIEViewModelInfo::model,
        verb = "Replace model", participle = "replaced", sendGlyph = Glyph.SwapHoriz,
        effect = "Replaces the ViewModel’s current state. The app’s next update computes from it.",
        unobservedName = "Model", direction = Direction.OutOf,
    ),
    Intent(
        color = InspectorColors::intent, container = InspectorColors::intentContainer, type = VIEViewModelInfo::intent,
        verb = "Post intent", participle = "posted", sendGlyph = Glyph.Login,
        effect = "Posted to the ViewModel as if the user did it.",
        unobservedName = null, direction = Direction.Into,
    ),
    Signal(
        color = InspectorColors::signal, container = InspectorColors::signalContainer, type = VIEViewModelInfo::signal,
        verb = "Emit signal", participle = "emitted", sendGlyph = Glyph.Campaign,
        effect = "Emitted to the view as if the ViewModel sent it.",
        unobservedName = null, direction = Direction.OutOf,
    );

    /** The editor's note when nothing of this kind of type [typeName] was observed, and it starts from `{}`. */
    fun unobservedNote(typeName: String): String = "no ${unobservedName ?: typeName} observed yet — starting from {}"

    /** Whether an object goes into the ViewModel or comes out of it: the glyph of its row, and the words of its detail. */
    enum class Direction(val glyph: Glyph, val mirrored: Boolean, val label: String) {
        Into(Glyph.Login, mirrored = false, label = "into the ViewModel"),
        OutOf(Glyph.Logout, mirrored = true, label = "out of the ViewModel"),
    }
}

/** How the inspector shows this kind. */
internal fun VIEObjectKind.style(): KindStyle = when (this) {
    VIEObjectKind.Model -> KindStyle.Model
    VIEObjectKind.Intent -> KindStyle.Intent
    VIEObjectKind.Signal -> KindStyle.Signal
}

/** The type info of a ViewModel's objects of one [kind]. */
fun VIEViewModelInfo.type(kind: VIEObjectKind): VIETypeInfo = kind.style().type(this)
