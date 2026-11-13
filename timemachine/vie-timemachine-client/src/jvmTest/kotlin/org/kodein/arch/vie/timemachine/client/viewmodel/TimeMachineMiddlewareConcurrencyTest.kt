package org.kodein.arch.vie.timemachine.client.viewmodel

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import androidx.lifecycle.ViewModelStore
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEMiddleware
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.timemachine.client.fixtures.role
import org.kodein.arch.vie.timemachine.client.fixtures.testLog
import org.kodein.arch.vie.timemachine.client.role.VIERole
import org.kodein.arch.vie.timemachine.client.timeline.TimeMachineIntervener
import org.kodein.arch.vie.timemachine.client.timeline.TimeMachineSink
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineTimelineMessage
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation


/** A data class, not an object: each injected model must be an instance of its own. */
@Serializable
internal data class StressModel(val n: Int = 0) : VIE.Model

@Serializable
internal sealed interface StressIntent : VIE.Intent {
    @Serializable
    data object Ping : StressIntent
}

@Serializable
internal sealed interface StressSignal : VIE.Signal {
    @Serializable
    data object Pinged : StressSignal, VIE.Signal.Event
}

internal class StressVM(
    middlewares: List<VIEMiddleware<StressModel, StressIntent, StressSignal>>,
) : VIEViewModel<StressModel, StressIntent, StressSignal>(StressModel(), middlewares) {
    override suspend fun process(intent: StressIntent) {}
}

/**
 * Drives a probed ViewModel whose `viewModelScope` runs on a multi-threaded dispatcher, as it does
 * when no Main dispatcher is available: its coroutines then observe and inject concurrently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeMachineMiddlewareConcurrencyTest {

    /** What is left of a ViewModel that was built, observed, then dropped without being cleared. */
    private class Dropped(val viewModel: WeakReference<StressVM>, val intervener: TimeMachineIntervener)

    // In a function of its own: no local of the test may keep the ViewModel reachable.
    private fun buildThenDrop(probe: TimeMachineMiddleware<StressModel, StressIntent, StressSignal>, sink: TimeMachineSink): Dropped {
        val vm = StressVM(listOf(probe))
        val live = assertNotNull(sink.liveViewModels[probe.viewModelId], "The ViewModel must be registered while it lives")
        assertFalse(live.intervener.isViewModelCollected)
        return Dropped(WeakReference(vm), live.intervener)
    }

    @Test
    fun aViewModelThatIsNeverClearedIsNotKeptReachableAndIsReportedClosedOnceCollected() {
        val messages = ConcurrentLinkedQueue<TimeMachineTimelineMessage>()
        val sink = TimeMachineSink(testLog) { messages += it.describe(TimeMachineSequence.First) }
        val probe = TimeMachineMiddleware(role<StressModel>(), role<StressIntent>(), role<StressSignal>(), sink)

        val dropped = buildThenDrop(probe, sink)
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (dropped.viewModel.get() != null) {
            assertTrue(deadline.hasNotPassedNow(), "The ViewModel is still reachable: the probe keeps it from being garbage-collected")
            System.gc()
            Thread.sleep(50)
        }

        assertTrue(dropped.intervener.isViewModelCollected)
        val request = TimeMachineServerMessage.Intervene(Uuid.random(), probe.viewModelId, VIEObjectKind.Intent, role<StressIntent>().typeNameOf(StressIntent.Ping), requireNotNull(role<StressIntent>().encode(StressIntent.Ping)))
        val failure = assertIs<VIEInterventionResult.Failure>(dropped.intervener.intervene(request))
        assertEquals(VIEInterventionResult.Reason.ClosedViewModel, failure.reason)

        assertFalse(probe.viewModelId in sink.liveViewModels, "A collected ViewModel must leave the registry")
        assertEquals(1, messages.count { it is TimeMachineClientMessage.ViewModelClosed && it.viewModelId == probe.viewModelId })
    }

    @Test
    fun concurrentInjectionsAreEachTaggedOnAMultiThreadedViewModelScope() = runBlocking {
        val observations = ConcurrentLinkedQueue<VIEObservation>()
        val sink = TimeMachineSink(testLog) { entry ->
            (entry.describe(TimeMachineSequence.First) as? TimeMachineClientMessage.Observed)?.let { observations += it.observation }
        }
        val probe = TimeMachineMiddleware(role<StressModel>(), role<StressIntent>(), role<StressSignal>(), sink)

        Dispatchers.setMain(Dispatchers.Default)
        try {
            val store = ViewModelStore().apply { put("vm", StressVM(listOf(probe))) }
            try {
                val intervener = sink.liveViewModels.getValue(probe.viewModelId).intervener
                val requested = mutableSetOf<Uuid>()
                // Each round stays below the injection queues' bound, so a correct probe tags every object.
                repeat(200) { round ->
                    requested += (0 until 18).map { i ->
                        async(Dispatchers.Default) {
                            val request = when (i % 3) {
                                0 -> intervention(probe.viewModelId, VIEObjectKind.Model, role<StressModel>(), StressModel(round * 18 + i + 1))
                                1 -> intervention(probe.viewModelId, VIEObjectKind.Intent, role<StressIntent>(), StressIntent.Ping)
                                else -> intervention(probe.viewModelId, VIEObjectKind.Signal, role<StressSignal>(), StressSignal.Pinged)
                            }
                            assertEquals(VIEInterventionResult.Success, intervener.intervene(request))
                            request.requestId
                        }
                    }.awaitAll()
                    // Every observation but the first model is one of the injected objects.
                    withTimeout(5.seconds) {
                        while (observations.size - 1 < requested.size) delay(1)
                    }
                }
                val (initial, injected) = observations.partition { it.kind == VIEObjectKind.Model && it.asString == StressModel().toString() }
                assertEquals(listOf(null), initial.map { it.interventionId }, "Only the first model is the ViewModel's own")
                val tagged = injected.map { it.interventionId }
                assertEquals(requested, tagged.toSet(), "Every injected model, intent and signal must be tagged with its own request")
                assertEquals(requested.size, tagged.size, "No object may be observed twice")
            } finally {
                store.clear()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun <T : Any> intervention(viewModelId: Uuid, kind: VIEObjectKind, role: VIERole<T>, value: T) =
        TimeMachineServerMessage.Intervene(
            requestId = Uuid.random(),
            viewModelId = viewModelId,
            kind = kind,
            typeName = role.typeNameOf(value),
            asJson = requireNotNull(role.encode(value)),
        )
}
