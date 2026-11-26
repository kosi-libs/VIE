import Shared
import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Before the first ViewModel is built: a probe started later only observes the ViewModels
        // built after it.
        DemoTimeMachineKt.startTimeMachine()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
