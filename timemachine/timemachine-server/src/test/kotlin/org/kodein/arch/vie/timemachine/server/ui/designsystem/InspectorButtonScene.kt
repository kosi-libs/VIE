package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/** A button as the views write it: its variant, its size, and whether it leads with a glyph. */
private class ButtonSpecimen(val variant: ButtonVariant, val size: ButtonSize, val glyph: Glyph?)

/** [InspectorButton] in every variant and size, with a glyph, then each variant without one, in each state. */
internal val InspectorButtonScene: GalleryScene = galleryMatrix(
    name = "inspector-button",
    rows = ButtonVariant.entries.flatMap { variant -> ButtonSize.entries.map { size -> ButtonSpecimen(variant, size, Glyph.EditNote) } } +
        ButtonVariant.entries.map { ButtonSpecimen(it, ButtonSize.Medium, glyph = null) },
    columns = SpecimenState.entries,
    rowLabel = { "${it.variant.name.lowercase()} · ${it.size.name.lowercase()}${if (it.glyph == null) " · no glyph" else ""}" },
    columnLabel = { it.label },
    cellWidth = 200.dp,
) { specimen, state ->
    InspectorButton(
        "Edit & intervene", onClick = {}, glyph = specimen.glyph, variant = specimen.variant, size = specimen.size,
        enabled = state.enabled, interactionSource = rememberSpecimenInteraction(state),
    )
}
