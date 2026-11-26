package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.kodein.arch.vie.timemachine.server.ui.theme.LightInspectorColors

class JsonTextTest {

    /** What [JsonElement.compact] is to give, the slow way: the whole text, then the cut. */
    private fun JsonElement.cut(max: Int = 40): String = toString().let { if (it.length > max) it.take(max - 1) + "…" else it }

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val colors = LightInspectorColors

    /** What [colorJsonLine] colours: each coloured part of the line, with its colour. */
    private fun AnnotatedString.coloured(): List<Pair<String, Color>> = spanStyles.map { text.substring(it.start, it.end) to it.item.color }

    @Test
    fun `a line is coloured token by token`() {
        val line = """  "name": "say \"hi\"", "count": -1.5e3, "done": true, "none": null"""
        val coloured = colorJsonLine(line, colors)

        assertEquals(line, coloured.text)
        assertEquals(
            listOf(
                "\"name\"" to colors.jsonKey,
                "\"say \\\"hi\\\"\"" to colors.jsonString,
                "\"count\"" to colors.jsonKey,
                "-1.5e3" to colors.jsonNumber,
                "\"done\"" to colors.jsonKey,
                "true" to colors.jsonLiteral,
                "\"none\"" to colors.jsonKey,
                "null" to colors.jsonLiteral,
            ),
            coloured.coloured(),
        )
    }

    @Test
    fun `a line holding a very long string is coloured without overflowing the stack`() {
        val plain = "a".repeat(100_000)
        // Every character of this one is part of an escape: the other way a string is matched.
        val escaped = "\\n".repeat(100_000)
        for (value in listOf(plain, escaped)) {
            val line = "    \"key\": \"$value\","
            val coloured = colorJsonLine(line, colors)

            assertEquals(line, coloured.text)
            val parts = coloured.coloured()
            assertEquals(listOf("\"key\"" to colors.jsonKey), parts.take(1))
            assertEquals(2, parts.size)
            assertEquals(colors.jsonString, parts[1].second)
            assertEquals(value.length + 2, parts[1].first.length)
        }
    }

    @Test
    fun `a string that is never closed is left uncoloured`() {
        val line = "\"key\": \"no end"
        assertEquals(listOf("\"key\"" to colors.jsonKey), colorJsonLine(line, colors).coloured())
    }

    @Test
    fun `a value that fits is written whole`() {
        assertEquals("42", JsonPrimitive(42).compact())
        assertEquals("null", json("null").compact())
        assertEquals("\"milk\"", JsonPrimitive("milk").compact())
        assertEquals("{}", json("{}").compact())
        assertEquals("[]", json("[]").compact())
        assertEquals("""{"id":"a","done":true,"tags":[1,2]}""", json("""{"id": "a", "done": true, "tags": [1, 2]}""").compact())
    }

    @Test
    fun `a longer one is cut to the length with an ellipsis`() {
        val task = json("""{"id": "8c1e5d2a", "title": "Buy milk", "notes": "2 litres", "completed": false}""")
        assertEquals("""{"id":"8c1e5d2a","title":"Buy milk","no…""", task.compact())
        assertEquals(40, task.compact().length)
    }

    @Test
    fun `it is the text's own start, whatever the value and wherever the cut falls`() {
        val values = listOf(
            json("""{"quote": "say \"hi\"\n", "back\\slash": "é", "nested": {"deep": {"deeper": [1, 2, 3, {"a": null}]}}}"""),
            json("""[[], {}, [[]], {"a": {}}, "", 0, -1.5e3, true, false, null, "a long enough string to pass the cut"]"""),
            JsonPrimitive("a string that is on its own longer than the forty characters kept"),
            buildJsonArray { repeat(200) { add(JsonPrimitive(it)) } },
            buildJsonObject { repeat(200) { put("key$it", buildJsonArray { add(JsonPrimitive("v$it")) }) } },
        )
        for (value in values) for (max in 1..120) assertEquals(value.cut(max), value.compact(max), "cut at $max of $value")
    }

    @Test
    fun `a document far longer than the cut is not written whole`() {
        var reads = 0
        val counting = object : AbstractList<JsonElement>() {
            override val size = 100_000
            override fun get(index: Int): JsonElement = JsonPrimitive(index).also { reads++ }
        }
        assertEquals("[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,…", JsonArray(counting).compact())
        assertTrue(reads < 100, "it read $reads items")
    }
}
