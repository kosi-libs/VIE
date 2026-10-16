package org.kodein.arch.vie.timemachine.protocol

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable


/**
 * Version of the TimeMachine wire protocol, as carried by [TimeMachineClientMessage.Hello] and
 * checked against [TimeMachineConnection.protocolVersion].
 *
 * Every [Int] is representable: this type does not validate its [value], because
 * kotlinx-serialization runs constructor `init` blocks while decoding, and a rejected value must
 * decode cleanly so the server can answer with
 * [TimeMachineServerMessage.Rejected] rather than throw out of the decode step. A version is
 * speakable when it equals [TimeMachineConnection.protocolVersion].
 */
@Serializable
@JvmInline
public value class TimeMachineProtocolVersion(public val value: Int) {

    override fun toString(): String = value.toString()
}
