package org.kodein.arch.vie.tm.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * The client's report back to the server after an intervention attempt.
 */
@Serializable
public sealed interface VIEInterventionResult {

    /** The object was successfully emitted to / delivered to the ViewModel. */
    @Serializable
    @SerialName("success")
    public data object Success : VIEInterventionResult

    /** The object could not be emitted / delivered. */
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
