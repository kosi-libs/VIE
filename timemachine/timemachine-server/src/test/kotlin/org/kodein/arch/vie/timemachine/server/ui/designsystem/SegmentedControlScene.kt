package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind


private val TreeOrRaw = listOf(false, true)

/** [SegmentedControl] at both sizes: a choice, an unavailable option, an unavailable option chosen, and disabled. */
internal val SegmentedControlScene: GalleryScene = GalleryScene(
    name = "segmented-control",
    cellWidth = 320.dp,
    rows = listOf(
        GalleryRow("regular") { SegmentedControl(VIEObjectKind.entries, VIEObjectKind.Model, {}, { it.name }, Modifier.width(300.dp)) },
        GalleryRow("regular · Signal unavailable") {
            SegmentedControl(VIEObjectKind.entries, VIEObjectKind.Intent, {}, { it.name }, Modifier.width(300.dp), optionEnabled = { it != VIEObjectKind.Signal })
        },
        GalleryRow("regular · unavailable chosen") {
            SegmentedControl(VIEObjectKind.entries, VIEObjectKind.Signal, {}, { it.name }, Modifier.width(300.dp), optionEnabled = { it != VIEObjectKind.Signal })
        },
        GalleryRow("regular · disabled") { SegmentedControl(VIEObjectKind.entries, VIEObjectKind.Model, {}, { it.name }, Modifier.width(300.dp), enabled = false) },
        GalleryRow("small · Tree") { SegmentedControl(TreeOrRaw, false, {}, { if (it) "Raw" else "Tree" }, size = SegmentedControlSize.Small) },
        GalleryRow("small · Raw") { SegmentedControl(TreeOrRaw, true, {}, { if (it) "Raw" else "Tree" }, size = SegmentedControlSize.Small) },
    ),
)
