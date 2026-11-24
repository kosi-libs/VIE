package org.kodein.arch.vie.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewScope


/**
 * Reacts to signals for the lifetime of the composition: collects [VIEViewScope.signalFlow] and
 * invokes [onSignal] for each one.
 *
 * ```kotlin
 * SignalEffect { signal ->
 *     when (signal) {
 *         is Signal.LoggedOut -> goToLogin()
 *         is Signal.ShowError -> snackbarHostState.showSnackbar(signal.message)
 *     }
 * }
 * ```
 *
 * @param Signal The one-off type this scope's [VIEViewScope.signalFlow] emits.
 * @receiver The scope whose [VIEViewScope.signalFlow] is collected.
 * @param onSignal Called with each signal emitted while the composition is active; the latest
 *   composition's lambda is the one called.
 */
@Composable
public fun <Signal : VIE.Signal> VIEViewScope<*, Signal>.SignalEffect(
    onSignal: suspend (Signal) -> Unit
) {
    val currentOnSignal by rememberUpdatedState(onSignal)
    LaunchedEffect(Unit) {
        signalFlow.collect { signal ->
            currentOnSignal(signal)
        }
    }
}
