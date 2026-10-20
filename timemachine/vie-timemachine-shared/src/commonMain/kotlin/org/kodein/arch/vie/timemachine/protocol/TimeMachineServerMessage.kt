package org.kodein.arch.vie.timemachine.protocol

import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


/**
 * A message sent from the TimeMachine server to a client (an application being inspected).
 */
@Serializable
public sealed interface TimeMachineServerMessage {

    /** Answer to a [TimeMachineClientMessage.Hello] the server accepts. */
    @Serializable
    @SerialName("welcome")
    public data object Welcome : TimeMachineServerMessage

    /**
     * Answer to a [TimeMachineClientMessage.Hello] the server refuses. The connection is closed
     * after this message.
     *
     * Its envelope is frozen across all protocol versions, like `Hello`'s:
     * `{"#type":"rejected","protocolVersion":<Int>}`. A server rejects for one cause per protocol
     * version, so the two versions tell the client why; a new cause bumps the version.
     *
     * @property protocolVersion The server's own [TimeMachineConnection.protocolVersion].
     */
    @Serializable
    @SerialName("rejected")
    public data class Rejected(
        val protocolVersion: Int,
    ) : TimeMachineServerMessage

    /**
     * Acknowledges the [TimeMachineTimelineMessage]s this connection carried, up to [sequence]: the
     * server has applied every one of them, whether it kept it or already held it.
     *
     * A connection carries its timeline messages in ascending [TimeMachineTimelineMessage.sequence]
     * order, so the acknowledgement is cumulative. The client keeps each timeline message it sent
     * until one covers it, and sends the unacknowledged ones again on its next connection.
     */
    @Serializable
    @SerialName("received")
    public data class Received(
        val sequence: TimeMachineSequence,
    ) : TimeMachineServerMessage

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
     * @property typeName The serial name of the object's concrete type, as
     *   [VIETypeInfo.sealedSubTypes] and [VIEObservation.typeName] report it.
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
