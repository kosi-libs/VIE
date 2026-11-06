package org.kodein.arch.vie.timemachine.client

import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEMiddleware


/**
 * What [TimeMachine.middleware] returns while the probe does not run: it passes every flow through
 * untouched, and never looks at the ViewModel it is attached to. It holds no state, so one instance
 * serves every ViewModel, and a disabled probe allocates nothing.
 */
@PublishedApi
internal object NoOpMiddleware : VIEMiddleware<VIE.Model, VIE.Intent, VIE.Signal> {

    /** This middleware, for a ViewModel of any roles: it never touches a model, intent or signal. */
    @Suppress("UNCHECKED_CAST")
    fun <Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> typed(): VIEMiddleware<Model, Intent, Signal> =
        this as VIEMiddleware<Model, Intent, Signal>
}
