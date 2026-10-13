package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorColors
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
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

private fun InspectorColors.value(kind: ValueKind, punctuation: Color): Color = when (kind) {
    ValueKind.Punctuation -> punctuation
    ValueKind.String -> jsonString
    ValueKind.Number -> jsonNumber
    ValueKind.Literal -> jsonLiteral
}

/**
 * A collapsible JSON tree. The nodes in [changed] sit on the model-container tint, which fades with
 * [highlight] (1 to 0): the highlight is an animation, the set is the model's.
 */
@Composable
internal fun JsonTree(
    root: JsonElement,
    expanded: Set<JsonPath>,
    modifier: Modifier = Modifier,
    changed: Set<JsonPath> = emptySet(),
    highlight: Float = 0f,
    onToggle: (JsonPath) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Column(modifier) {
        for (line in treeLines(root, expanded)) {
            val tint = if (line.path in changed && !line.closer) colors.modelContainer.copy(alpha = highlight) else Color.Transparent
            Row(
                Modifier.fillMaxWidth().height(InspectorDimens.codeLine)
                    .background(tint, MaterialTheme.shapes.extraSmall)
                    .then(if (line.openable) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onToggle(line.path) } else Modifier)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(InspectorDimens.treeIndent * line.depth))
                if (line.openable) Symbol(if (line.open) "expand_more" else "chevron_right", size = 14.dp, tint = scheme.outline)
                else Spacer(Modifier.width(14.dp))
                if (line.key != null) {
                    Text(line.key, style = monoStyle(12f, if (line.isIndex) scheme.outline else colors.jsonKey), maxLines = 1)
                    Text(": ", style = monoStyle(12f, scheme.outline), maxLines = 1)
                }
                Text(line.text, style = monoStyle(12f, colors.value(line.kind, scheme.onSurfaceVariant)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                line.summary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic), color = scheme.outline, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** [text] as numbered, coloured lines. */
@Composable
internal fun RawJson(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Column(modifier) {
        text.lines().forEachIndexed { index, line ->
            Row(Modifier.height(InspectorDimens.codeLine)) {
                Text((index + 1).toString(), style = monoStyle(12f, scheme.outline), textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(34.dp).padding(end = 10.dp))
                Text(colorJsonLine(line, colors), style = monoStyle(12f, scheme.onSurfaceVariant), softWrap = false, maxLines = 1)
            }
        }
    }
}

/** The Tree | Raw segmented switch of the current model's header. */
@Composable
internal fun TreeRawSwitch(raw: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    Row(
        modifier.height(24.dp).clip(InspectorPillShape).border(1.dp, colors.strongOutline, InspectorPillShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((label, isRaw) in listOf("Tree" to false, "Raw" to true)) {
            val on = raw == isRaw
            Row(
                Modifier.fillMaxHeight().background(if (on) scheme.primaryContainer else Color.Transparent)
                    .pointerHoverIcon(PointerIcon.Hand).clickable { onChange(isRaw) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
