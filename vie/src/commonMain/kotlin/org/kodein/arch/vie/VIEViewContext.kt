package org.kodein.arch.vie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow


public interface VIEViewContext<Intent : VIE.Intent, Signal : VIE.Signal> {
    public fun post(intent: Intent)
    public val signalFlow: Flow<Signal>
}

@Composable
public fun <Signal : VIE.Signal> VIEViewContext<*, Signal>.SignalEffect(
    onSignal: suspend (Signal) -> Unit
) {
    LaunchedEffect(Unit) {
        signalFlow.collect { signal ->
            onSignal(signal)
        }
    }
}
