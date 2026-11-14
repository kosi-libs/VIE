package org.kodein.arch.vie.timemachine.server.ui.designsystem

import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/**
 * [CopyAction] at rest and hovered, and the check it is for a moment once it has copied. The check is only
 * reached by a click, and the gallery has no clipboard: its row draws it as `CopyAction` does.
 */
internal val CopyActionScene: GalleryScene = GalleryScene(
    name = "copy-action",
    rows = listOf(
        GalleryRow("at rest") { CopyAction(text = { "" }, description = "Copy") },
        GalleryRow("hovered") { CopyAction(text = { "" }, description = "Copy", interactionSource = rememberSpecimenInteraction(SpecimenState.Hovered)) },
        GalleryRow("copied, for 1.5 s") { IconAction(Glyph.Check, "Copied", onClick = {}, tint = InspectorTheme.colors.success) },
    ),
)
