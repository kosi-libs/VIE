package org.kodein.arch.vie.timemachine.client

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import androidx.lifecycle.ViewModelStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonNull
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEMiddleware
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.TimeMachineTimelineMessage
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery
import org.kodein.arch.vie.timemachine.protocol.VIEObservation


@Serializable
private data class CountModel(val count: Int) : VIE.Model

private data class NotSerializableModel(val count: Int) : VIE.Model

@Serializable
private data class ThrowingToStringModel(val count: Int) : VIE.Model {
    override fun toString(): String = error("boom")
}

private object ExplodingSerializer : KSerializer<ExplodingModel> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ExplodingModel", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: ExplodingModel) = error("boom")
    override fun deserialize(decoder: Decoder): ExplodingModel = error("boom")
}

@Serializable(with = ExplodingSerializer::class)
private data class ExplodingModel(val count: Int) : VIE.Model

@Serializable
private sealed interface SealedModel : VIE.Model {
    val count: Int

    @Serializable
    data class Filtered(val type: String, override val count: Int) : SealedModel

    @Serializable(with = BrokenSerializer::class)
    data class Broken(override val count: Int) : SealedModel
}

private object BrokenSerializer : KSerializer<SealedModel.Broken> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BrokenModel")
    override fun serialize(encoder: Encoder, value: SealedModel.Broken) = error("boom")
    override fun deserialize(decoder: Decoder): SealedModel.Broken = error("boom")
}

private fun SealedModel.incremented(): SealedModel = when (this) {
    is SealedModel.Filtered -> copy(count = count + 1)
    is SealedModel.Broken -> copy(count = count + 1)
}

@Serializable
private sealed interface ProbeIntent : VIE.Intent {
    @Serializable
    data object Increment : ProbeIntent
}

@Serializable
private sealed interface ProbeSignal : VIE.Signal {
    @Serializable
    data object Incremented : ProbeSignal, VIE.Signal.Event

    /** Picked neither delivery kind: the ViewModel cannot emit it. */
    @Serializable
    data object Untyped : ProbeSignal
}

private open class ProbeVM<M : VIE.Model>(
    firstModel: M,
    private val increment: M.() -> M,
    middlewares: List<VIEMiddleware<M, ProbeIntent, ProbeSignal>>,
) : VIEViewModel<M, ProbeIntent, ProbeSignal>(firstModel, middlewares) {
    var processed = 0
    override suspend fun process(intent: ProbeIntent) {
        when (intent) {
            ProbeIntent.Increment -> {
                update { increment() }
                signal(ProbeSignal.Incremented)
            }
        }
        processed++
    }
}

/** A subclass of an open ViewModel, which the probe must name after itself, not after its parent. */
private class ChildProbeVM(
    middlewares: List<VIEMiddleware<CountModel, ProbeIntent, ProbeSignal>>,
) : ProbeVM<CountModel>(CountModel(0), { copy(count = count + 1) }, middlewares)

private inline fun <reified M : VIE.Model> probe(sink: TimeMachineSink) =
    TimeMachineMiddleware(role<M>(), role<ProbeIntent>(), role<ProbeSignal>(), sink)

/** A sink that describes each entry as soon as it is reported, on the reporting thread. */
private fun recordingSink(messages: MutableList<TimeMachineTimelineMessage>) = TimeMachineSink { messages += it.describe(TimeMachineSequence(messages.size.toLong())) }

private val List<TimeMachineTimelineMessage>.observations: List<VIEObservation>
    get() = filterIsInstance<TimeMachineClientMessage.Observed>().map { it.observation }

/** Asks the live ViewModel [viewModelId] to take [value], as the inspector would: returns the request's
 *  identifier and its result. */
