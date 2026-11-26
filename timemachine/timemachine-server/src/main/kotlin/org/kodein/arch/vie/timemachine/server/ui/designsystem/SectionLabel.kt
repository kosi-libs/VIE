package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/**
 * The label of a section or of a column, which its caller writes in capitals: "TOSTRING", "RECENT
 * INTERVENTIONS", "SEQ". It is set in the `sectionLabel` text role, in the outline's colour, on one line,
 * and cut with an ellipsis when it has not the room.
 *
 * @param textAlign Where it sits in a width it is given: the end for a column of numbers, the centre for
 *   one of badges.
 */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text, modifier,
        color = MaterialTheme.colorScheme.outline, textAlign = textAlign,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = InspectorTheme.text.sectionLabel,
    )
}
