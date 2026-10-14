package org.kodein.arch.vie.timemachine.client

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import androidx.lifecycle.ViewModelStore
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
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
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation


@Serializable
internal data object StressModel : VIE.Model

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
) : VIEViewModel<StressModel, StressIntent, StressSignal>(StressModel, middlewares) {
    override suspend fun process(intent: StressIntent) {}
}

/**
 * Drives a probed ViewModel whose `viewModelScope` runs on a multi-threaded dispatcher, as it does
 * when no Main dispatcher is available: its coroutines then observe and inject concurrently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeMachineMiddlewareConcurrencyTest {

    @Test
    fun concurrentInjectionsAreEachTaggedOnAMultiThreadedViewModelScope() = runBlocking {
        val observations = ConcurrentLinkedQueue<VIEObservation>()
        val sink = TimeMachineSink { entry ->
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
                repeat(200) {
                    requested += (0 until 16).map { i ->
                        async(Dispatchers.Default) {
                            val request = if (i % 2 == 0) intervention(probe.viewModelId, VIEObjectKind.Intent, role<StressIntent>(), StressIntent.Ping)
                            else intervention(probe.viewModelId, VIEObjectKind.Signal, role<StressSignal>(), StressSignal.Pinged)
                            assertEquals(VIEInterventionResult.Success, intervener.intervene(request))
                            request.requestId
                        }
                    }.awaitAll()
                    withTimeout(5.seconds) {
                        while (observations.count { it.kind != VIEObjectKind.Model } < requested.size) delay(1)
                    }
                }
                val tagged = observations.filter { it.kind != VIEObjectKind.Model }.map { it.interventionId }
                assertEquals(requested, tagged.toSet(), "Every injected intent and signal must be tagged with its own request")
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
