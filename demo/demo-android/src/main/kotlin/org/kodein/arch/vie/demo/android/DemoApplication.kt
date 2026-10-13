package org.kodein.arch.vie.demo.android

import android.app.Application
import android.content.pm.ApplicationInfo
import org.kodein.arch.vie.timemachine.client.TimeMachine
import org.kodein.arch.vie.timemachine.client.TimeMachineConfig


class DemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Started here and not in the Activity: once per process, and before the first ViewModel is built. A probe
        // started later only observes the ViewModels built after it.
        // The emulator reaches an inspector on this machine through `adb reverse tcp:13229 tcp:13229`.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        TimeMachine.start(TimeMachineConfig(appName = "Demo (Android)", enabled = debuggable))
    }
}
