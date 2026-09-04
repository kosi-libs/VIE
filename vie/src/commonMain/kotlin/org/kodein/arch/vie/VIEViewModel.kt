package org.kodein.arch.vie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


/**
 * The abstract ViewModel base: implements [VIE], built on `androidx.lifecycle.ViewModel` rather
 * than beside it, so `viewModelScope`, `viewModel { }` keying and Navigation3 entry scoping all
 * work unchanged. Subclass it, pass [firstModel] and any [middlewares] up, and implement
 * [process].
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
 * @param firstModel The model [modelFlow] starts with. A model is always complete, so `modelFlow`
 *   never has to represent "nothing yet".
 * @param middlewares Intercepts the model, intent and signal flows, closest-to-source first: the
 *   list is folded from the end, so the *last* middleware wraps the raw source flow and the
 *   *first* sits closest to the ViewModel and view.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public abstract class VIEViewModel<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    firstModel: Model,
    middlewares: List<VIEMiddleware<Model, Intent, Signal>> = emptyList(),
) : ViewModel(), VIE<Model, Intent, Signal> {

    /** The current state of the screen, after all [middlewares] have run. */
    final override val modelFlow: StateFlow<Model>

    /** The current model — shorthand for `modelFlow.value`. */
    protected val model: Model get() = modelFlow.value

    private val intentCollector: FlowCollector<Intent>

    private val signalCollector: FlowCollector<Signal>

    /** The stream of one-off [Signal]s the view should react to but not render. */
    final override val signalFlow: Flow<Signal>

    private class Update<M>(
        val update: suspend M.() -> M,
        val job: CompletableJob,
    )

    private val updateChannel = Channel<Update<Model>>()

    init {
        val modelCollector = MutableStateFlow(firstModel)
        modelFlow = middlewares.foldRight(modelCollector.asFlow()) { middleware, flow -> middleware.interceptModels(flow) }
            .stateIn(viewModelScope, SharingStarted.Lazily, firstModel)
        viewModelScope.launch {
            updateChannel.consumeEach {
                modelCollector.emit(it.update(modelFlow.value))
                it.job.complete()
            }
        }

        intentCollector = MutableSharedFlow()
        val intentFlow = middlewares.foldRight(intentCollector.asFlow()) { middleware, flow -> middleware.interceptIntents(flow) }
        viewModelScope.launch {
            intentFlow.collect {
                process(it)
            }
        }

        signalCollector = MutableSharedFlow()
        val allSignalsFlow = middlewares.foldRight(signalCollector.asFlow()) { middleware, flow -> middleware.interceptSignals(flow) }
        val effectChannel = Channel<VIE.Signal.Effect /* & Signal */>()
        val evenFlow = MutableSharedFlow<VIE.Signal.Event /* & Signal */>()
        viewModelScope.launch {
            allSignalsFlow.collect { signal ->
                when (signal) {
                    is VIE.Signal.Effect -> effectChannel.send(signal)
                    is VIE.Signal.Event -> evenFlow.emit(signal)
                    is VIE.Signal.None -> error("Cannot send None signal")
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        signalFlow = merge(
            effectChannel.receiveAsFlow() as Flow<Signal>,
            evenFlow  as Flow<Signal>,
        )
    }

    /** Feeds [intent] into the intent pipeline, to be handled by [process]. Not overridable. */
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
     * @param update Computes the next model from the current one.
     */
    public suspend fun update(update: suspend Model.() -> Model) {
        val job = Job()
        updateChannel.send(Update(update, job))
        job.join()
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
    public suspend fun <S> signal(signal: S) where S : Signal, S : VIE.Signal.Type  {
        signalCollector.emit(signal)
    }
}
