package org.kodein.arch.vie.timemachine.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspectorViewState
import org.kodein.arch.vie.timemachine.server.ui.shell.InspectorShell
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/** The window's content: the theme and the window's locals around the inspector. */
@Composable
internal fun InspectorWindow(inspector: Inspector, viewState: InspectorViewState) {
    InspectorTheme(viewState.dark) {
        CompositionLocalProvider(LocalInspector provides inspector, LocalInspectorViewState provides viewState) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceDim)) {
                InspectorShell()
            }
        }
    }
}
