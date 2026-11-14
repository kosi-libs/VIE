package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.kodein.arch.vie.timemachine.server.ui.common.JsonPath
import org.kodein.arch.vie.timemachine.server.ui.common.JsonViewControls
import org.kodein.arch.vie.timemachine.server.ui.common.pretty


/** A task list as the demo's TaskListModel has it: an array of one task, then two values. */
private val TaskList: JsonElement = Json.parseToJsonElement("""{"tasks":[{"id":"8c1e5d2a","title":"Buy milk","done":false}],"filter":"All","count":1}""")
private val TaskListText: String = TaskList.pretty()

/** The tree with every container open: eleven lines, all in view. */
private val AllOpen: Set<JsonPath> = setOf(emptyList(), listOf("tasks"), listOf("tasks", "0"))

/** How a view shows the viewer: its variant, where it puts the switch, and the nodes it tints. */
private enum class JsonViewerSpecimen(val label: String, val variant: JsonViewerVariant, val changed: Set<JsonPath>) {
    Plain("plain · header", JsonViewerVariant.Plain, emptySet()),
    Changed("plain · changed", JsonViewerVariant.Plain, setOf(listOf("tasks", "0", "done"), listOf("count"))),
    Framed("framed · leading item", JsonViewerVariant.Framed, emptySet()),
}

/** [JsonViewer] as a tree and as text: plain under a header, with changed nodes at full highlight, and framed under a title. */
internal val JsonViewerScene: GalleryScene = galleryMatrix(
    name = "json-viewer",
    rows = JsonViewerSpecimen.entries,
    columns = listOf(false, true),
    rowLabel = { it.label },
    columnLabel = { raw -> if (raw) "raw" else "tree" },
    cellWidth = 320.dp,
) { specimen, raw ->
    val highlight = remember { mutableFloatStateOf(1f) }
    val scheme = MaterialTheme.colorScheme
    JsonViewer(
        json = TaskList,
        rawText = TaskListText,
        controls = JsonViewControls(raw = raw, expanded = AllOpen),
        onRawChange = {},
        onToggle = {},
        modifier = Modifier.size(width = 296.dp, height = 260.dp),
        variant = specimen.variant,
        changed = specimen.changed,
        highlight = if (specimen.changed.isEmpty()) null else highlight,
        header = { rawSwitch ->
            if (specimen.variant == JsonViewerVariant.Plain) Row(
                Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Current model", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1)
                Spacer(Modifier.weight(1f))
                rawSwitch()
            }
        },
        leadingItems = { rawSwitch ->
            if (specimen.variant == JsonViewerVariant.Framed) item {
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("FULL MODEL", style = MaterialTheme.typography.labelSmall, color = scheme.outline, modifier = Modifier.weight(1f))
                    rawSwitch()
                }
            }
        },
    )
}
