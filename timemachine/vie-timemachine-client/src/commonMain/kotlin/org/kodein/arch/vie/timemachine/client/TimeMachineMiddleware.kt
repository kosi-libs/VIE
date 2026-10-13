package org.kodein.arch.vie.timemachine.client

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
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult.Reason
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo


/**
 * The probe's middleware: reports one ViewModel's start, every model, intent and signal that passes
 * through it, and its close, to [sink]; and places the objects the inspector intervenes with into
 * that ViewModel.
 *
 * - *Started* is reported when the middleware is attached to its ViewModel, not when it is built:
 *   a middleware built but never installed reports nothing. The ViewModel is named after its own
 *   class, read once attached: a subclass is named after itself, not after the class that installed
 *   the middleware. *Closed* is reported when the ViewModel is cleared — through a closeable
 *   registered on it, not the intent flow's completion, which also happens when `process()` throws
 *   or a middleware ends the flow while the ViewModel lives on.
 * - Intents and signals are observed in their intercepts, so their order is exactly the order the
 *   ViewModel handles them. The signal flow itself is never collected: that would steal effects
 *   from the view.
 * - Models are observed from [onModelUpdated], which the ViewModel calls after each `update {}`: from
 *   the start, with no view collecting, and every intermediate model of a burst, none conflated. The
 *   first model, which no `update {}` produced, is reported once attached.
 * - Interventions go through a [VIEViewModelInjector] — the ViewModel's own public `update {}`,
 *   `post()` and `signal()` — so an injected model is the one the next `update { copy(…) }` computes
 *   from. An injected object is recognised by identity when it is observed, and tagged with its
 *   request's identifier. An injected model is always tagged, equal to the current one or not: it
 *   is injected inside the ViewModel's own `update {}`, which then calls [onModelUpdated] with that
 *   very instance. An injected data object, though, is not tagged when an app-produced instance of
 *   it, the very same object, overtakes it.
 *
 * Belongs *last* in `middlewares`, for intents and signals: that puts the middleware nearest the
 * source, so it reports signals as the ViewModel emitted them, and intents as the view posted them —
 * before any other middleware transforms, filters or maps them. An intent another middleware drops
 * is still reported, though `process()` never receives it. Models are reported as `update {}`
 * produced them wherever it sits.
 *
 * As [VIEMiddleware.onAttached] requires, an instance serves exactly one ViewModel: the first it is
 * installed in. Installed in another one too (e.g. as a DI singleton), it prints a notice and
 * ignores that ViewModel's start, close, intents and signals. Its models are still reported under
 * the first one's identity, since [onModelUpdated] does not say which ViewModel updated: sharing an
 * instance remains a mistake.
 *
 * Never throws into the host app: a failure to report an object is printed and swallowed. Nor does
 * it describe objects on the ViewModel's dispatcher: [sink] defers that (see [TimeMachineSink]).
 */
