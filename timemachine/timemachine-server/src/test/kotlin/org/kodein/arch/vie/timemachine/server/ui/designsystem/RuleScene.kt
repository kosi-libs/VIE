package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/** The rules: across, in the outline and the strong outline; weighted on each side of a label; and down, between two segments. */
internal val RuleScene: GalleryScene = GalleryScene(
    name = "rule",
    cellWidth = 240.dp,
    rows = listOf(
        GalleryRow("horizontal") { HorizontalRule() },
        GalleryRow("horizontal · strong") { HorizontalRule(color = InspectorTheme.colors.strongOutline) },
        GalleryRow("horizontal · weighted") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalRule(Modifier.weight(1f), color = InspectorTheme.colors.strongOutline)
                Text("12 older entries evicted", style = InspectorTheme.text.note, color = InspectorTheme.colors.evicted)
                HorizontalRule(Modifier.weight(1f), color = InspectorTheme.colors.strongOutline)
            }
        },
        GalleryRow("vertical") {
            Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Model", Modifier.padding(horizontal = 12.dp), style = InspectorTheme.text.label, color = MaterialTheme.colorScheme.onSurface)
                VerticalRule(color = InspectorTheme.colors.strongOutline)
                Text("Intent", Modifier.padding(horizontal = 12.dp), style = InspectorTheme.text.label, color = MaterialTheme.colorScheme.onSurface)
            }
        },
    ),
)
