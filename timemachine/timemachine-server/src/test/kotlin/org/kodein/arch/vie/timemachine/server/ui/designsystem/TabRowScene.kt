package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


private val Tabs = listOf("Detail", "Intervene")

/** [TabRow] on each tab, with and without a badge. */
internal val TabRowScene: GalleryScene = GalleryScene(
    name = "tab-row",
    cellWidth = 320.dp,
    rows = listOf(
        GalleryRow("first shown") { TabRow(Tabs, "Detail", {}, { it }, Modifier.width(300.dp)) },
        GalleryRow("second shown · badge") { TabRow(Tabs, "Intervene", {}, { it }, Modifier.width(300.dp), badge = { if (it == "Intervene") "3" else null }) },
    ),
)
