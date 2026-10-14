package org.kodein.arch.vie.timemachine.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


/**
 * Encodes a [TimeMachineServerMessage.Rejected.Reason] by name, and decodes a name it does not know
 * as [TimeMachineServerMessage.Rejected.Reason.Unknown].
 *
 * `Rejected` is a frozen envelope: a client must be able to read it whatever reason a later server
 * refuses it with, so the enum cannot be closed.
 */
internal object ReasonSerializer : KSerializer<TimeMachineServerMessage.Rejected.Reason> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.kodein.arch.vie.timemachine.protocol.Rejected.Reason", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TimeMachineServerMessage.Rejected.Reason) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): TimeMachineServerMessage.Rejected.Reason {
        val name = decoder.decodeString()
        return TimeMachineServerMessage.Rejected.Reason.entries.firstOrNull { it.name == name }
            ?: TimeMachineServerMessage.Rejected.Reason.Unknown
    }
}
