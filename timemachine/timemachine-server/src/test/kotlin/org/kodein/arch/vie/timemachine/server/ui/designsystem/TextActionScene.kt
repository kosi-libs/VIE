package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.common.TextAction
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/** A [TextAction] as a view writes it: its text, its glyph if any, its height, and whether it is tinted as an error. */
private class TextActionSpecimen(val label: String, val text: String, val glyph: Glyph?, val height: Dp, val error: Boolean = false)

/** [TextAction] in the three heights the views give it, with and without a glyph, and in the error tint. */
internal val TextActionScene: GalleryScene = galleryMatrix(
    name = "text-action",
    rows = listOf(
        TextActionSpecimen("26 · text", "Clear filters", glyph = null, height = 26.dp),
        TextActionSpecimen("26 · glyph", "Clear disconnected", glyph = Glyph.DeleteSweep, height = 26.dp),
        TextActionSpecimen("24 · glyph", "Clear session", glyph = Glyph.ClearAll, height = 24.dp),
        TextActionSpecimen("28 · error", "Dismiss", glyph = null, height = 28.dp, error = true),
    ),
    columns = SpecimenState.entries,
    rowLabel = { it.label },
    columnLabel = { it.label },
    cellWidth = 180.dp,
) { specimen, state ->
    TextAction(
        specimen.text,
        glyph = specimen.glyph,
        color = if (specimen.error) InspectorTheme.colors.error else MaterialTheme.colorScheme.primary,
        enabled = state.enabled,
        height = specimen.height,
    ) {}
}