private fun <T : Any> TimeMachineSink.intervene(viewModelId: Uuid, kind: VIEObjectKind, role: VIERole<T>, value: T): Pair<Uuid, VIEInterventionResult> {
    val request = TimeMachineServerMessage.Intervene(
        requestId = Uuid.random(),
        viewModelId = viewModelId,
        kind = kind,
        typeName = role.typeNameOf(value),
        asJson = requireNotNull(role.encode(value)),
    )
    return request.requestId to requireNotNull(liveViewModels[viewModelId]).intervener.intervene(request)
}

/**
 * Drives [vm] the way a real app does before any view shows up: nothing collects `modelFlow` (unlike
 * `runVMTest`), and the ViewModel is cleared before returning, so [block] can be followed by
 * assertions on what the probe reported after close.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun <M : VIE.Model> TestScope.withoutView(
    newVM: () -> ProbeVM<M>,
    block: suspend TestScope.(ProbeVM<M>) -> Unit,
) {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val vm = newVM()
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            block(vm)
        } finally {
            store.clear()
            advanceUntilIdle()
        }
    } finally {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimeMachineMiddlewareTest {

    @Test
    fun startedIsReportedFirstOnceAttachedAndDescribesTheViewModel() = runTest {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        val probe = probe<CountModel>(recordingSink(messages))
        assertTrue(messages.isEmpty(), "A probe built but not installed must report nothing")

        withoutView({ ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe)) }) {}

        val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(messages.first())
        assertEquals(probe.viewModelId, started.info.id)
        assertEquals(ProbeVM::class.displayName, started.info.typeName)
        assertEquals(role<CountModel>().toVIETypeInfo(), started.info.model)
        assertEquals(role<ProbeIntent>().toVIETypeInfo(), started.info.intent)
        assertEquals(role<ProbeSignal>().toVIETypeInfo(), started.info.signal)
    }

    @Test
    fun aSubclassIsNamedAfterItselfNotItsParent() = runTest {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        withoutView({ ChildProbeVM(listOf(probe(recordingSink(messages)))) }) {}

        val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(messages.first())
        assertEquals(ChildProbeVM::class.displayName, started.info.typeName)
    }

    @Test
    fun aLocalViewModelIsStillNamedAfterItsClass() = runTest {
        // A local class has no qualified name, on the platforms that report qualified names.
        class LocalVM(probe: TimeMachineMiddleware<CountModel, ProbeIntent, ProbeSignal>) :
            ProbeVM<CountModel>(CountModel(0), { copy(count = count + 1) }, listOf(probe))

        val messages = mutableListOf<TimeMachineTimelineMessage>()
        withoutView({ LocalVM(probe(recordingSink(messages))) }) {}

        val started = assertIs<TimeMachineClientMessage.ViewModelStarted>(messages.first())
        assertTrue(started.info.typeName.endsWith("LocalVM"), "Named ${started.info.typeName}")
    }

    @Test
    fun allThreeKindsAreObservedInOrder(): TestResult {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        return runVMTest(
            newVM = { ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe<CountModel>(recordingSink(messages)))) },
        ) {
            post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertIs<TimeMachineClientMessage.ViewModelStarted>(messages.first())
            assertEquals(
                listOf(
                    VIEObjectKind.Model to CountModel(0).toString(),
                    VIEObjectKind.Intent to ProbeIntent.Increment.toString(),
                    VIEObjectKind.Model to CountModel(1).toString(),
                    VIEObjectKind.Signal to ProbeSignal.Incremented.toString(),
                ),
                messages.observations.map { it.kind to it.asString },
            )
            val (_, intent, model, signal) = messages.observations
            assertEquals(role<CountModel>().typeName, model.typeName)
            assertEquals(role<ProbeIntent>().sealedSubTypes!!.single(), intent.typeName)
            assertEquals(role<ProbeSignal>().typeNameOf(ProbeSignal.Incremented), signal.typeName)
            assertEquals(role<CountModel>().encode(CountModel(1)), model.asJson)
            assertNotNull(intent.asJson)
            assertNotNull(signal.asJson)
            assertTrue(messages.observations.all { it.interventionId == null })
            assertEquals(listOf(null, null, null, VIESignalDelivery.Event), messages.observations.map { it.delivery })
        }
    }

    @Test
    fun modelsAreObservedWithNoViewCollecting() = runTest {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        withoutView({ ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe<CountModel>(recordingSink(messages)))) }) { vm ->
            vm.post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertEquals(
                listOf(CountModel(0).toString(), CountModel(1).toString()),
                messages.observations.filter { it.kind == VIEObjectKind.Model }.map { it.asString },
            )
        }
    }

    @Test
    fun aBurstOfUpdatesReportsEveryIntermediateModel() = runTest {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        withoutView({ ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe<CountModel>(recordingSink(messages)))) }) { vm ->
            repeat(5) { vm.post(ProbeIntent.Increment) }
            advanceUntilIdle()

            assertEquals(
                (0..5).map { CountModel(it).toString() },
                messages.observations.filter { it.kind == VIEObjectKind.Model }.map { it.asString },
            )
        }
    }

    @Test
    fun closedIsReportedOnceAndLast() = runTest {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        val probe = probe<CountModel>(recordingSink(messages))
        withoutView({ ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe)) }) { vm ->
            vm.post(ProbeIntent.Increment)
            advanceUntilIdle()
        }

        val closed = assertIs<TimeMachineClientMessage.ViewModelClosed>(messages.last())
        assertEquals(probe.viewModelId, closed.viewModelId)
        assertEquals(1, messages.count { it is TimeMachineClientMessage.ViewModelClosed })
    }

    @Test
    fun closedIsNotReportedWhenTheIntentFlowEndsEarly() = runTest {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        // Listed first, so folded last: it completes the intent flow downstream of the probe while
        // the ViewModel stays alive — as a `process()` that throws would.
        val takeOne = object : VIEMiddleware<CountModel, ProbeIntent, ProbeSignal> {
            override fun interceptIntents(flow: Flow<ProbeIntent>): Flow<ProbeIntent> = flow.take(1)
        }
        withoutView({ ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(takeOne, probe<CountModel>(recordingSink(messages)))) }) { vm ->
            vm.post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertEquals(1, vm.processed)
            assertTrue(messages.none { it is TimeMachineClientMessage.ViewModelClosed })
        }

        assertIs<TimeMachineClientMessage.ViewModelClosed>(messages.last())
        assertEquals(1, messages.count { it is TimeMachineClientMessage.ViewModelClosed })
    }

    @Test
    fun nonSerializableModelIsObservedWithoutJson(): TestResult {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        return runVMTest(
            newVM = { ProbeVM(NotSerializableModel(0), { copy(count = count + 1) }, listOf(probe<NotSerializableModel>(recordingSink(messages)))) },
        ) {
            post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertEquals(1, pendingSignals().size)
            val models = messages.observations.filter { it.kind == VIEObjectKind.Model }
            assertEquals(listOf(NotSerializableModel(0).toString(), NotSerializableModel(1).toString()), models.map { it.asString })
            assertTrue(models.all { it.asJson == null })
            assertTrue(models.all { it.typeName.endsWith("NotSerializableModel") })
        }
    }

    @Test
    fun anEncodingFailureNeverReachesTheApp(): TestResult {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        return runVMTest(
            newVM = { ProbeVM(ExplodingModel(0), { copy(count = count + 1) }, listOf(probe<ExplodingModel>(recordingSink(messages)))) },
        ) {
            post(ProbeIntent.Increment)
            advanceUntilIdle()
            post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertEquals(2, pendingSignals().size)
            val models = messages.observations.filter { it.kind == VIEObjectKind.Model }
            assertEquals(3, models.size)
            models.forEach { assertNull(it.asJson) }
        }
    }

    @Test
    fun aMiddlewareListedAfterTheProbeSeesEveryModelOnce(): TestResult {
        // The probe observes models as they pass through its intercept, without collecting the flow a
        // second time, so its position in `middlewares` does not duplicate them for the middlewares
        // around it.
        class Recording : VIEMiddleware<CountModel, ProbeIntent, ProbeSignal> {
            val seen = mutableListOf<Int>()
            override fun interceptModels(flow: Flow<CountModel>): Flow<CountModel> = flow.onEach { seen += it.count }
        }
        val before = Recording()
        val after = Recording()
        return runVMTest(
            newVM = { ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(before, probe<CountModel>(TimeMachineSink {}), after)) },
        ) {
            post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertEquals(listOf(0, 1), before.seen)
            assertEquals(listOf(0, 1), after.seen)
        }
    }

    @Test
    fun aToStringFailureNeverReachesTheApp(): TestResult {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        return runVMTest(
            newVM = { ProbeVM(ThrowingToStringModel(0), { copy(count = count + 1) }, listOf(probe<ThrowingToStringModel>(recordingSink(messages)))) },
        ) {
            post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertEquals(1, pendingSignals().size)
            val models = messages.observations.filter { it.kind == VIEObjectKind.Model }
            assertEquals(2, models.size)
            models.forEach { assertTrue(it.asString.startsWith("<toString() failed:"), it.asString) }
            val role = role<ThrowingToStringModel>()
            assertEquals(listOf(role.encode(ThrowingToStringModel(0)), role.encode(ThrowingToStringModel(1))), models.map { it.asJson })
        }
    }

    @Test
    fun aThrowingOutletNeverReachesTheApp() = runTest {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        // Records, then throws: every report fails, and what was recorded shows later ones still run.
        val sink = TimeMachineSink { messages += it.describe(TimeMachineSequence(messages.size.toLong())); error("boom") }
        withoutView({ ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe<CountModel>(sink))) }) { vm ->
            vm.post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertEquals(1, vm.processed)
        }

        assertIs<TimeMachineClientMessage.ViewModelStarted>(messages.first())
        assertIs<TimeMachineClientMessage.ViewModelClosed>(messages.last())
        assertEquals(
            setOf(VIEObjectKind.Model, VIEObjectKind.Intent, VIEObjectKind.Signal),
            messages.observations.map { it.kind }.toSet(),
        )
    }

    @Test
    fun aSealedSubTypeWithATypePropertyIsEncodedUnderItsConcreteName(): TestResult {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        return runVMTest(
            newVM = { ProbeVM<SealedModel>(SealedModel.Filtered("all", 0), { incremented() }, listOf(probe<SealedModel>(recordingSink(messages)))) },
        ) {
            post(ProbeIntent.Increment)
            advanceUntilIdle()

            val models = messages.observations.filter { it.kind == VIEObjectKind.Model }
            assertEquals(2, models.size)
            val role = role<SealedModel>()
            models.forEach { assertEquals("org.kodein.arch.vie.timemachine.client.SealedModel.Filtered", it.typeName) }
            assertEquals(role.encode(SealedModel.Filtered("all", 1)), models.last().asJson)
        }
    }

    @Test
    fun aSealedSubTypeThatFailsToEncodeIsStillReportedUnderItsConcreteName(): TestResult {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        return runVMTest(
            newVM = { ProbeVM<SealedModel>(SealedModel.Broken(0), { incremented() }, listOf(probe<SealedModel>(recordingSink(messages)))) },
        ) {
            post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertEquals(1, pendingSignals().size)
            val models = messages.observations.filter { it.kind == VIEObjectKind.Model }
            assertEquals(2, models.size)
            assertTrue("BrokenModel" in requireNotNull(role<SealedModel>().sealedSubTypes))
            models.forEach {
                assertNull(it.asJson)
                assertEquals("BrokenModel", it.typeName)
            }
        }
    }

    @Test
    fun anInjectedModelIsTheOneTheNextUpdateComputesFrom(): TestResult {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        val sink = recordingSink(messages)
        val probe = probe<CountModel>(sink)
        return runVMTest(
            newVM = { ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe)) },
        ) {
            repeat(5) { post(ProbeIntent.Increment) }
            advanceUntilIdle()
            assertEquals(CountModel(5), model)

            val (requestId, result) = sink.intervene(probe.viewModelId, VIEObjectKind.Model, role<CountModel>(), CountModel(100))
            assertEquals(VIEInterventionResult.Success, result)
            advanceUntilIdle()
            post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertEquals(CountModel(101), model)
            val models = messages.observations.filter { it.kind == VIEObjectKind.Model }
            val (injected, produced) = models.partition { it.asString == CountModel(100).toString() }
            assertEquals(requestId, injected.single().interventionId)
            assertTrue(produced.all { it.interventionId == null })
            assertEquals(CountModel(101).toString(), produced.last().asString)
        }
    }

    @Test
    fun anInjectedModelEqualToTheCurrentOneIsStillTagged(): TestResult {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        val sink = recordingSink(messages)
        val probe = probe<CountModel>(sink)
        return runVMTest(
            newVM = { ProbeVM(CountModel(5), { copy(count = count + 1) }, listOf(probe)) },
        ) {
            advanceUntilIdle()
            assertEquals(CountModel(5), model)

            val (requestId, result) = sink.intervene(probe.viewModelId, VIEObjectKind.Model, role<CountModel>(), CountModel(5))
            assertEquals(VIEInterventionResult.Success, result)
            advanceUntilIdle()

            val models = messages.observations.filter { it.kind == VIEObjectKind.Model }
            assertEquals(requestId, models.last().interventionId)
        }
    }

    @Test
    fun anInjectedIntentIsProcessedAndTagged(): TestResult {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        val sink = recordingSink(messages)
        val probe = probe<CountModel>(sink)
        return runVMTest(
            newVM = { ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe)) },
        ) {
            val (requestId, result) = sink.intervene(probe.viewModelId, VIEObjectKind.Intent, role<ProbeIntent>(), ProbeIntent.Increment)
            assertEquals(VIEInterventionResult.Success, result)
            advanceUntilIdle()
            post(ProbeIntent.Increment)
            advanceUntilIdle()

            assertEquals(2, pendingSignals().size)
            val intents = messages.observations.filter { it.kind == VIEObjectKind.Intent }
            assertEquals(listOf(requestId, null), intents.map { it.interventionId })
        }
    }

    @Test
    fun anInjectedSignalReachesTheViewAndIsTagged(): TestResult {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        val sink = recordingSink(messages)
        val probe = probe<CountModel>(sink)
        return runVMTest(
            newVM = { ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe)) },
        ) {
            val (requestId, result) = sink.intervene(probe.viewModelId, VIEObjectKind.Signal, role<ProbeSignal>(), ProbeSignal.Incremented)
            assertEquals(VIEInterventionResult.Success, result)
            advanceUntilIdle()

            assertEquals(listOf<ProbeSignal>(ProbeSignal.Incremented), pendingSignals())
            assertEquals(requestId, messages.observations.single { it.kind == VIEObjectKind.Signal }.interventionId)
        }
    }

    @Test
    fun aSignalOfNeitherDeliveryKindIsRefused(): TestResult {
        val sink = TimeMachineSink {}
        val probe = probe<CountModel>(sink)
        return runVMTest(
            newVM = { ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe)) },
        ) {
            val (_, result) = sink.intervene(probe.viewModelId, VIEObjectKind.Signal, role<ProbeSignal>(), ProbeSignal.Untyped)
            advanceUntilIdle()

            assertEquals(VIEInterventionResult.Reason.UnknownType, assertIs<VIEInterventionResult.Failure>(result).reason)
            assertTrue(pendingSignals().isEmpty())
        }
    }

    @Test
    fun aNonSerializableRoleRefusesIntervention(): TestResult {
        val sink = TimeMachineSink {}
        val probe = probe<NotSerializableModel>(sink)
        return runVMTest(
            newVM = { ProbeVM(NotSerializableModel(0), { copy(count = count + 1) }, listOf(probe)) },
        ) {
            val live = requireNotNull(sink.liveViewModels[probe.viewModelId])
            val request = TimeMachineServerMessage.Intervene(Uuid.random(), probe.viewModelId, VIEObjectKind.Model, live.info.model.typeName, JsonNull)
            val result = live.intervener.intervene(request)

            assertEquals(VIEInterventionResult.Reason.NotSerializable, assertIs<VIEInterventionResult.Failure>(result).reason)
        }
    }

    @Test
    fun aMiddlewareAttachedToASecondViewModelLeavesItAlone() = runTest {
        val messages = mutableListOf<TimeMachineTimelineMessage>()
        val probe = probe<CountModel>(recordingSink(messages))
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val first = ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe))
            val second = ProbeVM(CountModel(10), { copy(count = count + 1) }, listOf(probe))
            val store = ViewModelStore().apply { put("first", first); put("second", second) }
            second.post(ProbeIntent.Increment)
            advanceUntilIdle()
            assertEquals(1, second.processed)
            store.clear()
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }

        assertEquals(listOf(probe.viewModelId), messages.filterIsInstance<TimeMachineClientMessage.ViewModelStarted>().map { it.info.id })
        // Nothing of the second ViewModel is reported: only the first's first model.
        assertEquals(listOf(CountModel(0).toString()), messages.observations.map { it.asString })
        assertEquals(1, messages.count { it is TimeMachineClientMessage.ViewModelClosed })
    }

    @Test
    fun aClosedViewModelRefusesIntervention() = runTest {
        val sink = TimeMachineSink {}
        val probe = probe<CountModel>(sink)
        lateinit var live: TimeMachineSink.LiveViewModel
        withoutView({ ProbeVM(CountModel(0), { copy(count = count + 1) }, listOf(probe)) }) {
            live = requireNotNull(sink.liveViewModels[probe.viewModelId])
        }

        val request = TimeMachineServerMessage.Intervene(
            Uuid.random(), probe.viewModelId, VIEObjectKind.Model, live.info.model.typeName, requireNotNull(role<CountModel>().encode(CountModel(1))),
        )
        assertEquals(VIEInterventionResult.Reason.ClosedViewModel, assertIs<VIEInterventionResult.Failure>(live.intervener.intervene(request)).reason)
        assertTrue(probe.viewModelId !in sink.liveViewModels)
    }

    @Test
    fun anInjectionLaunchedOnAClearedViewModelIsCancelled() = runTest {
        withoutView({ ProbeVM(CountModel(0), { copy(count = count + 1) }, emptyList()) }) { vm ->
            val injector = VIEViewModelInjector(vm)
            // Cleared: what `intervene()` sees when the clear comes between its `closed` check and the launch.
            val store = ViewModelStore().apply { put("other", vm) }
            store.clear()

            assertTrue(injector.injectModel(CountModel(1)) {}.isCancelled)
            assertTrue(injector.injectIntent(ProbeIntent.Increment) {}.isCancelled)
            assertTrue(assertNotNull(injector.injectSignal(ProbeSignal.Incremented) {}).isCancelled)
        }
    }

    @Test
    fun anInjectionLaunchedOnALiveViewModelIsNotCancelled() = runTest {
        withoutView({ ProbeVM(CountModel(0), { copy(count = count + 1) }, emptyList()) }) { vm ->
            val injector = VIEViewModelInjector(vm)

            assertTrue(!injector.injectModel(CountModel(1)) {}.isCancelled)
            assertNull(injector.injectSignal(ProbeSignal.Untyped) {})
        }
    }
}
