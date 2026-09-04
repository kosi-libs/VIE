package org.kodein.arch.vie.tm.protocol

import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


/**
 * A message sent from the TimeMachine server to a client (an application being inspected).
 */
@Serializable
public sealed interface TimeMachineServerMessage {

    /**
     * Requests the full [VIEViewModelInfo] of a ViewModel the server received an observation about
     * but does not know.
     */
    @Serializable
    @SerialName("request-vm-info")
    public data class RequestViewModelInfo(
        val viewModelId: Uuid,
    ) : TimeMachineServerMessage

    /**
     * Asks the client to make a ViewModel emit or receive an object.
     *
     * Only valid if the matching role was reported [VIETypeInfo.isSerializable]. The client answers
     * with a [TimeMachineClientMessage.InterventionResult] carrying the same [requestId].
     *
     * @property viewModelId The ViewModel that will emit or receive the object.
     * @property kind Whether the object is a `Model`, an `Intent` or a `Signal`.
     * @property typeName The fully qualified type of the object.
     * @property asJson The JSON representation of the object.
     */
    @Serializable
    @SerialName("intervene")
    public data class Intervene(
        val requestId: Uuid,
        val viewModelId: Uuid,
        val kind: VIEObjectKind,
        val typeName: String,
        val asJson: JsonElement,
    ) : TimeMachineServerMessage
}
