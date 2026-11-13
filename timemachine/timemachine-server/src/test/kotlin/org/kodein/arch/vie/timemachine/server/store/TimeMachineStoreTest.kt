package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind


@OptIn(ExperimentalCoroutinesApi::class)
class TimeMachineStoreTest {

    private val c1 = ConnectionId(1)
    private val session = TimeMachineSession(Uuid.random(), "App")

    /** A core that throws on any batch holding a frame observed on [poison], and otherwise applies it as it is. */
    private fun throwingOn(poison: Uuid, failure: () -> Throwable = { IllegalStateException("injected") }): TimeMachineState.(List<TimeMachineEvent>) -> Transition =
        { events ->
            if (events.any { ((it as? TimeMachineEvent.MessageReceived)?.message as? TimeMachineClientMessage.Observed)?.observation?.viewModelId == poison }) throw failure()
            on(events)
        }

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
            reduce = throwingOn(poison),
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
        // Each frame is acknowledged on its own, right after its commands.
        assertSends(
            c1,
            listOf(
                TimeMachineServerMessage.Welcome,
                TimeMachineServerMessage.RequestViewModelInfo(a),
                TimeMachineServerMessage.Received(seq(0)),
                TimeMachineServerMessage.Received(seq(1)),
                TimeMachineServerMessage.RequestViewModelInfo(b),
                TimeMachineServerMessage.Received(seq(2)),
            ),
            executed,
        )

