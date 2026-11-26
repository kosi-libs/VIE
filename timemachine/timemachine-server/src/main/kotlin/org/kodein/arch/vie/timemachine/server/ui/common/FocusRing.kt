package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.LocalInspectorColors


/**
 * A ring at the inside edge of [shape] while what is below this in the chain has the keyboard's focus: two lines,
 * [org.kodein.arch.vie.timemachine.server.ui.theme.InspectorColors.focusRing] on the edge and `focusRingInner`
 * inside it. It is for the keyboard: a control the pointer reached, with the focus or not, has the hover's and
 * the press's tint and no ring. It goes before what takes the focus (`clickable`, `focusable`): it reads the
 * focus of the node below it, and draws over what is below.
 */
internal fun Modifier.focusRing(shape: Shape = RectangleShape): Modifier = this then FocusRingElement(shape)

/** The width of the ring's outer line, and of its inner one. */
private val RingLine = 2.dp
private val RingInnerLine = 1.dp

private data class FocusRingElement(val shape: Shape) : ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(shape)

    override fun update(node: FocusRingNode) {
        node.shape = shape
        node.invalidateDraw()
    }
}

private class FocusRingNode(var shape: Shape) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode, CompositionLocalConsumerModifierNode {

    private var visible = false

    override fun onFocusEvent(focusState: FocusState) {
        // Whether the keyboard moved the focus is known as it arrives: the key that moved it came first.
        val show = focusState.isFocused && currentValueOf(LocalInputModeManager).inputMode == InputMode.Keyboard
        if (show != visible) {
            visible = show
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!visible) return
        val colors = currentValueOf(LocalInspectorColors)
        val outline = shape.createOutline(Size(size.width, size.height), layoutDirection, this)
        // A stroke is centred on the edge: clipped to the shape, half of it is left, so each line is drawn twice as wide.
        val inside = Path().apply { addOutline(outline) }
        clipPath(inside) {
            drawOutline(outline, colors.focusRingInner, style = Stroke(2 * (RingLine + RingInnerLine).toPx()))
            drawOutline(outline, colors.focusRing, style = Stroke(2 * RingLine.toPx()))
        }
    }
}
