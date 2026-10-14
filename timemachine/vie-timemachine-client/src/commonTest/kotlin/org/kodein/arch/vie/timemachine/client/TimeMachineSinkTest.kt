package org.kodein.arch.vie.timemachine.client

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery


private class Counted(val n: Int) {
    var described = 0
    override fun toString(): String {
        described++
        return "Counted($n)"
    }
}

/** Its `toString()` throws [failure], as a deeply nested model's would throw a `StackOverflowError`. */
private class FailingToString(val failure: () -> Throwable) {
    override fun toString(): String = throw failure()
}

@Serializable
private sealed interface DeliverySignal : VIE.Signal {
    @Serializable
    data object Broadcast : DeliverySignal, VIE.Signal.Event

    @Serializable
    data object Targeted : DeliverySignal, VIE.Signal.Effect

    /** Picked neither delivery kind. */
    @Serializable
    data object Untyped : DeliverySignal
}

private object ErrorSerializer : KSerializer<ErrorModel> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ErrorModel", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: ErrorModel) = throw Error("out of memory")
    override fun deserialize(decoder: Decoder): ErrorModel = throw Error("out of memory")
}

@Serializable(with = ErrorSerializer::class)
private data class ErrorModel(val count: Int)

class TimeMachineSinkTest {

    private val vmA = Uuid.random()
    private val vmB = Uuid.random()

    @Test
    fun anObservedObjectIsDescribedByItsEntryNotWhenReported() {
        val entries = mutableListOf<TimeMachineSink.Entry>()
        val sink = TimeMachineSink { entries += it }
        val value = Counted(1)

        sink.observed(vmA, VIEObjectKind.Model, role<Counted>(), value)
        assertEquals(0, value.described)

        val observed = assertIs<TimeMachineClientMessage.Observed>(entries.single().describe(sequence = TimeMachineSequence(7)))
        assertEquals(1, value.described)
        assertEquals("Counted(1)" to 7L, observed.observation.asString to observed.sequence.value)
    }

    /** Reports [value] and describes it right away. */
    private fun <T : Any> describe(role: VIERole<T>, value: T): TimeMachineClientMessage.Observed {
        val entries = mutableListOf<TimeMachineSink.Entry>()
        TimeMachineSink { entries += it }.observed(vmA, VIEObjectKind.Model, role, value)
        return assertIs<TimeMachineClientMessage.Observed>(entries.single().describe(sequence = TimeMachineSequence(0)))
    }

    @Test
    fun aSignalIsDescribedWithTheDeliveryItsTypePicked() {
        fun delivery(signal: DeliverySignal): VIESignalDelivery? {
            val entries = mutableListOf<TimeMachineSink.Entry>()
            TimeMachineSink { entries += it }.observed(vmA, VIEObjectKind.Signal, role<DeliverySignal>(), signal)
            return assertIs<TimeMachineClientMessage.Observed>(entries.single().describe(TimeMachineSequence(0))).observation.delivery
        }

        assertEquals(VIESignalDelivery.Event, delivery(DeliverySignal.Broadcast))
        assertEquals(VIESignalDelivery.Effect, delivery(DeliverySignal.Targeted))
        assertNull(delivery(DeliverySignal.Untyped))
    }

    @Test
    fun aModelAndAnIntentCarryNoDelivery() {
        assertNull(describe(role<Counted>(), Counted(1)).observation.delivery)
        val entries = mutableListOf<TimeMachineSink.Entry>()
        TimeMachineSink { entries += it }.observed(vmA, VIEObjectKind.Intent, role<Counted>(), Counted(2))
        assertNull(assertIs<TimeMachineClientMessage.Observed>(entries.single().describe(TimeMachineSequence(0))).observation.delivery)
    }

    @Test
    fun anErrorFromToStringIsAFailureToDescribe() {
        val observed = describe(role<FailingToString>(), FailingToString { Error("too deep") })
        assertTrue(observed.observation.asString.startsWith("<toString() failed:"), observed.observation.asString)
    }

    @Test
    fun aCancellationFromToStringIsAFailureToDescribe() {
        val observed = describe(role<FailingToString>(), FailingToString { CancellationException("from the app") })
        assertTrue(observed.observation.asString.startsWith("<toString() failed:"), observed.observation.asString)
    }

    @Test
    fun anErrorFromTheSerializerLeavesOnlyTheJsonOut() {
        val observed = describe(role<ErrorModel>(), ErrorModel(1))
        assertEquals("ErrorModel(count=1)", observed.observation.asString)
        assertNull(observed.observation.asJson)
    }

    @Test
    fun theSinkKnowsWhichViewModelsAreAlive() {
        val sink = TimeMachineSink {}
        sink.started(info(vmA), noIntervention)
        sink.started(info(vmB), noIntervention)
        sink.closed(vmA)

        assertEquals(mapOf(vmB to info(vmB)), sink.liveViewModels.mapValues { it.value.info })
    }
}
