package org.kodein.arch.vie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow


public interface VIEViewScope<Intent : VIE.Intent, Signal : VIE.Signal> {
    public fun post(intent: Intent)
    public val signalFlow: Flow<Signal>
}

@Composable
public fun <Signal : VIE.Signal> VIEViewScope<*, Signal>.SignalEffect(
    onSignal: suspend (Signal) -> Unit
) {
    LaunchedEffect(Unit) {
        signalFlow.collect { signal ->
            onSignal(signal)
        }
    }
}
