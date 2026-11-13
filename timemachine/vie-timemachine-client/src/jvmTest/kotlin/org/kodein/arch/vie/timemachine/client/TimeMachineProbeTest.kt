package org.kodein.arch.vie.timemachine.client

import io.ktor.websocket.close
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.kodein.arch.vie.timemachine.client.connection.InspectorConnection
import org.kodein.arch.vie.timemachine.client.fixtures.FakeInspector
import org.kodein.arch.vie.timemachine.client.fixtures.info
import org.kodein.arch.vie.timemachine.client.fixtures.noIntervention
import org.kodein.arch.vie.timemachine.client.timeline.TimeMachineSink


/**
 * Runs [block] with a probe that connects to [inspector], and with the sink that probe reports to:
 * the probe builds its connection, and so reads that sink, once started.
 */
private fun withProbe(
    inspector: FakeInspector,
    // Longer than any test: no connection holds unless the test asks for it.
    heldAfter: Duration = 10.seconds,
    // Runs inside the probe, wherever it prints a notice: what it throws is thrown there.
    onNotice: (String) -> Unit = {},
    onRetired: (TimeMachineProbe) -> Unit = {},
    // Runs when the probe builds its connection, where it builds it: what it throws is thrown there.
    onNewConnection: () -> Unit = {},
    block: suspend (TimeMachineProbe, Deferred<TimeMachineSink>) -> Unit,
) = runBlocking {
    val config = TimeMachineConfig(appName = "ProbeTest", enabled = true, port = inspector.port)
    val notice: (String) -> Unit = { println(it); onNotice(it) }
    val probeSink = CompletableDeferred<TimeMachineSink>()
    val probe = TimeMachineProbe(config, notice, onRetired) { session, sink, outbox ->
        onNewConnection()
        probeSink.complete(sink)
        InspectorConnection(
            config, session, sink, outbox,
            firstRetryDelay = 20.milliseconds,
            maxRetryDelay = 100.milliseconds,
            heldAfter = heldAfter,
            handshakeTimeout = 300.milliseconds,
            notice = notice,
        )
    }
    try {
        withTimeout(10.seconds) { block(probe, probeSink) }
    } finally {
        probe.stop()
        inspector.close()
    }
}

class TimeMachineProbeTest {

    @Test
    fun anErrorInTheConnectionLoopRetiresTheProbe() {
        val inspector = FakeInspector()
        val retired = CompletableDeferred<TimeMachineProbe>()
        withProbe(
            inspector,
            // The connection is announced once it holds, from inside the connection loop.
            heldAfter = 50.milliseconds,
            onNotice = { if ("connected to the inspector" in it) throw LinkageError("from the test") },
            onRetired = { retired.complete(it) },
        ) { probe, sinkOnceStarted ->
            probe.start()
            val sink = sinkOnceStarted.await()
            val vm = Uuid.random()
            sink.started(info(vm), noIntervention)
            inspector.hellos.receive()

            assertSame(probe, retired.await())
            // The probe is told retired before it stops: give it the time to.
            while (!sink.isClosed) delay(10.milliseconds)
            assertTrue(sink.liveViewModels.isEmpty(), "A retired probe must let go of its ViewModels")
            assertNull(withTimeoutOrNull(500.milliseconds) { inspector.hellos.receive() }, "A retired probe must not connect again")
        }
    }

    @Test
    fun theConnectionIsBuiltOnTheProbesCoroutineAndNotOnTheThreadThatStartsIt() {
        val inspector = FakeInspector()
        val builtOn = CompletableDeferred<Thread>()
        withProbe(inspector, onNewConnection = { builtOn.complete(Thread.currentThread()) }) { probe, _ ->
            probe.start()
            assertNotSame(Thread.currentThread(), builtOn.await(), "Building the HTTP client must not take the starter's time")
            inspector.hellos.receive()
        }
    }

    @Test
    fun anErrorWhileBuildingTheConnectionRetiresTheProbe() {
        val inspector = FakeInspector()
        val retired = CompletableDeferred<TimeMachineProbe>()
        withProbe(
            inspector,
            onRetired = { retired.complete(it) },
            onNewConnection = { throw LinkageError("from the test") },
        ) { probe, _ ->
            probe.start()
            assertSame(probe, retired.await())
            assertNull(withTimeoutOrNull(500.milliseconds) { inspector.hellos.receive() }, "A retired probe must not connect")
        }
    }

    @Test
    fun stoppingTheProbeEndsTheConnectionLoop() {
        val inspector = FakeInspector()
        withProbe(inspector) { probe, _ ->
            probe.start()
            inspector.hellos.receive()
            val connection = inspector.welcomed.receive()
            probe.stop()
            connection.close()
            assertNull(withTimeoutOrNull(500.milliseconds) { inspector.hellos.receive() })
        }
    }
}
