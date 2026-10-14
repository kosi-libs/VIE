package org.kodein.arch.vie.timemachine.protocol

import kotlinx.serialization.Serializable


/**
 * How a `VIE.Signal` reaches its view: the delivery kind its concrete type picked.
 *
 * It travels on the observation of a Signal, not on [VIETypeInfo], because a serial descriptor does
 * not tell an Event from an Effect: only the instance does.
 * The inspector needs it to warn about an intervention: an [Effect] is delivered to exactly one
 * collector, so injecting one into a ViewModel no view collects blocks that ViewModel's signals, and
 * then the `process` that emits the next one.
 */
@Serializable
public enum class VIESignalDelivery {
    /** `VIE.Signal.Event`: broadcast to every active collector, dropped when there is none. */
    Event,

    /** `VIE.Signal.Effect`: delivered to exactly one collector, waiting for one if there is none. */
    Effect,
}
