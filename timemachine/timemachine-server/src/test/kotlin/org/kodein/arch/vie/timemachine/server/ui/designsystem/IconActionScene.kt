package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/** A glyph button as the views write it: its glyph, its size, and whether it is tinted as a chevron. */
private class IconSpecimen(val label: String, val glyph: Glyph, val size: Dp, val outline: Boolean)

/** [IconAction] at the glyph sizes the views give it: the target stays the same. */
internal val IconActionScene: GalleryScene = galleryMatrix(
    name = "icon-action",
    rows = listOf(
        IconSpecimen("15 · outline", Glyph.LightMode, 15.dp, outline = true),
        IconSpecimen("16 · outline", Glyph.ChevronRight, 16.dp, outline = true),
        IconSpecimen("17", Glyph.EditNote, 17.dp, outline = false),
        IconSpecimen("18", Glyph.RightPanelClose, 18.dp, outline = false),
    ),
    columns = listOf(SpecimenState.Enabled, SpecimenState.Hovered, SpecimenState.Pressed),
    rowLabel = { it.label },
    columnLabel = { it.label },
) { specimen, state ->
    IconAction(
        specimen.glyph, specimen.label, onClick = {}, size = specimen.size,
        tint = if (specimen.outline) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
        interactionSource = rememberSpecimenInteraction(state),
    )
}
