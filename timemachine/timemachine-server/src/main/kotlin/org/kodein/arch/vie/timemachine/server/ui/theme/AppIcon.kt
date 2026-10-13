package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val IconBackground = Color(0xFF12161B)
private val IconModel = Color(0xFF86B7FF)
private val IconIntent = Color(0xFFF2B661)
private val IconSignal = Color(0xFFC9A6FF)
private val IconPlayhead = Color(0xFF5ED3BF)

/** The icon's design grid: every coordinate below is in these units, whatever the drawn size. */
private const val Grid = 64f

/**
 * The app icon: a timeline of Model, Intent and Signal bars, stopped at the playhead, "now".
 * It is dark in both themes, being the dock and taskbar identity, and it is the only source of
 * the exported icon files.
 */
@Composable
fun AppIcon(modifier: Modifier = Modifier.size(64.dp)) {
    Canvas(modifier) { drawAppIcon() }
}

/**
 * The icon, in the square this scope has: every coordinate is on the design's 64-unit grid, scaled to the
 * smaller side. The composable, the exported files and the windows' [AppIconPainter] all draw with it.
 */
internal fun DrawScope.drawAppIcon() {
    val k = size.minDimension / Grid
    fun bar(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Color) =
        drawRoundRect(color, Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(radius * k))

    drawRoundRect(IconBackground, Offset.Zero, Size(Grid * k, Grid * k), CornerRadius(15 * k))
    drawInnerBorder(k)
    bar(12f, 17f, 24f, 7f, 4f, IconModel)
    bar(20f, 28.5f, 18f, 7f, 4f, IconIntent)
    bar(12f, 40f, 28f, 7f, 4f, IconSignal)
    bar(45f, 12f, 3f, 40f, 2f, IconPlayhead)
    drawCircle(IconPlayhead, radius = 5 * k, center = Offset(46.5f * k, 14f * k))
}

/** The icon as a [Painter], for what takes one: the icon of the inspector's windows, in their title bars. */
internal object AppIconPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() = drawAppIcon()
}

/** A 1-unit inset outline, `rgba(255,255,255,.1)`. */
private fun DrawScope.drawInnerBorder(k: Float) {
    drawRoundRect(
        color = Color.White.copy(alpha = 0.1f),
        topLeft = Offset(0.5f * k, 0.5f * k),
        size = Size((Grid - 1) * k, (Grid - 1) * k),
        cornerRadius = CornerRadius(14.5f * k),
        style = Stroke(width = k),
    )
}

/** "VIE" in Plex Sans SemiBold with +4% tracking, then "TimeMachine" in Regular, `onSurfaceVariant`. */
@Composable
private fun Wordmark(fontSize: TextUnit = 26.sp, modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em)) { append("VIE") }
            append(" ")
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append("TimeMachine")
            }
        },
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize, color = MaterialTheme.colorScheme.onSurface),
        maxLines = 1,
    )
}

/** The icon beside the wordmark, as in the design's lock-up. */
@Composable
fun AppLockup(iconSize: Dp = 44.dp, fontSize: TextUnit = 26.sp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(Modifier.size(iconSize))
        Wordmark(fontSize)
    }
}
