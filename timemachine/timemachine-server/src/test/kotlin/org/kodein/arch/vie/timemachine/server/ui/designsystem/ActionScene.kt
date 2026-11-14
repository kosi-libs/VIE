package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape


/** The shapes the views give an action, from the default rectangle of a row to the circle of a glyph button. */
private enum class ActionShape(val label: String) { Rectangle("rectangle"), Medium("shapes.medium"), Pill("pill"), Circle("circle") }

/** [action] on each shape, in each state: what shows the pointer keeps to the outline. */
internal val ActionScene: GalleryScene = galleryMatrix(
    name = "action",
    rows = ActionShape.entries,
    columns = SpecimenState.entries,
    rowLabel = { it.label },
    columnLabel = { it.label },
) { row, state ->
    val shape = when (row) {
        ActionShape.Rectangle -> RectangleShape
        ActionShape.Medium -> MaterialTheme.shapes.medium
        ActionShape.Pill -> InspectorPillShape
        ActionShape.Circle -> CircleShape
    }
    Box(
        Modifier
            .size(if (row == ActionShape.Circle) DpSize(26.dp, 26.dp) else DpSize(112.dp, 26.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .action(shape = shape, enabled = state.enabled, interactionSource = rememberSpecimenInteraction(state)) {},
    )
}
