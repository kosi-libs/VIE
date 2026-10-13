package org.kodein.arch.vie.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.kodein.arch.vie.timemachine.client.TimeMachine
import org.kodein.arch.vie.timemachine.client.TimeMachineConfig


@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Started before the first ViewModel is built: a probe started later only observes the ViewModels built after it.
    // A browser has no build types, so a page served from localhost stands for the debug build: that is also the only
    // page whose `localhost` is the machine the inspector runs on.
    TimeMachine.start(TimeMachineConfig(appName = "Demo (web)", enabled = window.location.hostname == "localhost"))

    ComposeViewport(document.body!!) {
        App()
    }
}
