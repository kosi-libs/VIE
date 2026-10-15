package org.kodein.arch.vie.timemachine.server.ui.common

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject


/** Where a node is in a JSON document: object keys and array indices (as text), from the root. */
typealias JsonPath = List<String>

/** How a field differs between two JSON documents, with the symbol the `Δ` line shows. */
enum class DiffKind(val symbol: String) {
    Changed("~"),
    Added("+"),
    Removed("−"),
}

/**
 * One difference between two documents. [old] is `null` for an added field, [new] for a removed one.
 */
data class DiffOp(val kind: DiffKind, val path: JsonPath, val old: JsonElement?, val new: JsonElement?)

/**
 * The field-level differences going from [old] to [new]: a changed value (or one that changed type)
 * is [DiffKind.Changed]; an added or removed key or array element is [DiffKind.Added] or
 * [DiffKind.Removed], so a list that grew by two has two additions.
 */
fun diffJson(old: JsonElement, new: JsonElement): List<DiffOp> = buildList { diffInto(emptyList(), old, new) }

private fun MutableList<DiffOp>.diffInto(path: JsonPath, old: JsonElement, new: JsonElement) {
    when {
        old == new -> Unit
        old is JsonObject && new is JsonObject -> {
            for ((key, value) in old) {
                val other = new[key]
                if (other == null) add(DiffOp(DiffKind.Removed, path + key, value, null)) else diffInto(path + key, value, other)
            }
            for ((key, value) in new) if (key !in old) add(DiffOp(DiffKind.Added, path + key, null, value))
        }
        old is JsonArray && new is JsonArray -> {
            val common = minOf(old.size, new.size)
            for (index in 0 until common) diffInto(path + index.toString(), old[index], new[index])
            for (index in common until new.size) add(DiffOp(DiffKind.Added, path + index.toString(), null, new[index]))
            for (index in common until old.size) add(DiffOp(DiffKind.Removed, path + index.toString(), old[index], null))
        }
        else -> add(DiffOp(DiffKind.Changed, path, old, new))
    }
}

/**
 * The node of this document a diff at [path] maps to: [path] itself when that node exists, else its
 * nearest ancestor that does (a removed field is not in the document being shown).
 */
fun JsonElement.nearestNodePath(path: JsonPath): JsonPath {
    var node = this
    val walked = ArrayList<String>()
    for (segment in path) {
        val current = node
        node = when (current) {
            is JsonObject -> current[segment]
            is JsonArray -> segment.toIntOrNull()?.let { current.getOrNull(it) }
            else -> null
        } ?: break
        walked += segment
    }
    return walked
}
