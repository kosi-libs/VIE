package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/** The action of the Sessions pane's header, as it is there. */
@Composable
private fun ClearDisconnected() {
    InspectorButton("Clear disconnected", onClick = {}, glyph = Glyph.DeleteSweep, variant = ButtonVariant.Text, enabled = true)
}

/** [PaneHeader] with a title alone, a count, an action, a glyph, and in a pane too narrow for its position. */
internal val PaneHeaderScene: GalleryScene = GalleryScene(
    name = "pane-header",
    cellWidth = 380.dp,
    rows = listOf(
        GalleryRow("title") { PaneHeader("Sessions") },
        GalleryRow("title · count") { PaneHeader("Sessions", count = "3") },
        GalleryRow("title · count · action") { PaneHeader("Sessions", count = "3") { ClearDisconnected() } },
        GalleryRow("glyph · title · position") {
            PaneHeader("Current model", count = "#208 · 12:00:01.123", glyph = Glyph.PushPin, glyphTint = InspectorTheme.colors.model)
        },
        GalleryRow("narrow · position cut") {
            Box(Modifier.width(320.dp)) {
                PaneHeader("Current model", count = "#208 · 12:00:01.123", glyph = Glyph.PushPin, glyphTint = InspectorTheme.colors.model) { ClearDisconnected() }
            }
        },
    ),
)
