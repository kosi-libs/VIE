package org.kodein.arch.vie.demo

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.kodein.arch.vie.timemachine.client.TimeMachine
import org.kodein.arch.vie.timemachine.client.TimeMachineConfig
import platform.UIKit.UIViewController


@OptIn(ExperimentalNativeApi::class)
fun MainViewController(): UIViewController {
    // Started before the first ViewModel is built: a probe started later only observes the ViewModels built after it.
    // A debug binary stands for the debug build, which is what Xcode's Debug configuration links. The simulator reaches
    // an inspector on this Mac as `localhost`; a device needs the Mac's address, as the config's `host`.
    TimeMachine.start(TimeMachineConfig(appName = "Demo (iOS)", enabled = Platform.isDebugBinary))

    return ComposeUIViewController { App() }
}
