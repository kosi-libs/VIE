package org.kodein.mvi.nomi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch


public abstract class MVIViewModel<Model : MVI.Model, Intent : MVI.Intent, Signal : MVI.Signal>(
    firstModel: Model
) : ViewModel(), MVI<Model, Intent, Signal> {

    private val mModelFlow = MutableStateFlow(firstModel)
    final override val modelFlow: StateFlow<Model> = mModelFlow.asStateFlow()

    protected val model: Model get() = mModelFlow.value

    private val intentChannel = Channel<Intent>()

    private val effectChannel = Channel<MVI.Signal.Effect /* & Signal */>()
    private val evenFlow = MutableSharedFlow<MVI.Signal.Event /* & Signal */>()

    @Suppress("UNCHECKED_CAST")
    override val signalFlow: Flow<Signal> get() = merge(
        effectChannel.receiveAsFlow() as Flow<Signal>,
        evenFlow  as Flow<Signal>,
    )

    private class Update<M>(
        val update: suspend M.() -> M,
        val job: CompletableJob,
    )

    private val updateChannel = Channel<Update<Model>>()

    init {
        viewModelScope.launch {
            intentChannel.consumeEach {
                process(it)
            }
        }
        viewModelScope.launch {
            updateChannel.consumeEach {
                mModelFlow.emit(it.update(mModelFlow.value))
                it.job.complete()
            }
        }
    }

    final override fun post(intent: Intent) {
        viewModelScope.launch {
            intentChannel.send(intent)
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
        when (signal) {
            is MVI.Signal.Effect -> effectChannel.send(signal)
            is MVI.Signal.Event -> evenFlow.emit(signal)
            is MVI.Signal.None -> error("Cannot send None signal")
        }
    }
}
