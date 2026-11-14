package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph


/** [EmptyState] from a title alone to a glyph, a title, a description and an action. */
internal val EmptyStateScene: GalleryScene = GalleryScene(
    name = "empty-state",
    cellWidth = 380.dp,
    rows = listOf(
        GalleryRow("title") { EmptyState("No model yet", Modifier.fillMaxWidth()) },
        GalleryRow("title · description") {
            EmptyState("No observations yet", Modifier.fillMaxWidth(), description = "Use the app — its Models, Intents and Signals appear here.")
        },
        GalleryRow("glyph · title · description") {
            EmptyState("Select a ViewModel to see its timeline", Modifier.fillMaxWidth(), glyph = Glyph.ViewTimeline, description = "Demo has 4 ViewModels · pick one on the left")
        },
        GalleryRow("glyph · title · description · action") {
            EmptyState(
                "No entry matches the filters", Modifier.fillMaxWidth(), glyph = Glyph.FilterAltOff, description = "3 entries hidden",
                action = { LinkAction("Clear filters", onClick = {}) },
            )
        },
    ),
)
