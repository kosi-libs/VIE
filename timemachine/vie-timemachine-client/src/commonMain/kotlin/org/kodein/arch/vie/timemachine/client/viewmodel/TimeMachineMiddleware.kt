package org.kodein.arch.vie.timemachine.client.viewmodel

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.update
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEMiddleware
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.timemachine.client.ProbeTuning
import org.kodein.arch.vie.timemachine.client.type.VIEObjectType
import org.kodein.arch.vie.timemachine.client.type.displayName
import org.kodein.arch.vie.timemachine.client.timeline.TimeMachineIntervener
import org.kodein.arch.vie.timemachine.client.timeline.TimeMachineSink
import org.kodein.arch.vie.timemachine.protocol.TimeMachineInterventionResult
import org.kodein.arch.vie.timemachine.protocol.TimeMachineInterventionResult.Reason
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo


/**
 * The probe's middleware: reports one ViewModel's start, every model, intent and signal that passes
 * through it, and its close, to [sink]; and injects into that ViewModel the object of each
 * intervention the inspector requests.
 *
 * - *Started* is reported when the middleware is attached to its ViewModel, not when it is built:
 *   a middleware built but never installed reports nothing. The ViewModel is named after its own
 *   class, read once attached: a subclass is named after itself, not after the class that installed
 *   the middleware. *Closed* is reported when the ViewModel is cleared — through a closeable
 *   registered on it, not the intent flow's completion, which also happens when `process()` throws
 *   or a middleware ends the flow while the ViewModel lives on. From then on, an object that reaches
 *   an intercept is no longer reported. On a multi-threaded dispatcher, though, an object whose report
 *   is already underway on another thread can still be reported after the close. A ViewModel that is
 *   never cleared is not kept reachable by the probe: it is reported *closed* some time after it is
 *   garbage-collected (see [TimeMachineSink]).
 * - Models, intents and signals are observed in their intercepts, so their order is exactly the
 *   order the ViewModel handles them. The signal flow itself is never collected: that would steal
 *   effects from the view.
 * - The model flow runs from the start, with no view collecting, and carries every intermediate
 *   model of a burst, none conflated, starting with the first model — after *started*, since the
 *   ViewModel attaches its middlewares before it starts its model flow.
 * - Interventions go through a [TimeMachineViewModelInjector] — the ViewModel's own public `update {}`,
 *   `post()` and `signal()` — so an injected model is the one the next `update { copy(…) }` computes
 *   from. An injected object is recognised by identity when it is observed, and tagged with its
 *   request's identifier. An injected model is always tagged, equal to the current one or not: it
 *   is injected inside the ViewModel's own `update {}`, which then sends that very instance down the
 *   model flow. An injected data object, though, is not tagged when an app-produced instance of it,
 *   the very same object, overtakes it.
 *
 * Belongs *last* in `middlewares`: that puts the middleware nearest the source, so it reports models
 * as `update {}` produced them, signals as the ViewModel emitted them, and intents as the view posted
 * them — before any other middleware transforms, filters or maps them. An intent another middleware
 * drops is still reported, though `process()` never receives it.
 *
 * As [VIEMiddleware.onAttached] requires, an instance serves exactly one ViewModel: the first it is
 * attached to. Attached to another one too (e.g. as a DI singleton), it prints a notice, and neither
 * reports that ViewModel's start and close nor injects anything into it. It ignores that ViewModel's models,
 * intents and signals too, but only when the two are built one after the other on a single-threaded
 * dispatcher, such as `Dispatchers.Main`: the intercepts are not told which ViewModel calls them, and
 * the first call of each wins. A ViewModel calls [interceptModels] from the coroutine that collects
 * its models, which on a multi-threaded dispatcher can run after the next ViewModel's; and two
 * ViewModels built concurrently can call any intercept in either order. The second ViewModel's
 * objects are then reported under the first one's identifier, in place of the first one's.
 *
 * Never throws into the host app: a failure to report an object is printed and swallowed, an `Error`
 * included. Nor does
 * it describe objects on the ViewModel's dispatcher: [sink] defers that (see [TimeMachineSink]).
 *
 * It outlives its probe, since it stays installed in its ViewModel: once the probe stops, [sink] is
 * closed, and the middleware reports nothing more.
 */
