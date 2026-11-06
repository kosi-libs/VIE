package org.kodein.arch.vie.timemachine.client

import kotlin.reflect.typeOf
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo


/** For a ViewModel no test intervenes in. */
internal val noIntervention = TimeMachineIntervener { error("No intervention expected") }

/** The description of a ViewModel [id], whose three roles are all the same non-serializable type. */
internal fun info(id: Uuid) = VIETypeInfo("T", isSerializable = false).let { VIEViewModelInfo(id, "VM", it, it, it) }

/** The [VIERole] of [T]. */
internal inline fun <reified T> role(): VIERole<T> = VIERole.of(typeOf<T>())
