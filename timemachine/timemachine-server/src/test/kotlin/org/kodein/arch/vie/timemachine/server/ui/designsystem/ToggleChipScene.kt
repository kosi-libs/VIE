package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.ui.common.style
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/** The filters a toggle chip is: the kinds', and "Intervened only" with its glyph. */
private enum class ToggleTone(val label: String) { Model("Model"), Intent("Intent"), Signal("Signal"), Intervened("Intervened only") }

/** Off and on, at rest and under the pointer. */
private enum class ToggleState(val label: String, val selected: Boolean, val state: SpecimenState) {
    Off("off", false, SpecimenState.Enabled), On("on", true, SpecimenState.Enabled),
    OffHovered("off · hovered", false, SpecimenState.Hovered), OnPressed("on · pressed", true, SpecimenState.Pressed),
}

/** [ToggleChip] in each filter's tone, off and on, at rest and under the pointer. */
internal val ToggleChipScene: GalleryScene = galleryMatrix(
    name = "toggle-chip",
    rows = ToggleTone.entries,
    columns = ToggleState.entries,
    rowLabel = { it.label },
    columnLabel = { it.label },
    cellWidth = 190.dp,
) { row, column ->
    val tone = when (row) {
        ToggleTone.Model -> ChipTone.Kind(VIEObjectKind.Model.style())
        ToggleTone.Intent -> ChipTone.Kind(VIEObjectKind.Intent.style())
        ToggleTone.Signal -> ChipTone.Kind(VIEObjectKind.Signal.style())
        ToggleTone.Intervened -> ChipTone.Intervened
    }
    ToggleChip(
        row.label, selected = column.selected, onClick = {}, tone = tone, count = 12,
        glyph = if (row == ToggleTone.Intervened) Glyph.TouchApp else null,
        interactionSource = rememberSpecimenInteraction(column.state),
    )
}
