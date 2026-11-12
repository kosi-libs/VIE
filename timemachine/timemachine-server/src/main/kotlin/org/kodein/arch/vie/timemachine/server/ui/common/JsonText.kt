package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorColors
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme

private val PrettyJson = Json { prettyPrint = true }

/** This JSON as the editor and the raw view show it. */
fun JsonElement.pretty(): String = PrettyJson.encodeToString(JsonElement.serializer(), this)

@Composable
internal fun DiffText(op: DiffOp) {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val symbolColor = when (op.kind) {
        DiffKind.Added -> colors.success
        DiffKind.Removed -> colors.error
        DiffKind.Changed -> colors.model
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = symbolColor, fontWeight = FontWeight.SemiBold)) { append(op.kind.symbol) }
            append(" ")
            withStyle(SpanStyle(color = scheme.onSurfaceVariant)) { append(op.path.joinToString(".")) }
            op.old?.let {
                append(" ")
                withStyle(SpanStyle(color = scheme.outline, textDecoration = if (op.new != null) TextDecoration.LineThrough else TextDecoration.None)) { append(it.compact()) }
            }
            if (op.old != null && op.new != null) withStyle(SpanStyle(color = scheme.outline)) { append(" → ") }
            op.new?.let {
                append(if (op.old == null) " " else "")
                withStyle(SpanStyle(color = scheme.onSurface)) { append(it.compact()) }
            }
        },
        style = monoStyle(11.5f, scheme.onSurface), maxLines = 1,
    )
}

/**
 * This JSON on one line, cut to [max] characters with an ellipsis. A diff's value can be a whole
 * document, so it is written up to the cut rather than serialised whole and cut after.
 */
internal fun JsonElement.compact(max: Int = 40): String {
    val text = StringBuilder().apply { appendUpTo(max, this@compact) }
    return if (text.length > max) text.take(max - 1).toString() + "…" else text.toString()
}

/** Appends [value] as its `toString` writes it, and stops adding members once past [max] characters. */
private fun StringBuilder.appendUpTo(max: Int, value: JsonElement) {
    when (value) {
        is JsonObject -> {
            append('{')
            var first = true
            for ((key, member) in value) {
                if (length > max) return
                if (!first) append(',')
                first = false
                append(JsonPrimitive(key).toString()).append(':')
                appendUpTo(max, member)
            }
            append('}')
        }
        is JsonArray -> {
            append('[')
            for ((index, item) in value.withIndex()) {
                if (length > max) return
                if (index > 0) append(',')
                appendUpTo(max, item)
            }
            append(']')
        }
        is JsonPrimitive -> append(value.toString())
    }
}

private val JsonToken = Regex("""("(?:[^"\\]|\\.)*")(\s*:)?|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false|null)\b""")

internal fun colorJsonLine(line: String, colors: InspectorColors) = buildAnnotatedString {
    var last = 0
    for (match in JsonToken.findAll(line)) {
        append(line.substring(last, match.range.first))
        val (string, colon, number, literal) = match.destructured
        val color = when {
            string.isNotEmpty() && colon.isNotEmpty() -> colors.jsonKey
            string.isNotEmpty() -> colors.jsonString
            number.isNotEmpty() -> colors.jsonNumber
            else -> colors.jsonLiteral
        }
        withStyle(SpanStyle(color = color)) { append(string + number + literal) }
        append(colon)
        last = match.range.last + 1
    }
    append(line.substring(last))
}

/** The lines of [text], coloured as JSON, kept for as long as the text and the colours are the same. */
@Composable
internal fun rememberJsonLines(text: String): List<AnnotatedString> {
    val colors = InspectorTheme.colors
    return remember(text, colors) { text.lines().map { colorJsonLine(it, colors) } }
}
