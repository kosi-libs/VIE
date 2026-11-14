package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.ui.common.style
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/** The tones a chip comes in, a kind's among them. */
private enum class ToneSpecimen(val label: String) {
    Neutral("neutral"), Success("success"), Error("error"), Intervened("intervened"), Primary("primary"),
    Model("Model"), Intent("Intent"), Signal("Signal"),
}

private fun ToneSpecimen.tone(): ChipTone = when (this) {
    ToneSpecimen.Neutral -> ChipTone.Neutral
    ToneSpecimen.Success -> ChipTone.Success
    ToneSpecimen.Error -> ChipTone.Error
    ToneSpecimen.Intervened -> ChipTone.Intervened
    ToneSpecimen.Primary -> ChipTone.Primary
    ToneSpecimen.Model -> ChipTone.Kind(VIEObjectKind.Model.style())
    ToneSpecimen.Intent -> ChipTone.Kind(VIEObjectKind.Intent.style())
    ToneSpecimen.Signal -> ChipTone.Kind(VIEObjectKind.Signal.style())
}

/** What each column draws: a plain badge, outlined, mono, after a dot, after a glyph, as wide as a kind's badge, and medium. */
private enum class ChipSpecimen(val label: String) { Plain("small"), Outlined("outlined"), Mono("mono"), Dot("dot"), Leading("glyph"), Wide("52 dp"), Medium("medium") }

/** [Chip] in every tone and every form. */
internal val ChipScene: GalleryScene = galleryMatrix(
    name = "chip",
    rows = ToneSpecimen.entries,
    columns = ChipSpecimen.entries,
    rowLabel = { it.label },
    columnLabel = { it.label },
    cellWidth = 150.dp,
) { row, column ->
    val tone = row.tone()
    when (column) {
        ChipSpecimen.Plain -> Chip("alive", tone)
        ChipSpecimen.Outlined -> Chip("toString only", tone, outlined = true)
        ChipSpecimen.Mono -> Chip("3f2a…c91e", tone, mono = true)
        ChipSpecimen.Dot -> Chip("closed 14:02:31", tone, leading = { ChipDot(it) })
        ChipSpecimen.Leading -> Chip("intervened #3", tone, leading = { Symbol(Glyph.TouchApp, size = 13.dp, tint = it) })
        ChipSpecimen.Wide -> Chip(row.label, tone, Modifier.width(InspectorDimens.kindBadgeWidth))
        ChipSpecimen.Medium -> Chip("lsof -i :13229", tone, mono = true, size = ChipSize.Medium)
    }
}

/** The chips that are buttons. */
private enum class ClickableChip(val label: String) { TypeChip("type chip"), Source("source") }

/** The clickable chips, at rest, hovered and pressed: the hover keeps to the chip's corners. */
internal val ChipClickableScene: GalleryScene = galleryMatrix(
    name = "chip-clickable",
    rows = ClickableChip.entries,
    columns = listOf(SpecimenState.Enabled, SpecimenState.Hovered, SpecimenState.Pressed),
    rowLabel = { it.label },
    columnLabel = { it.label },
    cellWidth = 180.dp,
) { row, state ->
    val interaction = rememberSpecimenInteraction(state)
    when (row) {
        ClickableChip.TypeChip -> Chip(
            "Model", ChipTone.Neutral, outlined = true, size = ChipSize.Medium,
            leading = { ChipDot(InspectorTheme.colors.model) },
            trailing = {
                Symbol(Glyph.DataObject, size = 14.dp, tint = InspectorTheme.colors.success)
                Text("3", style = InspectorTheme.text.monoMeta, color = MaterialTheme.colorScheme.outline)
            },
            onClick = {}, interactionSource = interaction,
        )
        ClickableChip.Source -> Chip("TaskListVM 3f2a", ChipTone.Neutral, mono = true, onClick = {}, interactionSource = interaction)
    }
}
