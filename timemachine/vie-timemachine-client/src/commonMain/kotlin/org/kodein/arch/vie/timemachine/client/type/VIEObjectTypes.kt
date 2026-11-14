package org.kodein.arch.vie.timemachine.client.type

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.update
import kotlin.reflect.KType
import org.kodein.arch.vie.timemachine.client.ProbeLog


/**
 * The [VIEObjectType]s a probe built so far, by type.
 *
 * One depends only on its type, and building it is a reflective lookup on the thread that builds
 * the ViewModel: it is done once per type, a type that fails to introspect included, so that it
 * is printed once and not for every ViewModel. Copy-on-write, like the sink's registry.
 */
internal class VIEObjectTypes(private val log: ProbeLog) {

    private val types = AtomicReference<Map<KType, VIEObjectType<*>>>(emptyMap())

    /** The [VIEObjectType] of [type], built the first time it is asked for. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(type: KType): VIEObjectType<T> {
        types.load()[type]?.let { return it as VIEObjectType<T> }
        // Two threads may both build it; what they build is equivalent, and the last one stored wins.
        val introspected = VIEObjectType.of<T>(type, log)
        types.update { it + (type to introspected) }
        return introspected
    }
}
