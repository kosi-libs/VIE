package org.kodein.arch.vie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch


/**
 * Abstract ViewModel implementing [VIE], built on `androidx.lifecycle.ViewModel` so
 * `viewModelScope`, `viewModel { }` and Navigation3 entry scoping work unchanged.
 *
 * ```kotlin
 * class CounterVM(
 *     private val store: CounterStore,
 * ) : CounterVIE, VIEViewModel<CounterVIE.Model, CounterVIE.Intent, CounterVIE.Signal>(
 *     firstModel = CounterVIE.Model(count = 0),
 * ) {
 *     override suspend fun process(intent: CounterVIE.Intent) {
 *         when (intent) {
 *             is CounterVIE.Intent.Increment -> update { copy(count = count + 1) }
 *         }
 *     }
 * }
 * ```
 *
 * @param Model The state the view renders.
 * @param Intent The events the view sends up.
 * @param Signal The one-offs the view reacts to but does not render.
 * @param firstModel The model [modelFlow] starts with.
 * @param middlewares Intercept the flows; the last is closest to where values come from, the first to where they go.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public abstract class VIEViewModel<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    firstModel: Model,
    middlewares: List<VIEMiddleware<Model, Intent, Signal>> = emptyList(),
) : ViewModel(), VIE<Model, Intent, Signal> {

    /** The current model, after every middleware. Kept up to date with or without a collector; conflated. */
    final override val modelFlow: StateFlow<Model>

    /** The latest model [update] applied, before any middleware: current as soon as `update` returns. */
    protected val model: Model get() = modelCollector.replayCache.last()

    private val intentCollector: FlowCollector<Intent>

    private val signalCollector: FlowCollector<Signal>

    /** The stream of one-off [Signal]s the view should react to but not render. */
    final override val signalFlow: Flow<Signal>

    private class Update<M>(
        val update: suspend M.() -> M,
        val result: CompletableDeferred<Unit>,
    )

    private val updateChannel = Channel<Update<Model>>()

    // Not a StateFlow: middlewares must see every model, equal ones included. The replay slot is the current model.
    // Unbounded extra buffer, so `emit` never waits on a middleware: `update` must not depend on the pipeline's pace.
    private val modelCollector = MutableSharedFlow<Model>(
        replay = 1,
        extraBufferCapacity = Channel.UNLIMITED,
    ).apply {
        tryEmit(firstModel)
    }

    init {
        middlewares.forEach { middleware ->
            middleware.onAttached(this)
        }

        val modelState = MutableStateFlow(firstModel)
        modelFlow = modelState.asStateFlow()
        // Launched before the update loop, so it subscribes to modelCollector before any update is emitted.
        viewModelScope.launch {
            middlewares.foldRight<_, Flow<Model>>(modelCollector) { middleware, flow ->
                middleware.interceptModels(flow)
            }.collect { modelState.value = it }
        }

        viewModelScope.launch {
            updateChannel.consumeEach {
                // `model`, not `modelFlow.value`, which lags behind and has been through the middlewares.
                val next = try {
                    it.update(model)
                } catch (e: Throwable) {
                    // The block's failure is its caller's: the loop must keep serving later updates.
                    it.result.completeExceptionally(e)
                    currentCoroutineContext().ensureActive()
                    return@consumeEach
                }
                modelCollector.emit(next)
                it.result.complete(Unit)
            }
        }

        intentCollector = MutableSharedFlow()
        val intentFlow =
            middlewares.foldRight(intentCollector.asFlow()) { middleware, flow -> middleware.interceptIntents(flow) }
        viewModelScope.launch {
            intentFlow.collect {
                process(it)
            }
        }

        signalCollector = MutableSharedFlow()
        val allSignalsFlow =
            middlewares.foldRight(signalCollector.asFlow()) { middleware, flow -> middleware.interceptSignals(flow) }
        // An Effect waits in a queue shared by every collector, not in the dispatch loop: later signals,
        // Events included, and `signal` callers do not wait behind it. A collector takes an Effect out of
        // the queue only at the moment it hands it downstream, never ahead of time: a collector that is
        // cancelled while it handles an Effect loses that one, and none of the Effects queued behind it.
        val effects = EffectQueue()
        // Events, and the markers that tell a collector an Effect is waiting, go through one SharedFlow,
        // so that a collector sees them in the order they were emitted. Its buffer is unbounded, so that
        // the dispatch loop never waits on a collector: one that never returns holds what follows it in memory.
        val deliveries = MutableSharedFlow<Delivery>(extraBufferCapacity = Channel.UNLIMITED)
        viewModelScope.launch {
            allSignalsFlow.collect { signal ->
                when (signal) {
                    // Queued before it is announced: a collector that sees the marker finds the Effect.
                    is VIE.Signal.Effect -> deliveries.emit(Delivery.EffectQueued(effects.enqueue(signal)))
                    is VIE.Signal.Event -> deliveries.emit(Delivery.Event(signal))
                    is VIE.Signal.None -> error("Cannot send None signal")
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        signalFlow = deliveries
            // The Effects queued before this collector subscribed are handed to it first. Those queued
            // since arrive with their marker, which keeps their place among the Events.
            .onSubscription { emit(Delivery.Subscribed(effects.lastSeq)) }
            .transform { delivery ->
                when (delivery) {
                    is Delivery.Event -> emit(delivery.event as Signal)
                    is Delivery.EffectQueued -> effects.takeUpTo(delivery.seq)?.let { emit(it as Signal) }
                    is Delivery.Subscribed -> while (true) {
                        // A handler that swallowed its cancellation returns normally: stop before taking
                        // an Effect that would be dropped on its way downstream.
                        currentCoroutineContext().ensureActive()
                        emit((effects.takeUpTo(delivery.lastSeq) ?: break) as Signal)
                    }
                }
            }
    }

    /** Sends [intent] through the middlewares to [process]. */
    final override fun post(intent: Intent) {
        viewModelScope.launch {
            intentCollector.emit(intent)
        }
    }

    /**
     * Called for every posted [intent], one at a time, on `viewModelScope`. Long or parallel work
     * should be launched into `viewModelScope` explicitly rather than blocking the intent stream.
     *
     * @param intent The intent to handle.
     */
    protected abstract suspend fun process(intent: Intent)

    /**
     * Atomically replaces the model: hands [update] to a single consumer coroutine and suspends
     * until it has been applied. Serialized against every other call to this function, so two
     * concurrent calls cannot lose each other's changes.
     *
     * Throws whatever [update] throws, leaving the model unchanged.
     *
     * @param update Computes the next model from the current one.
     */
    public suspend fun update(update: suspend Model.() -> Model) {
        val result = CompletableDeferred<Unit>()
        updateChannel.send(Update(update, result))
        result.await()
    }

    /**
     * Emits [signal]. The `where` bound forces [S] to be a [VIE.Signal.Event] or a
     * [VIE.Signal.Effect] — a signal type that picked neither will not compile at the call site.
     *
     * @param S The concrete signal type, constrained to have picked a delivery kind.
     * @param signal The signal to emit.
     */
    // https://youtrack.jetbrains.com/projects/KT/issues/KT-19215
    @Suppress("BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER")
    public suspend fun <S> signal(signal: S) where S : Signal, S : VIE.Signal.Type {
        signalCollector.emit(signal)
    }
}

/**
 * The Effects no collector has taken yet, shared by all of them, each numbered in the order it was
 * queued. Taking is atomic: an Effect is handed to exactly one collector.
 */
private class EffectQueue {

    private class Item(val seq: Long, val effect: VIE.Signal.Effect)

    // Compared by identity, not by content: `compareAndSet` below succeeds only if no one else changed
    // the queue since it was read, which is what makes a take atomic.
    private class Snapshot(val lastSeq: Long, val items: List<Item>)

    // A StateFlow only for its atomic compare-and-set: nothing collects it.
    private val state = MutableStateFlow(Snapshot(lastSeq = 0, items = emptyList()))

    /** The number of the last Effect queued, `0` if none was. */
    val lastSeq: Long get() = state.value.lastSeq

    /** Queues [effect] behind the others, and returns its number. */
    fun enqueue(effect: VIE.Signal.Effect): Long =
        state.updateAndGet { Snapshot(it.lastSeq + 1, it.items + Item(it.lastSeq + 1, effect)) }.lastSeq

    /** Takes the oldest Effect if its number is at most [seq]: the later ones are left to their own marker. */
    fun takeUpTo(seq: Long): VIE.Signal.Effect? {
        while (true) {
            val snapshot = state.value
            val head = snapshot.items.firstOrNull()?.takeIf { it.seq <= seq } ?: return null
            if (state.compareAndSet(snapshot, Snapshot(snapshot.lastSeq, snapshot.items.drop(1)))) return head.effect
        }
    }
}

/** What a collector of `signalFlow` is told, in order. */
private sealed interface Delivery {
    /** An Event, for every collector. */
    class Event(val event: VIE.Signal.Event) : Delivery
    /** The Effect numbered [seq] is waiting: the collector that takes it from the queue handles it. */
    class EffectQueued(val seq: Long) : Delivery
    /** First thing a collector is told: the Effects up to [lastSeq] were queued before it subscribed. */
    class Subscribed(val lastSeq: Long) : Delivery
}
