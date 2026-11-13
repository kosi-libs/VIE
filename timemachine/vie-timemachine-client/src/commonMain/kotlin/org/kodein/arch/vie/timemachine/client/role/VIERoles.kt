package org.kodein.arch.vie.timemachine.client.role

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.update
import kotlin.reflect.KType
import org.kodein.arch.vie.timemachine.client.ProbeLog


/**
 * The [VIERole]s a probe built so far, by type.
 *
 * Roles depend only on their type, and building one is a reflective lookup on the thread that builds
 * the ViewModel: it is done once per type, a type that fails to introspect included, so that it
 * is printed once and not for every ViewModel. Copy-on-write, like the sink's registry.
 */
internal class VIERoles(private val log: ProbeLog) {

    private val roles = AtomicReference<Map<KType, VIERole<*>>>(emptyMap())

    /** The role of [type], built the first time it is asked for. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(type: KType): VIERole<T> {
        roles.load()[type]?.let { return it as VIERole<T> }
        // Two threads may both build it; the roles are equivalent, and the last one stored wins.
        val role = VIERole.of<T>(type, log)
        roles.update { it + (type to role) }
        return role
    }
}
