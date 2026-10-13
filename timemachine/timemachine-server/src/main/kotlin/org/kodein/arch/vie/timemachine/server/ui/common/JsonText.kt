package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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

private fun JsonElement.compact(): String = toString().let { if (it.length > 40) it.take(39) + "…" else it }

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
