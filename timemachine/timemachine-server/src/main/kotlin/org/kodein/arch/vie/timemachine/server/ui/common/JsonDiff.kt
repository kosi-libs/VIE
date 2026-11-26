package org.kodein.arch.vie.timemachine.server.ui.common

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.server.store.Row
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.store.ViewModelRecord
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.uuid.Uuid


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

/** What a Model row shows as `Δ`: the differences from the Model observed [previous]. */
internal data class ModelDiff(val previous: TimeMachineSequence, val ops: List<DiffOp>)

/**
 * The differences of the Model observed at [sequence] in [vm] from the Model observed before it, among
 * the rows [vm] retains: `null` when there is none, because none was observed or it was evicted. It has
 * no op when either of the two came without its JSON. The Model at [sequence] is its row, or
 * [ViewModelRecord.currentModel] when that one was never placed for being too heavy.
 */
internal fun modelDiff(vm: ViewModelRecord, sequence: TimeMachineSequence): ModelDiff? =
    vm.previousModel(sequence)?.let { diffFrom(vm, it, sequence) }

private fun diffFrom(vm: ViewModelRecord, previous: Row, sequence: TimeMachineSequence): ModelDiff {
    val old = previous.observation?.asJson
    val new = (vm.row(sequence)?.observation ?: vm.currentModel?.takeIf { it.sequence == sequence }?.value)?.asJson
    return ModelDiff(previous.sequence, if (old != null && new != null) diffJson(old, new) else emptyList())
}

private val Row.observation get() = (message as? TimeMachineClientMessage.Observed)?.observation

/**
 * The [modelDiff]s the panes ask for, one cache for all of them: the Timeline, the Detail tab and the
 * pinned Model show the same diffs, computed once by whichever pane asks first. It lives as long as its
 * inspector.
 *
 * An entry is a diff computed against one previous Model, given back only while that one is still the
 * previous Model of its row in the record asked about: what a pane shows is always [modelDiff] of the
 * state it projects, and a previous Model evicted since, or a late one placed between the two, is never
 * hidden by an older entry. [retain] drops the entries a state no longer needs; each projection that
 * reads the cache calls it first.
 *
 * The projections of several panes run at once on the compute dispatcher, so it is thread-safe. Two that
 * miss the same diff both compute it, and the last one is kept.
 */
internal class ModelDiffs {

    /** By ViewModel, then by the sequence of the Model's row. */
    private val byViewModel = ConcurrentHashMap<Uuid, ConcurrentSkipListMap<TimeMachineSequence, ModelDiff>>()

    /** [modelDiff] of [vm] at [sequence], from the cache while it holds. */
    fun of(vm: ViewModelRecord, sequence: TimeMachineSequence): ModelDiff? {
        val previous = vm.previousModel(sequence) ?: return null
        val diffs = byViewModel.getOrPut(vm.id) { ConcurrentSkipListMap() }
        diffs[sequence]?.takeIf { it.previous == previous.sequence }?.let { return it }
        return diffFrom(vm, previous, sequence).also { diffs[sequence] = it }
    }

    /**
     * Drops the entries [state] no longer needs: all those of a ViewModel it does not hold or that retains
     * no row, and those of the rows its bounds evicted. Bounds evict a ViewModel's oldest rows first, so
     * these are the entries before its oldest retained row.
     */
    fun retain(state: TimeMachineState) {
        val held = HashMap<Uuid, ViewModelRecord>()
        for (session in state.sessions.values) held.putAll(session.viewModels)
        byViewModel.entries.removeIf { (id, diffs) ->
            val oldest = held[id]?.rows?.firstOrNull()?.sequence
            if (oldest != null) diffs.headMap(oldest).clear()
            oldest == null
        }
    }

    /** The sequences of the rows of ViewModel [viewModelId] whose diff is kept. */
    fun cachedRows(viewModelId: Uuid): Set<TimeMachineSequence> = byViewModel[viewModelId]?.keys?.toSet().orEmpty()
}
