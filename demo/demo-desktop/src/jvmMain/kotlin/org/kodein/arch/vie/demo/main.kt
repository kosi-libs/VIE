package org.kodein.arch.vie.demo

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.singleWindowApplication
import org.kodein.arch.vie.timemachine.client.TimeMachine
import org.kodein.arch.vie.timemachine.client.TimeMachineConfig


fun main() {
    // Started before the first ViewModel is built: a probe started later only observes the ViewModels built after it.
    // The JVM has no build types, so the Gradle `run` task stands for the debug build (see build.gradle.kts).
    TimeMachine.start(TimeMachineConfig(appName = "Demo (desktop)", enabled = System.getProperty("vie.timemachine") != "false"))

    singleWindowApplication {
        App()
    }
}
