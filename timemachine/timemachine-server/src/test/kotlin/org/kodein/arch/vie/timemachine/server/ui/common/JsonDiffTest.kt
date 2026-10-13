package org.kodein.arch.vie.timemachine.server.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive


class JsonDiffTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun diff(old: String, new: String) = diffJson(json(old), json(new))

    @Test
    fun equalDocumentsHaveNoDiff() {
        assertEquals(emptyList(), diff("""{"a":[1,{"b":2}]}""", """{"a":[1,{"b":2}]}"""))
    }

    @Test
    fun aChangedFieldKeepsItsPathAndBothValues() {
        assertEquals(
            listOf(DiffOp(DiffKind.Changed, listOf("loading"), JsonPrimitive(true), JsonPrimitive(false))),
            diff("""{"loading":true}""", """{"loading":false}"""),
        )
    }

    @Test
    fun anAddedAndARemovedKeyAreTold() {
        assertEquals(
            listOf(
                DiffOp(DiffKind.Removed, listOf("a"), JsonPrimitive(1), null),
                DiffOp(DiffKind.Added, listOf("b"), null, JsonPrimitive(2)),
            ),
            diff("""{"a":1}""", """{"b":2}"""),
        )
    }

    @Test
    fun aListThatGrewOrShrankDiffsItsLengthAsAdditionsOrRemovals() {
        assertEquals(
            listOf(DiffOp(DiffKind.Added, listOf("tasks", "1"), null, JsonPrimitive(8))),
            diff("""{"tasks":[7]}""", """{"tasks":[7,8]}"""),
        )
        assertEquals(
            listOf(DiffOp(DiffKind.Removed, listOf("tasks", "1"), JsonPrimitive(8), null)),
            diff("""{"tasks":[7,8]}""", """{"tasks":[7]}"""),
        )
    }

    @Test
    fun aValueThatChangedTypeIsOneChange() {
        assertEquals(
            listOf(DiffOp(DiffKind.Changed, listOf("a"), json("[1]"), json("""{"x":1}"""))),
            diff("""{"a":[1]}""", """{"a":{"x":1}}"""),
        )
    }

    @Test
    fun aDiffPathMapsToTheNearestNodeThatExists() {
        val document = json("""{"tasks":[{"title":"a"}],"loading":false}""")
        assertEquals(listOf("tasks", "0", "title"), document.nearestNodePath(listOf("tasks", "0", "title")))
        assertEquals(listOf("tasks"), document.nearestNodePath(listOf("tasks", "3", "title")))
        assertEquals(emptyList(), document.nearestNodePath(listOf("gone", "x")))
        assertEquals(listOf("loading"), document.nearestNodePath(listOf("loading", "deeper")))
    }
}
