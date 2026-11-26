package org.kodein.arch.vie.demo.swiftui

import org.kodein.arch.vie.timemachine.client.TimeMachine
import org.kodein.arch.vie.timemachine.client.TimeMachineConfig
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform


/**
 * Starts the TimeMachine probe, for the SwiftUI app to call when it is created.
 *
 * It has to run before the first ViewModel is built: a probe started later only observes the
 * ViewModels built after it. A debug binary stands for the debug build, which is what Xcode's Debug
 * configuration links. The simulator reaches an inspector on this Mac as `localhost`; a device needs
 * the Mac's address, as the config's `host`.
 */
@OptIn(ExperimentalNativeApi::class)
fun startTimeMachine() {
    TimeMachine.start(TimeMachineConfig(appName = "Demo (SwiftUI)", enabled = Platform.isDebugBinary))
}
