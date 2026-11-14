package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp


/** [SectionLabel] at the start of a section, at the end and in the centre of a column, and cut in a narrow one. */
internal val SectionLabelScene: GalleryScene = GalleryScene(
    name = "section-label",
    cellWidth = 220.dp,
    rows = listOf(
        GalleryRow("section") { SectionLabel("RECENT INTERVENTIONS") },
        GalleryRow("column · end · 40") { SectionLabel("SEQ", Modifier.width(40.dp), textAlign = TextAlign.End) },
        GalleryRow("column · centre · 52") { SectionLabel("KIND", Modifier.width(52.dp), textAlign = TextAlign.Center) },
        GalleryRow("cut · 90") { SectionLabel("TYPE · CHANGE / TOSTRING", Modifier.width(90.dp)) },
    ),
)
