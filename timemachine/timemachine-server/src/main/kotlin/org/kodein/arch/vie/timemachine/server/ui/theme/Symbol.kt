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
 * A Material Symbols Rounded [glyph], drawn by its ligature. [mirrored] flips it horizontally, as the
 * design does for [Glyph.Logout] ("out of the ViewModel").
 */
@Composable
fun Symbol(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = LocalContentColor.current,
    mirrored: Boolean = false,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text = glyph.ligature,
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
