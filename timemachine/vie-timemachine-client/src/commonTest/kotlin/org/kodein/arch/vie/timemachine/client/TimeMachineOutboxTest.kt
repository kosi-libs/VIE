package org.kodein.arch.vie.timemachine.client

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.protocol.TimeMachineTimelineMessage
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation


/** An entry about [viewModelId] that describes a model as [text], calling [onDescribe] when it does. */
private fun entry(viewModelId: Uuid, text: String, onDescribe: () -> Unit = {}) = TimeMachineSink.Entry(viewModelId) { sequence ->
    onDescribe()
    val observation = VIEObservation(viewModelId = viewModelId, at = Clock.System.now(), kind = VIEObjectKind.Model, typeName = "T", asString = text)
    TimeMachineClientMessage.Observed(observation = observation, sequence = sequence)
}

private val TimeMachineTimelineMessage.asString: String?
    get() = (this as? TimeMachineClientMessage.Observed)?.observation?.asString

private fun decode(text: String) = TimeMachineJson.decodeFromString(TimeMachineClientMessage.serializer(), text) as TimeMachineTimelineMessage

@OptIn(ExperimentalCoroutinesApi::class)
class TimeMachineOutboxTest {

    private val vmA = Uuid.random()
    private val vmB = Uuid.random()

    /** Drains [outbox] into a list until [block] returns. */
    private suspend fun TestScope.draining(
        outbox: TimeMachineOutbox,
        block: suspend (sent: List<TimeMachineTimelineMessage>) -> Unit,
    ) {
        val sent = mutableListOf<TimeMachineTimelineMessage>()
        val drain = launch { outbox.drain { sent += decode(it) } }
        advanceUntilIdle()
        block(sent)
        drain.cancelAndJoin()
    }

    @Test
    fun anEntryIsDescribedWhenDrainedNotWhenOffered() = runTest {
        val outbox = TimeMachineOutbox(capacity = 8)
        var described = 0

        outbox.offer(entry(vmA, "0") { described++ })
        assertEquals(0, described)

        draining(outbox) { sent ->
            assertEquals(1, described)
            assertEquals(listOf("0"), sent.map { it.asString })
        }
    }

    @Test
    fun aFullQueueDropsTheOldestAndReportsDropsPerViewModelAheadOfTheNextMessage() = runTest {
        val outbox = TimeMachineOutbox(capacity = 2)

        outbox.offer(entry(vmA, "0")) // dropped
        outbox.offer(entry(vmB, "1")) // dropped
        outbox.offer(entry(vmA, "2")) // dropped
        outbox.offer(entry(vmA, "3"))
        outbox.offer(entry(vmB, "4"))

        draining(outbox) { sent ->
            val drops = sent.take(2).map { assertIs<TimeMachineClientMessage.ObservationsDropped>(it) }
            assertEquals(mapOf(vmA to 2, vmB to 1), drops.associate { it.viewModelId to it.count })
            assertEquals(listOf("3", "4"), sent.drop(2).map { it.asString })
            // The drop reports take the dropped messages' place: no sequence is missing.
            assertEquals(listOf(0L, 1L, 2L, 3L), sent.map { it.sequence.value })
        }
    }

    @Test
    fun aMessageThatFailsToSendIsSentFirstNextTimeWithTheSameSequence() = runTest {
        val outbox = TimeMachineOutbox(capacity = 8)
        outbox.offer(entry(vmA, "0"))
        outbox.offer(entry(vmA, "1"))

        val failed = CompletableDeferred<Throwable>()
        val broken = launch {
            runCatching { outbox.drain { throw IllegalStateException("connection lost") } }
                .onFailure { failed.complete(it) }
        }
        advanceUntilIdle()
        assertIs<IllegalStateException>(failed.await())
        broken.join()

        outbox.offer(entry(vmA, "2"))
        draining(outbox) { sent ->
            assertEquals(listOf("0", "1", "2"), sent.map { it.asString })
            assertEquals(listOf(0L, 1L, 2L), sent.map { it.sequence.value })
        }
    }

    @Test
    fun aDropReportThatFailsToSendIsNeverLost() = runTest {
        val outbox = TimeMachineOutbox(capacity = 1)
        outbox.offer(entry(vmA, "0")) // dropped
        outbox.offer(entry(vmA, "1"))

        val broken = launch { runCatching { outbox.drain { error("connection lost") } } }
        advanceUntilIdle()
        broken.join()

        draining(outbox) { sent ->
            val drop = assertIs<TimeMachineClientMessage.ObservationsDropped>(sent.first())
            assertEquals(vmA to 1, drop.viewModelId to drop.count)
            assertEquals(listOf("1"), sent.drop(1).map { it.asString })
            assertEquals(1, sent.count { it is TimeMachineClientMessage.ObservationsDropped })
        }
    }

