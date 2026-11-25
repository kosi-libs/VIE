package org.kodein.arch.vie.timemachine.server.ui.common


/**
 * The value [compute] gives for the last key asked, kept for as long as that very instance is asked for again.
 * It is meant for what is costly to derive from a large immutable value that a state carries unchanged
 * from one publication to the next, such as a JSON document and its text: the key is compared by identity
 * (`===`), never by `==`, which would walk the whole document to say what an identity check says at once.
 *
 * It remembers one key only: asked for another, it computes again and forgets the first. It can be asked from
 * several threads at once. Two that miss at the same time both compute, and the last one is kept, so [compute]
 * must have no effect but its result.
 */
internal class IdentityMemo<K : Any, V>(private val compute: (K) -> V) {

    private class Entry<K, V>(val key: K, val value: V)

    @Volatile
    private var last: Entry<K, V>? = null

    /** The value of [key], from the memory if it is the key asked for last, else computed and kept. */
    fun get(key: K): V {
        last?.let { if (it.key === key) return it.value }
        return compute(key).also { last = Entry(key, it) }
    }
}
