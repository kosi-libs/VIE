package org.kodein.arch.vie.timemachine.protocol

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable


/**
 * The position of a [TimeMachineTimelineMessage] in its session's timeline, as carried by
 * [TimeMachineTimelineMessage.sequence] and acknowledged by [TimeMachineServerMessage.Received].
 *
 * It is the only order the inspector trusts, and a type of its own so that it can't be mixed up
 * with the counts (rows, drops, characters) it travels next to. It encodes as the bare number.
 *
 * Every [Long] is representable: this type does not validate its [value], because
 * kotlinx-serialization runs constructor `init` blocks while decoding, and decoding must not throw.
 *
 * The only arithmetic is the neighbour: a position plus a count means nothing.
 */
@Serializable
@JvmInline
public value class TimeMachineSequence(public val value: Long) : Comparable<TimeMachineSequence> {

    /** The position right after this one. */
    public operator fun inc(): TimeMachineSequence = TimeMachineSequence(value + 1)

    /** The position right before this one. */
    public operator fun dec(): TimeMachineSequence = TimeMachineSequence(value - 1)

    override fun compareTo(other: TimeMachineSequence): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()

    public companion object {
        /** The position of a session's first message. */
        public val First: TimeMachineSequence = TimeMachineSequence(0)
    }
}
