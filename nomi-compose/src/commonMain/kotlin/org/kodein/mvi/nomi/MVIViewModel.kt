package org.kodein.mvi.nomi

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


@OptIn(ExperimentalCoroutinesApi::class)
public abstract class MVIViewModel<Model : MVI.Model, Intent : MVI.Intent, Signal : MVI.Signal>(
    firstModel: Model,
    middlewares: List<MVIMiddleware<Model, Intent, Signal>> = emptyList(),
) : ViewModel(), MVI<Model, Intent, Signal> {

    final override val modelFlow: StateFlow<Model>

    protected val model: Model get() = modelFlow.value

    private val intentCollector: FlowCollector<Intent>

    private val signalCollector: FlowCollector<Signal>

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
        val effectChannel = Channel<MVI.Signal.Effect /* & Signal */>()
        val evenFlow = MutableSharedFlow<MVI.Signal.Event /* & Signal */>()
        viewModelScope.launch {
            allSignalsFlow.collect { signal ->
                when (signal) {
                    is MVI.Signal.Effect -> effectChannel.send(signal)
                    is MVI.Signal.Event -> evenFlow.emit(signal)
                    is MVI.Signal.None -> error("Cannot send None signal")
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        signalFlow = merge(
            effectChannel.receiveAsFlow() as Flow<Signal>,
            evenFlow  as Flow<Signal>,
        )
    }

    final override fun post(intent: Intent) {
        viewModelScope.launch {
            intentCollector.emit(intent)
        }
    }

    protected abstract suspend fun process(intent: Intent)

    public suspend fun update(update: suspend Model.() -> Model) {
        val job = Job()
        updateChannel.send(Update(update, job))
        job.join()
    }

    // https://youtrack.jetbrains.com/projects/KT/issues/KT-19215
    @Suppress("BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER")
    public suspend fun <S> signal(signal: S) where S : Signal, S : MVI.Signal.Type  {
        signalCollector.emit(signal)
    }
}
