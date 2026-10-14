package org.kodein.arch.vie.timemachine.client

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.update
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineTimelineMessage


/**
 * The probe's outbound queue: holds the [TimeMachineSink.Entry]s waiting to be sent, including while
 * no inspector is connected.
 *
 * Bounded to `capacity` entries: once full, [offer] drops the oldest one, so the reporting thread
 * never waits. Drops are counted per ViewModel, and [drain] sends one
 * [TimeMachineClientMessage.ObservationsDropped] per affected ViewModel ahead of the next message.
 *
 * [drain] numbers the messages in the order it sends them, drop reports included: a drop report
 * takes the place of the messages it reports, which leave no gap in the sequence.
 *
 * A sent message stays in flight until the inspector [acknowledge]s it. [drain] waits while more
 * than `window` characters of JSON are in flight, but always admits one message: an inspector or a
 * link that falls behind leaves the entries in the bounded queue, where they drop and are reported,
 * rather than piling up in the transport's own unbounded buffers.
 *
 * A message [drain] fails to send is kept, with its sequence, and sent first by the next [drain],
 * right after the messages still in flight, which it sends again: neither a message nor a drop count
 * is ever lost to a broken connection.
 */
internal class TimeMachineOutbox(capacity: Int, private val window: Int = 256 * 1024) {

    /** A message numbered and encoded, ready to send. */
    private class Encoded(val sequence: TimeMachineSequence, val text: String)

    private val drops = AtomicReference<Map<Uuid, Int>>(emptyMap())

    private val queue = Channel<TimeMachineSink.Entry>(
        capacity = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { entry -> drops.update { it + (entry.viewModelId to (it[entry.viewModelId] ?: 0) + 1) } },
    )

    /** The highest sequence the inspector acknowledged. Written by [acknowledge], read by [drain]. */
    private val acknowledged = MutableStateFlow<TimeMachineSequence?>(null)

    /** The sequence of the next message built. Only touched by [drain], which never runs concurrently
     *  with itself. */
    private var nextSequence = TimeMachineSequence.First

    /** Sent, but not acknowledged yet, oldest first. Only touched by [drain]. */
    private val inFlight = ArrayDeque<Encoded>()

    /** The JSON characters [inFlight] holds. Only touched by [drain]. */
    private var inFlightLength = 0L

    /** Built, and so numbered, but not sent yet, oldest first. Only touched by [drain]. */
    private val unsent = ArrayDeque<Encoded>()

    /** Queues [entry]. Never suspends nor throws; does nothing once [close]d. */
    fun offer(entry: TimeMachineSink.Entry) {
        queue.trySend(entry)
    }

    /**
     * Records that the inspector applied every message up to [sequence], as a
     * [TimeMachineServerMessage.Received] says, and lets [drain] send what it was waiting to.
     * Never suspends: safe to call from the connection's receive loop.
     */
    fun acknowledge(sequence: TimeMachineSequence) {
        acknowledged.update { if (it == null || sequence > it) sequence else it }
    }

    /**
     * Sends the unacknowledged messages again, then every queued entry through [send], as it arrives
     * and as acknowledgements make room, until cancelled or [send] throws. Must not be called
     * concurrently with itself.
     */
    suspend fun drain(send: suspend (text: String) -> Unit): Nothing {
        trim(acknowledged.value)
        inFlight.forEach { send(it.text) }
        while (true) {
            acknowledged.first { trim(it); inFlightLength <= window }
            if (unsent.isEmpty()) build(queue.receive())
            val next = unsent.first()
            send(next.text)
            unsent.removeFirst()
            inFlight += next
            inFlightLength += next.text.length
        }
    }

    /** Numbers [entry], and the drop reports it is the first message after, into [unsent]. */
    private fun build(entry: TimeMachineSink.Entry) {
        // Taken after the entry is received: every drop it may have caused is reported ahead of it.
        drops.exchange(emptyMap()).forEach { (viewModelId, count) ->
            unsent += encode(TimeMachineClientMessage.ObservationsDropped(viewModelId = viewModelId, count = count, at = Clock.System.now(), sequence = nextSequence++))
        }
        unsent += encode(entry.describe(nextSequence++))
    }

    private fun encode(message: TimeMachineTimelineMessage) =
        Encoded(message.sequence, TimeMachineJson.encodeToString(TimeMachineClientMessage.serializer(), message))

    /** Forgets the in-flight messages up to [acknowledged]. */
    private fun trim(acknowledged: TimeMachineSequence?) {
        while (acknowledged != null && inFlight.isNotEmpty() && inFlight.first().sequence <= acknowledged) {
            inFlightLength -= inFlight.removeFirst().text.length
        }
    }

    /** Stops accepting entries and discards those still queued. */
    fun close() {
        queue.cancel()
    }
}