internal class TimeMachineMiddleware<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    private val modelType: VIEObjectType<Model>,
    private val intentType: VIEObjectType<Intent>,
    private val signalType: VIEObjectType<Signal>,
    private val sink: TimeMachineSink,
) : VIEMiddleware<Model, Intent, Signal> {

    val viewModelId: Uuid = Uuid.random()

    private val closed = AtomicBoolean(false)

    // One per hook, which a ViewModel calls once each: only the first call takes effect. A ViewModel
    // calls `onAttached` first, `interceptIntents` and `interceptSignals` from its constructor, and
    // `interceptModels` from the coroutine that collects its models, whenever that starts. So the
    // first ViewModel makes every first call only when the next one is built after it on a
    // single-threaded dispatcher (see the class KDoc).
    private val attached = AtomicBoolean(false)
    private val modelsIntercepted = AtomicBoolean(false)
    private val intentsIntercepted = AtomicBoolean(false)
    private val signalsIntercepted = AtomicBoolean(false)

    // Injected objects on their way into the ViewModel, by the intervention request they answer. Touched by the
    // injector's `entering` callbacks and by observation: several of `viewModelScope`'s coroutines,
    // which run on several threads when its dispatcher does (e.g. `Dispatchers.Default`, the fallback
    // when no Main dispatcher is available).
    private val injectedModels = InjectedQueue<Model>()
    private val injectedIntents = InjectedQueue<Intent>()
    private val injectedSignals = InjectedQueue<Signal>()

    override fun interceptModels(flow: Flow<Model>): Flow<Model> =
        if (!modelsIntercepted.compareAndSet(expectedValue = false, newValue = true)) flow
        else flow.onEach { observe(VIEObjectKind.Model, modelType, it, injectedModels.take(it)) }

    override fun interceptIntents(flow: Flow<Intent>): Flow<Intent> =
        if (!intentsIntercepted.compareAndSet(expectedValue = false, newValue = true)) flow
        else flow.onEach { observe(VIEObjectKind.Intent, intentType, it, injectedIntents.take(it)) }

    override fun interceptSignals(flow: Flow<Signal>): Flow<Signal> =
        if (!signalsIntercepted.compareAndSet(expectedValue = false, newValue = true)) flow
        else flow.onEach { observe(VIEObjectKind.Signal, signalType, it, injectedSignals.take(it)) }

    override fun onAttached(vm: VIEViewModel<Model, Intent, Signal>) {
        if (!attached.compareAndSet(expectedValue = false, newValue = true)) {
            sink.log.notice("TimeMachine: the middleware $viewModelId is already attached to a ViewModel, and cannot also report this ${vm.typeName}: its objects may even be reported as the other one's. Build one per ViewModel, and never share it (e.g. as a DI singleton).")
            return
        }
        // The probe stopped since it provided this middleware: nothing to report, nor to hold on to.
        if (sink.isClosed) return
        // Before any model is observed: the ViewModel starts its model flow only once every
        // middleware is attached.
        sink.log.onAppThread({ "report ViewModel start" }) { sink.started(infoOf(vm), intervenerOf(weakReference(vm))) }
        vm.addCloseable(AutoCloseable {
            closed.store(true)
            sink.log.onAppThread({ "report ViewModel close" }) { sink.closed(viewModelId) }
        })
    }

    private fun infoOf(vm: VIEViewModel<Model, Intent, Signal>) = VIEViewModelInfo(
        id = viewModelId,
        typeName = vm.typeName,
        model = modelType.toVIETypeInfo(),
        intent = intentType.toVIETypeInfo(),
        signal = signalType.toVIETypeInfo(),
    )

    /**
     * Handles the interventions in the ViewModel [viewModel] gives back, for as long as it does. It only captures
     * that weak reference, never the ViewModel: the sink's registry holds it until the ViewModel is
     * cleared, and one that never is must still be garbage-collected.
     */
    private fun intervenerOf(viewModel: () -> VIEViewModel<Model, Intent, Signal>?) = object : TimeMachineIntervener {
        override val isViewModelCollected: Boolean get() = viewModel() == null

        override fun intervene(request: TimeMachineServerMessage.Intervene): TimeMachineInterventionResult {
            val vm = viewModel()
                ?: return TimeMachineInterventionResult.Failure(Reason.ClosedViewModel, "ViewModel $viewModelId was garbage-collected without having been cleared")
            return intervene(TimeMachineViewModelInjector(vm), request)
        }
    }

    private fun intervene(injector: TimeMachineViewModelInjector<Model, Intent, Signal>, request: TimeMachineServerMessage.Intervene): TimeMachineInterventionResult {
        if (closed.load()) return closedFailure()
        val requestId = request.requestId
        return when (request.kind) {
            VIEObjectKind.Model -> modelType.decode(request) { model ->
                injector.injectModel(model) { injectedModels.add(Injected(model, requestId)) }.asResult()
            }
            VIEObjectKind.Intent -> intentType.decode(request) { intent ->
                injector.injectIntent(intent) { injectedIntents.add(Injected(intent, requestId)) }.asResult()
            }
            VIEObjectKind.Signal -> signalType.decode(request) { signal ->
                injector.injectSignal(signal) { injectedSignals.add(Injected(signal, requestId)) }?.asResult()
                    ?: TimeMachineInterventionResult.Failure(Reason.UnknownType, "${request.typeName} is neither a VIE.Signal.Event nor a VIE.Signal.Effect")
            }
        }
    }

    private fun closedFailure() = TimeMachineInterventionResult.Failure(Reason.ClosedViewModel, "ViewModel $viewModelId is closed")

    // An injection launched on a cleared ViewModel's scope is cancelled from the start: its object never enters.
    private fun Job.asResult(): TimeMachineInterventionResult = if (isCancelled) closedFailure() else TimeMachineInterventionResult.Success

    private inline fun <T> VIEObjectType<T>.decode(
        request: TimeMachineServerMessage.Intervene,
        inject: (T) -> TimeMachineInterventionResult,
    ): TimeMachineInterventionResult =
        when (val decoded = decodeForIntervention(request.typeName, request.asJson)) {
            is VIEObjectType.Decoded.Value -> inject(decoded.value)
            is VIEObjectType.Decoded.Refused -> decoded.failure
        }

    private fun <T : Any> observe(kind: VIEObjectKind, type: VIEObjectType<T>, value: T, interventionId: Uuid?) {
        // Only narrows the race with the close: a report already past this check can still follow it.
        if (closed.load()) return
        sink.log.onAppThread({ "report $kind" }) { sink.observed(viewModelId, kind, type, value, interventionId) }
    }
}

