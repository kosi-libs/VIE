package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorColors
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme

@Composable
internal fun KindBadge(kind: VIEObjectKind) {
    val colors = InspectorTheme.colors
    Text(
        kind.name,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.02.sp), color = colors.kind(kind), textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(InspectorDimens.kindBadgeWidth).height(InspectorDimens.kindBadgeHeight)
            .background(colors.kindContainer(kind), RoundedCornerShape(4.dp)).wrapLine(),
    )
}

/** Centres a single line vertically in a fixed-height badge. */
private fun Modifier.wrapLine(): Modifier = this.padding(top = 1.dp)

internal fun InspectorColors.kind(kind: VIEObjectKind): Color = when (kind) {
    VIEObjectKind.Model -> model
    VIEObjectKind.Intent -> intent
    VIEObjectKind.Signal -> signal
}

internal fun InspectorColors.kindContainer(kind: VIEObjectKind): Color = when (kind) {
    VIEObjectKind.Model -> modelContainer
    VIEObjectKind.Intent -> intentContainer
    VIEObjectKind.Signal -> signalContainer
}

/** The type info of one of a ViewModel's three roles. */
fun VIEViewModelInfo.role(kind: VIEObjectKind): VIETypeInfo = when (kind) {
    VIEObjectKind.Model -> model
    VIEObjectKind.Intent -> intent
    VIEObjectKind.Signal -> signal
}

/** The label of the button that sends an intervention of [kind]. */
internal fun verb(kind: VIEObjectKind): String = when (kind) {
    VIEObjectKind.Model -> "Replace model"
    VIEObjectKind.Intent -> "Post intent"
    VIEObjectKind.Signal -> "Emit signal"
}
