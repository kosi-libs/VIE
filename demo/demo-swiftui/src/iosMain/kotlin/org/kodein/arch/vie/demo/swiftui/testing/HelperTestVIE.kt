package org.kodein.arch.vie.demo.swiftui.testing

import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.demo.swiftui.testing.HelperTestVIE.Intent
import org.kodein.arch.vie.demo.swiftui.testing.HelperTestVIE.Model
import org.kodein.arch.vie.demo.swiftui.testing.HelperTestVIE.Signal


/**
 * The contract of [HelperTestVM], which the tests of the Swift helpers drive. It is no part of the
 * demo: the helpers start with `import Shared`, so what their tests need from Kotlin has to live
 * in this framework, and a `VIEViewScope` cannot be written in Swift, as it exposes a `Flow`.
 */
interface HelperTestVIE : VIE<Model, Intent, Signal> {

    data class Model(val count: Int) : VIE.Model

    sealed interface Intent : VIE.Intent {
        /** Adds one to [Model.count]. */
        data object Increment : Intent
        /** Emits [Signal.Shown] with the same [id]. */
        data class Show(val id: Int) : Intent
    }

    sealed interface Signal : VIE.Signal {
        /** An Effect: it waits for a collector, so a test can post it before the view collects. */
        data class Shown(val id: Int) : Signal, VIE.Signal.Effect
    }
}

/** Does what each [Intent] says, and counts how many times it was cleared. */
class HelperTestVM : HelperTestVIE, VIEViewModel<Model, Intent, Signal>(Model(count = 0)) {

    var clearedCount: Int = 0
        private set

    override suspend fun process(intent: Intent) {
        when (intent) {
            Intent.Increment -> update { copy(count = count + 1) }
            is Intent.Show -> signal(Signal.Shown(intent.id))
        }
    }

    override fun onCleared() {
        clearedCount++
    }
}
