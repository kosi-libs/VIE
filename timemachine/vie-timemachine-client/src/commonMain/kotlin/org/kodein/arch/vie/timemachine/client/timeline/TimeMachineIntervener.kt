package org.kodein.arch.vie.timemachine.client.timeline

import org.kodein.arch.vie.timemachine.protocol.TimeMachineInterventionResult
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage


/**
 * Makes one live ViewModel emit or receive the object an intervention request describes.
 *
 * The sink's registry holds it for as long as its ViewModel is not cleared, so it must not keep that
 * ViewModel reachable: one that is never cleared would otherwise never be garbage-collected.
 */
internal fun interface TimeMachineIntervener {
    /** Injects [request]'s object, or says why it cannot. Never throws nor suspends. */
    fun intervene(request: TimeMachineServerMessage.Intervene): TimeMachineInterventionResult

    /** Whether the ViewModel was garbage-collected without ever being cleared: it then reports nothing
     *  more, its close included, and nothing can be injected into it. */
    val isViewModelCollected: Boolean get() = false
}
