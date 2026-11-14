package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import org.kodein.arch.vie.timemachine.server.ui.common.splitSerialName


/**
 * A serial name in two tones: its packages and enclosing classes muted, then its last segment, the
 * type's own name, in bold. It takes the width of its text, at most the width it is given.
 *
 * When the name does not fit in [maxLines] lines, its prefix gives way first, cut at its end behind an
 * ellipsis, for as long as the last segment then fits whole; once nothing of the prefix is left, the last
 * segment is cut at its end. Its semantics name the type whole, however it is drawn.
 *
 * It lays the name out when it is measured, not when it is composed: resized, as a dragged splitter does
 * at each frame, it composes nothing; and it answers the intrinsic measurements that a menu asks of its
 * items.
 */
@Composable
internal fun SerialName(name: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) {
    require(maxLines > 0) { "A serial name takes at least one line, not $maxLines" }
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    Spacer(modifier.then(SerialNameElement(name, style, scheme.outline, scheme.onSurface, maxLines, measurer)))
}

/** What a [SerialName] draws, and with what: compared by value, so that an unchanged name updates nothing. */
private data class SerialNameElement(
    val name: String,
    val style: TextStyle,
    val muted: Color,
    val strong: Color,
    val maxLines: Int,
    val measurer: TextMeasurer,
) : ModifierNodeElement<SerialNameNode>() {
    override fun create(): SerialNameNode = SerialNameNode(this)

    // An updated node is invalidated whole (Modifier.Node.shouldAutoInvalidate): it measures, draws and
    // describes itself again.
    override fun update(node: SerialNameNode) {
        node.element = this
    }
}

private class SerialNameNode(var element: SerialNameElement) :
    Modifier.Node(), LayoutModifierNode, DrawModifierNode, SemanticsModifierNode {

    /** What the last measure laid out: what is drawn. */
    private var laidOut: TextLayoutResult? = null

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val result = element.fit(constraints.maxWidth)
        // Read here, so that a font resolved later lays the name out again, as Text does.
        result.multiParagraph.intrinsics.hasStaleResolvedFonts
        if (result != laidOut) {
            laidOut = result
            invalidateDraw()
        }
        val width = constraints.constrainWidth(result.size.width)
        val height = constraints.constrainHeight(result.size.height)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        return layout(width, height) { placeable.place(0, 0) }
    }

    // Asked by a menu for its items, and by rows for their children: answered without touching what is drawn.
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        element.fit(Constraints.Infinity).size.width

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        element.fit(width).size.height

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        element.fit(width).size.height

    override fun ContentDrawScope.draw() {
        drawContent()
        laidOut?.let { drawText(it) }
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        text = AnnotatedString(element.name)
        getTextLayoutResult { results -> laidOut?.let { results.add(it) } ?: false }
    }
}

/** [prefix] muted, then an ellipsis if it was [cut], then [last] in bold. */
private fun SerialNameElement.text(prefix: String, last: String, cut: Boolean): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = muted)) {
        append(prefix)
        if (cut) append('…')
    }
    withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append(last) }
}

/** [text] laid out in [maxWidth] px, on [SerialNameElement.maxLines] lines at most, cut with an ellipsis. */
private fun SerialNameElement.lay(text: AnnotatedString, maxWidth: Int): TextLayoutResult =
    measurer.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = maxLines, constraints = Constraints(maxWidth = maxWidth))

/**
 * The name laid out in [maxWidth] px: whole if it fits; else the longest start of its prefix that, behind
 * an ellipsis, leaves the last segment whole; else the last segment alone, cut at its end.
 */
private fun SerialNameElement.fit(maxWidth: Int): TextLayoutResult {
    val (prefix, last) = splitSerialName(name)
    val whole = lay(text(prefix, last, cut = false), maxWidth)
    if (!whole.hasVisualOverflow || prefix.isEmpty()) return whole
    // The more of the prefix is kept, the more room it takes: search the longest start that fits.
    var fitting: TextLayoutResult? = null
    var low = 0
    var high = prefix.length - 1
    while (low <= high) {
        val kept = (low + high) ushr 1
        val candidate = lay(text(prefix.take(kept), last, cut = true), maxWidth)
        if (candidate.hasVisualOverflow) high = kept - 1
        else {
            fitting = candidate
            low = kept + 1
        }
    }
    return fitting ?: lay(text("", last, cut = false), maxWidth)
}
