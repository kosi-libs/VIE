package org.kodein.arch.vie.timemachine.client.fixtures

import kotlin.reflect.typeOf
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.client.ProbeLog
import org.kodein.arch.vie.timemachine.client.type.VIEObjectType
import org.kodein.arch.vie.timemachine.client.timeline.TimeMachineIntervener
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo


/** For a ViewModel no test intervenes in. */
internal val noIntervention = TimeMachineIntervener { error("No intervention expected") }

/** For a ViewModel that is garbage-collected, without having been cleared, once [collected] says so. */
internal fun collectedOnce(collected: () -> Boolean) = object : TimeMachineIntervener {
    override val isViewModelCollected: Boolean get() = collected()
    override fun intervene(request: TimeMachineServerMessage.Intervene): VIEInterventionResult = error("No intervention expected")
}

/** The description of a ViewModel [id], whose three types are all the same non-serializable one. */
internal fun info(id: Uuid) = VIETypeInfo("T", isSerializable = false).let { VIEViewModelInfo(id, "VM", it, it, it) }

/** Prints what the probe says, for a test that does not read it. */
internal val testLog = ProbeLog(::println)

/** The [VIEObjectType] of [T]. */
internal inline fun <reified T> objectType(): VIEObjectType<T> = VIEObjectType.of(typeOf<T>(), testLog)
