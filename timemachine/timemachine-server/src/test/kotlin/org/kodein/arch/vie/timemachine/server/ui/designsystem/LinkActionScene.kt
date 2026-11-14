package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/** The links the views write: in the primary colour, in a band's, and with a glyph after the label. */
private enum class LinkSpecimen(val label: String) { Primary("primary"), Tinted("intervened"), Trailing("with a glyph") }

/** [LinkAction] in each colour and with a glyph, in each state. */
internal val LinkActionScene: GalleryScene = galleryMatrix(
    name = "link-action",
    rows = LinkSpecimen.entries,
    columns = SpecimenState.entries,
    rowLabel = { it.label },
    columnLabel = { it.label },
    cellWidth = 160.dp,
) { row, state ->
    val interaction = rememberSpecimenInteraction(state)
    when (row) {
        LinkSpecimen.Primary -> LinkAction("Resend", onClick = {}, enabled = state.enabled, interactionSource = interaction)
        LinkSpecimen.Tinted -> LinkAction("Show", onClick = {}, enabled = state.enabled, color = InspectorTheme.colors.intervened, interactionSource = interaction)
        LinkSpecimen.Trailing -> LinkAction("Jump to #238", onClick = {}, enabled = state.enabled, glyph = Glyph.ArrowForward, interactionSource = interaction)
    }
}
