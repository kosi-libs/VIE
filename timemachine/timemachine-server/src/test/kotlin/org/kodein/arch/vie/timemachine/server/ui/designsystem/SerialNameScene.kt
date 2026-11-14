package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/** The room a [SerialName] is given: a width, and how many lines it may take. */
private class SerialNameRoom(val label: String, val width: Dp, val maxLines: Int)

/** Names as the views show them: one with no package, a kind's type, and a long subtype. */
private val SerialNames = listOf(
    "Model",
    "org.kodein.arch.vie.demo.vie.TaskListVIE.Model",
    "org.kodein.arch.vie.demo.vie.TaskListVIE.Intent.UpdateTaskCompletion",
)

/** [SerialName] from a wide room to a tiny one, on one line, on two, and on as many as it takes. */
internal val SerialNameScene: GalleryScene = galleryMatrix(
    name = "serial-name",
    rows = listOf(
        SerialNameRoom("1 line · 520", 520.dp, 1),
        SerialNameRoom("1 line · 300", 300.dp, 1),
        SerialNameRoom("1 line · 140", 140.dp, 1),
        SerialNameRoom("1 line · 60", 60.dp, 1),
        SerialNameRoom("2 lines · 220", 220.dp, 2),
        SerialNameRoom("unbounded · 220", 220.dp, Int.MAX_VALUE),
    ),
    columns = SerialNames,
    rowLabel = { it.label },
    columnLabel = { it.substringAfterLast('.') },
    cellWidth = 540.dp,
) { room, name ->
    Box(Modifier.width(room.width)) { SerialName(name, InspectorTheme.text.code, maxLines = room.maxLines) }
}