internal class TimeMachineMiddleware<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    private val modelRole: VIERole<Model>,
    private val intentRole: VIERole<Intent>,
    private val signalRole: VIERole<Signal>,
    private val sink: TimeMachineSink,
) : VIEMiddleware<Model, Intent, Signal> {

    val viewModelId: Uuid = Uuid.random()

    private val closed = AtomicBoolean(false)

    // One per hook, which a ViewModel calls once each, in this order: only the first ViewModel's calls
    // take effect.
    private val intentsIntercepted = AtomicBoolean(false)
    private val signalsIntercepted = AtomicBoolean(false)
    private val attached = AtomicBoolean(false)

    // Injected objects on their way into the ViewModel, by the request that placed them. Touched by the
    // injector's `entering` callbacks and by observation: several of `viewModelScope`'s coroutines,
    // which run on several threads when its dispatcher does (e.g. `Dispatchers.Default`, the fallback
    // when no Main dispatcher is available).
    private val injectedModel = AtomicReference<Injected<Model>?>(null)
    private val injectedIntents = InjectedQueue<Intent>()
    private val injectedSignals = InjectedQueue<Signal>()

    override fun interceptIntents(flow: Flow<Intent>): Flow<Intent> =
        if (!intentsIntercepted.compareAndSet(expectedValue = false, newValue = true)) flow
        else flow.onEach { observe(VIEObjectKind.Intent, intentRole, it, injectedIntents.take(it)) }

    override fun interceptSignals(flow: Flow<Signal>): Flow<Signal> =
        if (!signalsIntercepted.compareAndSet(expectedValue = false, newValue = true)) flow
        else flow.onEach { observe(VIEObjectKind.Signal, signalRole, it, injectedSignals.take(it)) }

    override fun onAttached(vm: VIEViewModel<Model, Intent, Signal>) {
        if (!attached.compareAndSet(expectedValue = false, newValue = true)) {
            println("TimeMachine: the middleware $viewModelId already observes a ViewModel, and ignores this ${vm.typeName}: build one per ViewModel, and never share it (e.g. as a DI singleton).")
            return
        }
        val injector = VIEViewModelInjector(vm)
        // First: the observation of the first model, reported below, is of a ViewModel the
        // inspector has to know already.
        guarded({ "report ViewModel start" }) { sink.started(infoOf(vm)) { intervene(injector, it) } }
        vm.addCloseable(AutoCloseable {
            closed.store(true)
            guarded({ "report ViewModel close" }) { sink.closed(viewModelId) }
        })
        // The ViewModel is not constructed yet, so no `update {}` ran: this is its first model.
        observeModel(vm.modelFlow.value)
    }

    override fun onModelUpdated(model: Model) = observeModel(model)

    // The next model observed after an injection is the injected one: it is applied inside the
    // `update {}` that calls `onModelUpdated` right after.
    private fun observeModel(model: Model) {
        val interventionId = injectedModel.exchange(null)?.takeIf { it.value === model }?.requestId
        observe(VIEObjectKind.Model, modelRole, model, interventionId)
    }

    private fun infoOf(vm: VIEViewModel<Model, Intent, Signal>) = VIEViewModelInfo(
        id = viewModelId,
        typeName = vm.typeName,
        model = modelRole.toVIETypeInfo(),
        intent = intentRole.toVIETypeInfo(),
        signal = signalRole.toVIETypeInfo(),
    )

    private fun intervene(injector: VIEViewModelInjector<Model, Intent, Signal>, request: TimeMachineServerMessage.Intervene): VIEInterventionResult {
        if (closed.load()) return closedFailure()
        val requestId = request.requestId
        return when (request.kind) {
            VIEObjectKind.Model -> modelRole.decode(request) { model ->
                injector.injectModel(model) { injectedModel.store(Injected(model, requestId)) }.asResult()
            }
            VIEObjectKind.Intent -> intentRole.decode(request) { intent ->
                injector.injectIntent(intent) { injectedIntents.add(Injected(intent, requestId)) }.asResult()
            }
            VIEObjectKind.Signal -> signalRole.decode(request) { signal ->
                injector.injectSignal(signal) { injectedSignals.add(Injected(signal, requestId)) }?.asResult()
                    ?: VIEInterventionResult.Failure(Reason.UnknownType, "${request.typeName} is neither a VIE.Signal.Event nor a VIE.Signal.Effect")
            }
        }
    }

    private fun closedFailure() = VIEInterventionResult.Failure(Reason.ClosedViewModel, "ViewModel $viewModelId is closed")

    // An injection launched on a cleared ViewModel's scope is cancelled from the start: its object never enters.
    private fun Job.asResult(): VIEInterventionResult = if (isCancelled) closedFailure() else VIEInterventionResult.Success

    private inline fun <T> VIERole<T>.decode(
        request: TimeMachineServerMessage.Intervene,
        inject: (T) -> VIEInterventionResult,
    ): VIEInterventionResult =
        when (val decoded = decodeForIntervention(request.typeName, request.asJson)) {
            is VIERole.Decoded.Value -> inject(decoded.value)
            is VIERole.Decoded.Refused -> decoded.failure
        }

    private fun <T : Any> observe(kind: VIEObjectKind, role: VIERole<T>, value: T, interventionId: Uuid?) {
        guarded({ "report $kind" }) { sink.observed(viewModelId, kind, role, value, interventionId) }
    }
}

/**
 * The name of this ViewModel's class, for the inspector: fully qualified where the platform supports
 * it, simple for a local class, which has no qualified name.
 */
private val VIEViewModel<*, *, *>.typeName: String
    get() = this::class.let { it.displayName ?: it.simpleName ?: "<anonymous>" }

/** An object an intervention placed into a ViewModel, and the request that placed it. */
private class Injected<T>(val value: T, val requestId: Uuid)

/**
 * The intents or signals injected but not observed yet, oldest first. Bounded: an object that is
 * never observed must not be kept forever. That happens when the ViewModel is cleared after the
 * object is added but before it reaches the intercepted flow — `post()` relaunches its emit, and
 * `signal()` suspends on its shared flow — or when this middleware is not installed last, and
 * another one nearer the source drops the object.
 */
private class InjectedQueue<T> {
    // Copied on write: short, and only written by injections. Observing an object nobody injected —
    // nearly every one — only reads it.
    private val queue = AtomicReference<List<Injected<T>>>(emptyList())

    fun add(injected: Injected<T>) {
        queue.update { (if (it.size == 32) it.drop(1) else it) + injected }
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
