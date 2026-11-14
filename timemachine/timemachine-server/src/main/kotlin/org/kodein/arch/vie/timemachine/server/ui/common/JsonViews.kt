package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.server.ui.designsystem.SegmentedControl
import org.kodein.arch.vie.timemachine.server.ui.designsystem.SegmentedControlSize
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorColors
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/** What colours a tree line's value. */
internal enum class ValueKind { Punctuation, String, Number, Literal }

/**
 * One line of a JSON tree.
 *
 * @property path The node's path from the root; a closing bracket has its container's.
 * @property key The object key or array index, `null` for the root and a closing bracket.
 * @property openable Whether the line is a container with children, which a click opens or closes.
 * @property closer Whether it is a closing bracket.
 */
internal data class TreeLine(
    val path: JsonPath,
    val depth: Int,
    val key: String?,
    val isIndex: Boolean,
    val text: String,
    val kind: ValueKind,
    val summary: String?,
    val openable: Boolean,
    val open: Boolean,
    val closer: Boolean,
)

/** The lines of [root] with the containers in [expanded] open. */
internal fun treeLines(root: JsonElement, expanded: Set<JsonPath>): List<TreeLine> = buildList {
    fun walk(value: JsonElement, key: String?, path: JsonPath, depth: Int, isIndex: Boolean) {
        when (value) {
            is JsonObject, is JsonArray -> {
                val array = value is JsonArray
                val size = if (value is JsonObject) value.size else (value as JsonArray).size
                if (size == 0) {
                    add(TreeLine(path, depth, key, isIndex, if (array) "[]" else "{}", ValueKind.Punctuation, null, false, false, false))
                    return
                }
                val open = path in expanded
                val noun = if (array) (if (size == 1) "item" else "items") else (if (size == 1) "key" else "keys")
                add(TreeLine(path, depth, key, isIndex, if (open) (if (array) "[" else "{") else (if (array) "[…]" else "{…}"), ValueKind.Punctuation, "$size $noun", true, open, false))
                if (open) {
                    if (value is JsonObject) value.forEach { (k, v) -> walk(v, k, path + k, depth + 1, false) }
                    else (value as JsonArray).forEachIndexed { i, v -> walk(v, i.toString(), path + i.toString(), depth + 1, true) }
                    add(TreeLine(path, depth, null, false, if (array) "]" else "}", ValueKind.Punctuation, null, false, false, true))
                }
            }
            is JsonNull -> add(TreeLine(path, depth, key, isIndex, "null", ValueKind.Literal, null, false, false, false))
            is JsonPrimitive -> add(
                TreeLine(
                    path, depth, key, isIndex, value.toString(),
                    if (value.isString) ValueKind.String else if (value.content == "true" || value.content == "false") ValueKind.Literal else ValueKind.Number,
                    null, false, false, false,
                ),
            )
        }
    }
    walk(root, null, emptyList(), 0, false)
}

/**
 * What a JSON viewer shows of its document, which its pane keeps: the text or the tree, and the nodes
 * of the tree that are open.
 *
 * @property raw Whether the JSON shows as text rather than as a tree.
 * @property expanded The tree nodes that are open; the root is, to begin with.
 */
internal data class JsonViewControls(
    val raw: Boolean = false,
    val expanded: Set<JsonPath> = setOf(emptyList()),
) {
    /**
     * These controls with the node at [path] open if it was closed, and closed if it was open.
     * (A path is a list, which `+` and `-` would take for the elements to add or remove: hence `plusElement`.)
     */
    fun toggled(path: JsonPath): JsonViewControls = copy(expanded = if (path in expanded) expanded.minusElement(path) else expanded.plusElement(path))
}

private fun InspectorColors.value(kind: ValueKind, punctuation: Color): Color = when (kind) {
    ValueKind.Punctuation -> punctuation
    ValueKind.String -> jsonString
    ValueKind.Number -> jsonNumber
    ValueKind.Literal -> jsonLiteral
}

/**
 * A collapsible JSON tree, a lazy item per line of [lines] (see [treeLines]): a list only composes the
 * lines in view, however large the document. The nodes in [changed] sit on the model-container tint,
 * which fades with [highlight] (1 to 0): the highlight is an animation, the set is the model's. It is
 * read when a line is drawn, so a fade redraws the changed lines and composes nothing.
 *
 * @param framed Whether the lines are drawn in a code frame of their own, see [codeFrame].
 */
internal fun LazyListScope.jsonTree(
    lines: List<TreeLine>,
    changed: Set<JsonPath> = emptySet(),
    highlight: () -> Float = { 0f },
    framed: Boolean = false,
    onToggle: (JsonPath) -> Unit,
) {
    itemsIndexed(lines, key = { _, line -> line.itemKey() }, contentType = { _, _ -> JsonLineContent.Tree }) { index, line ->
        TreeLineRow(
            line, tinted = line.path in changed && !line.closer, highlight = highlight,
            modifier = if (framed) codeFrame(first = index == 0, last = index == lines.lastIndex) else Modifier,
            onToggle = onToggle,
        )
    }
}

