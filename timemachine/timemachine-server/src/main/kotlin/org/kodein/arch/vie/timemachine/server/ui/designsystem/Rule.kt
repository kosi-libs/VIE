package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens


/**
 * A rule across the width it is given: under a header, between rows, or, weighted, on each side of a
 * label in a `Row`, where it takes the room the label leaves.
 */
@Composable
internal fun HorizontalRule(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Box(modifier.fillMaxWidth().height(InspectorDimens.rule).background(color))
}

/** A rule down the height it is given: the edge of a pane, a splitter's line, between two segments. */
@Composable
internal fun VerticalRule(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Box(modifier.fillMaxHeight().width(InspectorDimens.rule).background(color))
}
