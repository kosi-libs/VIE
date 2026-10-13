package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A Material Symbols Rounded glyph, drawn by ligature: [name] is the symbol's name
 * (`inventory_2`, `history_toggle_off`). [mirrored] flips it horizontally, as the design does for
 * `logout` ("out of the ViewModel").
 */
@Composable
fun Symbol(
    name: String,
    size: Dp = 16.dp,
    tint: Color = LocalContentColor.current,
    mirrored: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text = name,
        modifier = modifier
            .size(size)
            .clipToBounds()
            .graphicsLayer { if (mirrored) scaleX = -1f },
        style = TextStyle(
            fontFamily = InspectorTheme.fonts.symbols,
            fontSize = fontSize,
            lineHeight = fontSize,
            color = tint,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        softWrap = false,
    )
}