    @Test
    fun withNoAcknowledgementOnlyOneWindowIsSent() = runTest {
        val outbox = TimeMachineOutbox(capacity = 8, window = 0)
        outbox.offer(entry(vmA, "0"))
        outbox.offer(entry(vmA, "1"))

        draining(outbox) { sent ->
            // A window of 0 still admits one message.
            assertEquals(listOf("0"), sent.map { it.asString })
            outbox.acknowledge(TimeMachineSequence(0))
            advanceUntilIdle()
            assertEquals(listOf("0", "1"), sent.map { it.asString })
        }
    }

    @Test
    fun whatAClosedWindowHoldsBackDropsAndIsReportedOnceAcknowledged() = runTest {
        val outbox = TimeMachineOutbox(capacity = 2, window = 0)
        outbox.offer(entry(vmA, "0"))

        draining(outbox) { sent ->
            outbox.offer(entry(vmA, "1")) // dropped
            outbox.offer(entry(vmB, "2")) // dropped
            outbox.offer(entry(vmA, "3"))
            outbox.offer(entry(vmB, "4"))
            advanceUntilIdle()
            assertEquals(listOf("0"), sent.map { it.asString })

            // Each acknowledgement releases the next message, the drop reports first.
            outbox.acknowledge(TimeMachineSequence(0))
            advanceUntilIdle()
            assertIs<TimeMachineClientMessage.ObservationsDropped>(sent.last())
            repeat(3) { outbox.acknowledge(sent.last().sequence); advanceUntilIdle() }

            val drops = sent.subList(1, 3).map { assertIs<TimeMachineClientMessage.ObservationsDropped>(it) }
            assertEquals(mapOf(vmA to 1, vmB to 1), drops.associate { it.viewModelId to it.count })
            assertEquals(listOf("3", "4"), sent.drop(3).map { it.asString })
            assertEquals(listOf(0L, 1L, 2L, 3L, 4L), sent.map { it.sequence.value })
        }
    }

    @Test
    fun aWindowCountsTheJsonInFlight() = runTest {
        val outbox = TimeMachineOutbox(capacity = 8, window = 10)
        outbox.offer(entry(vmA, "0"))
        outbox.offer(entry(vmA, "1"))
        outbox.offer(entry(vmA, "2"))

        draining(outbox) { sent ->
            // A single message already holds more than 10 characters of JSON.
            assertEquals(listOf("0"), sent.map { it.asString })
        }

        val large = TimeMachineOutbox(capacity = 8, window = 1024 * 1024)
        repeat(3) { large.offer(entry(vmA, "$it")) }
        draining(large) { sent -> assertEquals(listOf("0", "1", "2"), sent.map { it.asString }) }
    }

    @Test
    fun theNextDrainSendsTheUnacknowledgedMessagesAgainWithTheirSequences() = runTest {
        val outbox = TimeMachineOutbox(capacity = 8)
        outbox.offer(entry(vmA, "0"))
        outbox.offer(entry(vmA, "1"))
        outbox.offer(entry(vmA, "2"))

        draining(outbox) { sent ->
            assertEquals(listOf(0L, 1L, 2L), sent.map { it.sequence.value })
            outbox.acknowledge(TimeMachineSequence(0))
        }

        outbox.offer(entry(vmA, "3"))
        draining(outbox) { sent ->
            assertEquals(listOf("1", "2", "3"), sent.map { it.asString })
            assertEquals(listOf(1L, 2L, 3L), sent.map { it.sequence.value })
        }
    }

    @Test
    fun anOlderAcknowledgementChangesNothing() = runTest {
        val outbox = TimeMachineOutbox(capacity = 8)
        outbox.offer(entry(vmA, "0"))
        outbox.offer(entry(vmA, "1"))

        draining(outbox) { outbox.acknowledge(TimeMachineSequence(1)); outbox.acknowledge(TimeMachineSequence(0)) }

        draining(outbox) { sent -> assertEquals(emptyList(), sent) }
    }

    @Test
    fun aClosedOutboxIgnoresNewEntries() = runTest {
        val outbox = TimeMachineOutbox(capacity = 8)
        outbox.close()

        outbox.offer(entry(vmA, "0"))
    }
}
