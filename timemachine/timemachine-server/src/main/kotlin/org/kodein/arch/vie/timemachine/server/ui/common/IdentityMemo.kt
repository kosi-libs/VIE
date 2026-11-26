package org.kodein.arch.vie.timemachine.server.ui.common


/**
 * Remembers what was derived from the last key asked, for as long as that very instance is asked for again.
 * It is meant for what is costly to derive from a large immutable value that a state carries unchanged
 * from one publication to the next, such as a JSON document and its text: the key is compared by identity
 * (`===`), never by `==`, which would walk the whole document to say what an identity check says at once.
 *
 * It remembers one key only: asked for another, it derives again and forgets the first. It can be asked from
 * several threads at once. Two that miss at the same time both derive, and the last one is kept, so what is
 * derived must have no effect but its result.
 *
 * What is derived is given at each call rather than once, because it can depend on more than the key: the
 * changed nodes of a Model depend on its document and on its diff, and the diff, which is unique to the
 * document, is the key.
 */
internal class IdentityMemo<K : Any, V> {

    private class Entry<K, V>(val key: K, val value: V)

    @Volatile
    private var last: Entry<K, V>? = null

    /** What [compute] derives from [key], from the memory if [key] is the instance asked for last. */
    fun get(key: K, compute: (K) -> V): V {
        last?.let { if (it.key === key) return it.value }
        return compute(key).also { last = Entry(key, it) }
    }
}
