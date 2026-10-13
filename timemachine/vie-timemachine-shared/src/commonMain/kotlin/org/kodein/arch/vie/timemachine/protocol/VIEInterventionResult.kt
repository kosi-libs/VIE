package org.kodein.arch.vie.timemachine.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * The client's answer to an intervention request, sent as soon as the request is handled — before
 * the object reaches the ViewModel.
 *
 * It says whether the request was accepted, not whether the object was delivered: that the object
 * entered the ViewModel is shown by a [VIEObservation] whose [VIEObservation.interventionId] is the
 * request's [TimeMachineServerMessage.Intervene.requestId].
 */
@Serializable
public sealed interface VIEInterventionResult {

    /**
     * The request was valid, and the object's injection into the ViewModel was scheduled.
     *
     * Not a proof of delivery: the ViewModel may be cleared before the injection runs, and an
     * injected model that equals the current one, or is conflated away, is applied but never
     * observed.
     */
    @Serializable
    @SerialName("success")
    public data object Success : VIEInterventionResult

    /** The request was refused: nothing was placed into the ViewModel. */
    @Serializable
    @SerialName("failure")
    public data class Failure(
        val reason: Reason,
        val message: String? = null,
    ) : VIEInterventionResult

    @Serializable
    public enum class Reason {
        /** No ViewModel with the given identifier is known. */
        UnknownViewModel,

        /** The ViewModel was closed in the meantime. */
        ClosedViewModel,

        /** The targeted role (`Model`, `Intent` or `Signal`) was reported as not `@Serializable`. */
        NotSerializable,

        /** The given type name is not a known (subtype of the) targeted role. */
        UnknownType,

        /** The given JSON representation could not be decoded. */
        BadJson,
    }
}