        store.post(receive(c1, observed(c, 3)))
        runCurrent()
        assertEquals(listOf(a, b, c), store.state.value.session(session.id).viewModels.keys.toList())
        assertFalse(poison in store.state.value.session(session.id).viewModels)
        assertSends(
            c1,
            listOf(TimeMachineServerMessage.RequestViewModelInfo(c), TimeMachineServerMessage.Received(seq(3))),
            executed.drop(6),
        )
    }

    @Test
    fun aTimelineFrameTheCoreThrowsOnIsAcknowledgedSoItsProbeIsNotStalled() = runTest {
        val poison = Uuid.random()
        val executed = mutableListOf<TransportCommand>()
        val store = TimeMachineStore(
            backgroundScope,
            reduce = throwingOn(poison),
        ) { executed += it }
        val a = Uuid.random()
        store.post(hello(c1, session))
        store.post(receive(c1, observed(a, 0)))
        runCurrent()
        // The probe sends nothing more until this one is acknowledged.
        store.post(receive(c1, observed(poison, 1)))
        runCurrent()

        assertSends(
            c1,
            listOf(TimeMachineServerMessage.Welcome, TimeMachineServerMessage.RequestViewModelInfo(a), TimeMachineServerMessage.Received(seq(0)), TimeMachineServerMessage.Received(seq(1))),
            executed,
        )
        val record = store.state.value.session(session.id)
        assertEquals(listOf(a), record.viewModels.keys.toList())
        assertEquals(SequenceLedger(floor = seq(0), highest = seq(1), missing = listOf(seqs(1, 1))), record.sequences)
    }

    @Test
    fun anInterventionInABatchTheCoreThrowsOnIsSentAndTimedOnce() = runTest {
        val poison = Uuid.random()
        val a = Uuid.random()
        val executed = mutableListOf<TransportCommand>()
        val deadlines = mutableListOf<Uuid>()
        val throwing = throwingOn(poison)
        val store = TimeMachineStore(
            backgroundScope,
            interventionTimeout = 5.seconds,
            reduce = { events ->
                deadlines += events.filterIsInstance<TimeMachineEvent.DeadlinePassed>().map { it.requestId }
                throwing(events)
            },
        ) { executed += it }
        store.post(hello(c1, session))
        store.post(receive(c1, started(a, 0)))
        runCurrent()
        // The batch is replayed event by event: the intervention must be sent, and its deadline started, once.
        store.post(receive(c1, observed(poison, 1)))
        val request = store.intervene(session.id, a, VIEObjectKind.Intent, "I", JsonPrimitive(1))
        store.post(receive(c1, observed(a, 2)))
        runCurrent()

        assertSends(
            c1,
            listOf(
                TimeMachineServerMessage.Welcome,
                TimeMachineServerMessage.Received(seq(0)),
                TimeMachineServerMessage.Received(seq(1)),
                TimeMachineServerMessage.Intervene(request, a, VIEObjectKind.Intent, "I", JsonPrimitive(1)),
                TimeMachineServerMessage.Received(seq(2)),
            ),
            executed,
        )
        assertEquals(InterventionStatus.Pending, store.state.value.vm(session.id, a).interventions.getValue(request).status)

        advanceTimeBy(5.seconds)
        runCurrent()

        assertEquals(listOf(request), deadlines)
        assertEquals(InterventionStatus.TimedOut, store.state.value.vm(session.id, a).interventions.getValue(request).status)
    }

    @Test
    fun anErrorTheCoreThrowsStopsNeitherItsBatchNorTheWriter() = runTest {
        val poison = Uuid.random()
        val executed = mutableListOf<TransportCommand>()
        val store = TimeMachineStore(
            backgroundScope,
            reduce = throwingOn(poison) { NotImplementedError("injected") },
        ) { executed += it }
        val a = Uuid.random()
        val b = Uuid.random()
        store.post(hello(c1, session))
        store.post(receive(c1, observed(a, 0)))
        store.post(receive(c1, observed(poison, 1)))
        store.post(receive(c1, observed(b, 2)))
        runCurrent()

        val record = store.state.value.session(session.id)
        assertEquals(listOf(a, b), record.viewModels.keys.toList())
        assertEquals(SequenceLedger(floor = seq(0), highest = seq(2), missing = listOf(seqs(1, 1))), record.sequences)
        assertSends(
            c1,
            listOf(
                TimeMachineServerMessage.Welcome,
                TimeMachineServerMessage.RequestViewModelInfo(a),
                TimeMachineServerMessage.Received(seq(0)),
                TimeMachineServerMessage.Received(seq(1)),
                TimeMachineServerMessage.RequestViewModelInfo(b),
                TimeMachineServerMessage.Received(seq(2)),
            ),
            executed,
        )
        assertEquals(WriterState.Running, store.state.value.writer)
    }

    @Test
    fun anErrorExecuteThrowsStopsNeitherItsBatchNorTheWriter() = runTest {
        val executed = mutableListOf<TransportCommand>()
        val store = TimeMachineStore(backgroundScope) { command ->
            if (command is Command.Send && command.message == TimeMachineServerMessage.Welcome) throw AssertionError("injected")
            executed += command
        }
        val a = Uuid.random()
        val b = Uuid.random()
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
        assertEquals(WriterState.Running, store.state.value.writer)
    }

    // backgroundScope would fail the test on the failure of any child: the scope is a dedicated one, as the application's.
    @Test
    fun aFailureThatStopsTheWriterIsPublishedAndLeftToItsScope() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> uncaught += e })
        try {
            // Its commands throw when read, outside the guards: the only way to fail the writer without a new API.
            val unreadable = object : AbstractList<Command>() {
                override val size: Int get() = throw NotImplementedError("injected")
                override fun get(index: Int): Command = throw NotImplementedError("injected")
            }
            val executed = mutableListOf<TransportCommand>()
            val store = TimeMachineStore(scope, reduce = { events -> Transition(on(events).state, unreadable) }) { executed += it }
            val a = Uuid.random()
            store.post(hello(c1, session))
            runCurrent()

            assertEquals(WriterState.Failed("injected"), store.state.value.writer)
            // Left to the scope's handler, not swallowed.
            assertEquals("injected", uncaught.single().message)
            assertTrue(store.state.value.session(session.id).isConnected)

            store.post(receive(c1, observed(a, 0)))
            runCurrent()

            assertEquals(emptyMap(), store.state.value.session(session.id).viewModels)
            assertEquals(emptyList(), executed)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aCancelledScopeLeavesTheStoreVisiblyStopped() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            val executed = mutableListOf<TransportCommand>()
            val store = TimeMachineStore(scope) { executed += it }
            val a = Uuid.random()
            store.post(hello(c1, session))
            store.post(receive(c1, started(a, 0)))
            runCurrent()
            assertEquals(WriterState.Running, store.state.value.writer)

            scope.cancel()
            runCurrent()
            assertEquals(WriterState.Stopped, store.state.value.writer)

            val sent = executed.size
            val request = store.intervene(session.id, a, VIEObjectKind.Intent, "I", JsonPrimitive(1))
            runCurrent()

            assertFalse(request in store.state.value.vm(session.id, a).interventions)
            assertEquals(sent, executed.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun anEventIsSummarizedWithoutItsContent() {
        val vm = Uuid.random()

        assertEquals("Observed on connection 1 at sequence 7, acknowledged", receive(c1, observed(vm, 7)).summary())
        assertEquals("ViewModelInfo on connection 1", receive(c1, vmInfo(vm)).summary())
        assertEquals("Disconnected", TimeMachineEvent.Disconnected(c1, t0).summary())
    }

    // Reached only by injection: the core, as it is, throws on no event outside a timeline.
    @Test
    fun aMessageOutsideTheTimelineTheCoreThrowsOnIsDroppedAndTheRestOfItsBatchApplied() = runTest {
        val executed = mutableListOf<TransportCommand>()
        val store = TimeMachineStore(
            backgroundScope,
            reduce = { events ->
                if (events.any { ((it as? TimeMachineEvent.MessageReceived)?.message) is TimeMachineClientMessage.ViewModelInfo }) error("injected")
                on(events)
            },
        ) { executed += it }
        val a = Uuid.random()
        val b = Uuid.random()
        store.post(hello(c1, session))
        store.post(receive(c1, observed(a, 0)))
        runCurrent()
        executed.clear()
        // Not a timeline frame: with no sequence, there is nothing to skip nor to acknowledge for it.
        store.post(receive(c1, vmInfo(a)))
        store.post(receive(c1, observed(b, 1)))
        runCurrent()

        val record = store.state.value.session(session.id)
        assertTrue(record.viewModels.getValue(a).isUnknown)
        assertTrue(record.viewModels.getValue(a).awaitingInfo)
        assertTrue(b in record.viewModels)
        assertEquals(SequenceLedger(floor = seq(0), highest = seq(1)), record.sequences)
        assertEquals(
            listOf<TransportCommand>(Command.Send(c1, TimeMachineServerMessage.RequestViewModelInfo(b)), Command.Send(c1, TimeMachineServerMessage.Received(seq(1)))),
            executed,
        )
    }

    // Reached only by injection, and it freezes that a frame whose Skipped fails too is not acknowledged.
    @Test
    fun aTimelineFrameWhoseSkipTheCoreAlsoThrowsOnIsDroppedAndTheRestOfItsBatchApplied() = runTest {
        val poison = Uuid.random()
        val executed = mutableListOf<TransportCommand>()
        val store = TimeMachineStore(
            backgroundScope,
            reduce = { events ->
                if (events.any {
                        it is TimeMachineEvent.Skipped ||
                            ((it as? TimeMachineEvent.MessageReceived)?.message as? TimeMachineClientMessage.Observed)?.observation?.viewModelId == poison
                    }) error("injected")
                on(events)
            },
        ) { executed += it }
        val b = Uuid.random()
        store.post(hello(c1, session))
        store.post(receive(c1, observed(Uuid.random(), 0)))
        runCurrent()
        executed.clear()
        store.post(receive(c1, observed(poison, 1)))
        store.post(receive(c1, observed(b, 2)))
        runCurrent()

        // Nothing for frame 1: its acknowledgement is lost, and the probe's cumulative one for 2 covers it.
        assertEquals(
            listOf<TransportCommand>(Command.Send(c1, TimeMachineServerMessage.RequestViewModelInfo(b)), Command.Send(c1, TimeMachineServerMessage.Received(seq(2)))),
            executed,
        )
        val record = store.state.value.session(session.id)
        assertEquals(SequenceLedger(floor = seq(0), highest = seq(2), missing = listOf(seqs(1, 1))), record.sequences)
        assertFalse(poison in record.viewModels)
    }
}