/**
 * The name of this ViewModel's class, for the inspector: fully qualified where the platform supports
 * it, simple for a local class, which has no qualified name.
 */
private val VIEViewModel<*, *, *>.typeName: String
    get() = this::class.let { it.displayName ?: it.simpleName ?: "<anonymous>" }

/** An object injected into a ViewModel, and the intervention request it answers. */
private class Injected<T>(val value: T, val requestId: Uuid)

/**
 * The models, intents or signals injected but not observed yet, oldest first. Several can wait at
 * once: two models injected back to back are both applied by the update loop before the model flow
 * observes the first one.
 *
 * Bounded: an object that is never observed must not be kept forever. That happens when the
 * ViewModel is cleared after the object is added but before it reaches the intercepted flow —
 * `update {}` emits its model after `entering`, `post()` relaunches its emit, and `signal()`
 * suspends on its shared flow — or when this middleware is not installed last, and another one
 * nearer the source drops or replaces the object.
 */
private class InjectedQueue<T> {
    // Copied on write: short, and only written by injections. Observing an object nobody injected —
    // nearly every one — only reads it.
    private val queue = AtomicReference<List<Injected<T>>>(emptyList())

    fun add(injected: Injected<T>) {
        queue.update { (if (it.size == ProbeTuning.maxPendingInjections) it.drop(1) else it) + injected }
    }

    /** The request that injected [value] itself — not an equal object — if any; forgotten once taken. */
    fun take(value: T): Uuid? {
        while (true) {
            val current = queue.load()
            val index = current.indexOfFirst { it.value === value }
            if (index < 0) return null
            if (queue.compareAndSet(current, current.filterIndexed { i, _ -> i != index })) return current[index].requestId
        }
    }
}
