package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession


@OptIn(ExperimentalCoroutinesApi::class)
class TimeMachineStoreTest {

    private val c1 = ConnectionId(1)
    private val session = TimeMachineSession(Uuid.random(), "App")

    @Test
    fun eventsPostedConcurrentlyAreAllAppliedEachSendersInItsOwnOrder() = runBlocking {
        val senders = 8
        val frames = 200
        val scope = CoroutineScope(Dispatchers.Default)
        val store = TimeMachineStore(scope) {}
        try {
            val sessions = List(senders) { TimeMachineSession(Uuid.random(), "App $it") }
            // Each frame names a ViewModel of its own: first-seen order is the order frames were applied.
            val viewModels = List(senders) { List(frames) { Uuid.random() } }

            List(senders) { sender ->
                launch(Dispatchers.Default) {
                    val connection = ConnectionId(sender.toLong())
                    store.post(hello(connection, sessions[sender]))
                    viewModels[sender].forEachIndexed { index, vm ->
                        store.post(receive(connection, observed(vm, index.toLong())))
                        if (index % 16 == 0) yield()
                    }
                    store.post(TimeMachineEvent.Disconnected(connection, t0))
                }
            }.joinAll()

            val state = withTimeout(10.seconds) {
                store.state.first { state -> state.sessions.size == senders && state.sessions.values.none { it.isConnected } }
            }
            sessions.forEachIndexed { sender, session ->
                val record = state.session(session.id)
                // Every frame was applied, and before the disconnect: an unmapped connection's frames are ignored.
                assertEquals(viewModels[sender], record.viewModels.keys.toList())
                assertEquals(SequenceLedger(floor = seq(0), highest = seq(frames - 1L)), record.sequences)
            }
            assertEquals(emptyMap(), state.connections)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aThrowingExecuteStopsNeitherItsBatchNorTheWriter() = runTest {
        val executed = mutableListOf<TransportCommand>()
        val store = TimeMachineStore(backgroundScope) { command ->
            if (command is Command.Send && command.message == TimeMachineServerMessage.Welcome) error("injected")
            executed += command
        }
        val a = Uuid.random()
        val b = Uuid.random()
        // One batch: the failed Welcome comes before the acknowledgement.
        store.post(hello(c1, session))
        store.post(receive(c1, observed(a, 0)))
        runCurrent()
        store.post(receive(c1, observed(b, 1)))
        runCurrent()

        assertEquals(listOf(a, b), store.state.value.session(session.id).viewModels.keys.toList())
        assertEquals(
            listOf(TimeMachineServerMessage.Received(seq(0)), TimeMachineServerMessage.Received(seq(1))),
            executed.filterIsInstance<Command.Send>().map { it.message }.filterIsInstance<TimeMachineServerMessage.Received>(),
        )
    }

    @Test
    fun anEventTheCoreThrowsOnIsSkippedAndTheRestOfItsBatchAppliedAndAcknowledged() = runTest {
        val poison = Uuid.random()
        val executed = mutableListOf<TransportCommand>()
        val store = TimeMachineStore(
            backgroundScope,
            reduce = { events ->
                if (events.any { ((it as? TimeMachineEvent.MessageReceived)?.message as? TimeMachineClientMessage.Observed)?.observation?.viewModelId == poison }) error("injected")
                on(events)
            },
        ) { executed += it }
        val a = Uuid.random()
        val b = Uuid.random()
        val c = Uuid.random()
        store.post(hello(c1, session))
        store.post(receive(c1, observed(a, 0)))
        store.post(receive(c1, observed(poison, 1)))
        store.post(receive(c1, observed(b, 2)))
        runCurrent()

        val record = store.state.value.session(session.id)
        assertEquals(listOf(a, b), record.viewModels.keys.toList())
        assertEquals(SequenceLedger(floor = seq(0), highest = seq(2), missing = listOf(seqs(1, 1))), record.sequences)
        assertEquals(TimeMachineServerMessage.Received(seq(2)), (executed.last() as Command.Send).message)

        store.post(receive(c1, observed(c, 3)))
        runCurrent()
        assertEquals(listOf(a, b, c), store.state.value.session(session.id).viewModels.keys.toList())
        assertFalse(poison in store.state.value.session(session.id).viewModels)
    }
}
