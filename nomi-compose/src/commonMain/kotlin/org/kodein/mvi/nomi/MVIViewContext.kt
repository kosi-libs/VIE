package org.kodein.mvi.nomi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow


public interface MVIViewContext<Intent : MVI.Intent, Signal : MVI.Signal> {
    public fun post(intent: Intent)
    public val signalFlow: Flow<Signal>
}

@Composable
public fun <Signal : MVI.Signal> MVIViewContext<*, Signal>.SignalEffect(
    onSignal: suspend (Signal) -> Unit
) {
    LaunchedEffect(Unit) {
        signalFlow.collect { signal ->
            onSignal(signal)
        }
    }
}