/** [lines] of JSON text (see [rememberJsonLines]), numbered, a lazy item per line. */
internal fun LazyListScope.rawJson(lines: List<AnnotatedString>, framed: Boolean = false) {
    itemsIndexed(lines, key = { index, _ -> "json-text:$index" }, contentType = { _, _ -> JsonLineContent.Text }) { index, line ->
        TextLineRow(index + 1, line, if (framed) codeFrame(first = index == 0, last = index == lines.lastIndex) else Modifier)
    }
}

private enum class JsonLineContent { Tree, Text }

/**
 * What tells this line from the others of its tree, and from whatever else its list holds. Each segment
 * of the path is written with its length, so that no two paths give the same key whatever their keys
 * contain; a closing bracket has its container's path.
 */
private fun TreeLine.itemKey(): String = buildString {
    append(if (closer) "json-close:" else "json:")
    for (segment in path) append(segment.length).append(':').append(segment)
}

@Composable
private fun TreeLineRow(line: TreeLine, tinted: Boolean, highlight: () -> Float, modifier: Modifier, onToggle: (JsonPath) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val shape = MaterialTheme.shapes.extraSmall
    Row(
        modifier.fillMaxWidth().height(InspectorDimens.codeLine)
            .then(
                if (tinted) Modifier.drawBehind {
                    drawOutline(shape.createOutline(size, layoutDirection, this), colors.modelContainer.copy(alpha = highlight()))
                } else Modifier,
            )
            .then(if (line.openable) Modifier.action { onToggle(line.path) } else Modifier)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(InspectorDimens.treeIndent * line.depth))
        if (line.openable) Symbol(if (line.open) Glyph.ExpandMore else Glyph.ChevronRight, size = 14.dp, tint = scheme.outline)
        else Spacer(Modifier.width(14.dp))
        if (line.key != null) {
            Text(line.key, style = InspectorTheme.text.code, color = if (line.isIndex) scheme.outline else colors.jsonKey, maxLines = 1)
            Text(": ", style = InspectorTheme.text.code, color = scheme.outline, maxLines = 1)
        }
        Text(line.text, style = InspectorTheme.text.code, color = colors.value(line.kind, scheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        line.summary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = scheme.outline, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun TextLineRow(number: Int, line: AnnotatedString, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().height(InspectorDimens.codeLine)) {
        Text(number.toString(), style = InspectorTheme.text.code, color = scheme.outline, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(34.dp).padding(end = 10.dp))
        Text(line, style = InspectorTheme.text.code, color = scheme.onSurfaceVariant, softWrap = false, maxLines = 1)
    }
}

/**
 * The part of a code frame that one of its lines draws: the frame is a rounded, outlined container
 * around lines that are each an item of a lazy list, so no node spans it. Each line draws the whole
 * frame's outline where it would be if the frame ended at this line's edges only when it is the [first]
 * or the [last], and is clipped to its own bounds; the first and the last also keep the frame's vertical
 * padding.
 */
@Composable
private fun codeFrame(first: Boolean, last: Boolean): Modifier {
    val fill = MaterialTheme.colorScheme.surfaceContainer
    val stroke = MaterialTheme.colorScheme.outlineVariant
    return Modifier
        .drawBehind {
            val radius = CodeFrameRadius.toPx()
            val width = 1.dp.toPx()
            // Beyond this line's bounds where the frame goes on: its corners are then out of the clip.
            val top = if (first) 0f else -2 * radius
            val bottom = if (last) size.height else size.height + 2 * radius
            clipRect {
                drawRoundRect(fill, Offset(0f, top), Size(size.width, bottom - top), CornerRadius(radius))
                drawRoundRect(
                    stroke, Offset(width / 2, top + width / 2), Size(size.width - width, bottom - top - width),
                    CornerRadius(radius - width / 2), style = Stroke(width),
                )
            }
        }
        .padding(start = 4.dp, end = 4.dp, top = if (first) 6.dp else 0.dp, bottom = if (last) 6.dp else 0.dp)
}

private val CodeFrameRadius = 8.dp

/** The Tree | Raw switch of a JSON view's header: [raw] is whether the text shows rather than the tree. */
@Composable
internal fun TreeRawSwitch(raw: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    SegmentedControl(TreeOrRaw, selected = raw, onSelect = onChange, label = { if (it) "Raw" else "Tree" }, modifier = modifier, size = SegmentedControlSize.Small)
}

/** The switch's options, the tree first: whether the text shows. */
private val TreeOrRaw = listOf(false, true)
