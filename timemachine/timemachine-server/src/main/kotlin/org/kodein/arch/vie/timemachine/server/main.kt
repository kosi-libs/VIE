package org.kodein.arch.vie.timemachine.server

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.kodein.arch.vie.timemachine.server.store.TimeMachineStore
import org.kodein.arch.vie.timemachine.server.transport.TimeMachineConnections
import org.kodein.arch.vie.timemachine.server.transport.TimeMachineServer
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorSelection
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewModels
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.PaneVisibility
import org.kodein.arch.vie.timemachine.server.ui.theme.AppIconPainter
import kotlin.time.Clock

/**
 * The window's title is the packaged app's name (`packageName` in the build), and its icon the app's.
 */
fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val connections = TimeMachineConnections(scope)
    val store = TimeMachineStore(scope, execute = connections::execute)
    val server = TimeMachineServer(store, connections)
    val live = Inspector(
        store, InspectorSelection(), PaneVisibility(), InspectorViewModels(), Dispatchers.Default, Clock.System,
        retry = { scope.launch { server.retry() } },
    )
    scope.launch { server.start() }
    try {
        // Opened whatever the listener's outcome: a failure is shown, not thrown.
        application {
            val windowState = rememberWindowState(size = DpSize(1440.dp, 900.dp))
            val viewState = remember { InspectorViewState() }
            Window(
                onCloseRequest = ::exitApplication,
                title = "VIE TimeMachine",
                icon = AppIconPainter,
                state = windowState,
            ) {
                InspectorWindow(live, viewState)
            }
        }
    } finally {
        live.close()
        server.stop()
        scope.cancel()
    }
}
