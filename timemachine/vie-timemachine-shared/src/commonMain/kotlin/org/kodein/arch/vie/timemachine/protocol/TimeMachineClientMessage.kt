package org.kodein.arch.vie.timemachine.protocol

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * A message sent from a client (an application being inspected) to the TimeMachine server.
 */
@Serializable
public sealed interface TimeMachineClientMessage {

    /**
     * First frame of a connection: identifies the session and the wire protocol it speaks.
     *
     * Its envelope is frozen across all protocol versions:
     * `{"#type":"hello","protocolVersion":<Int>,"session":{"id":<UUID>,"name":<String>}}`. A later
     * version may add fields, never remove nor retype these, so that any later `Hello` still decodes
     * as this one and is rejected on its version. A first frame that does not decode as a `Hello` is
     * closed.
     *
     * @property protocolVersion Echoed against [TimeMachineConnection.protocolVersion]; a mismatch
     *   is answered with [TimeMachineServerMessage.Rejected].
     */
    @Serializable
    @SerialName("hello")
    public data class Hello(
        val session: TimeMachineSession,
        val protocolVersion: Int,
    ) : TimeMachineClientMessage

    /**
     * A VIE ViewModel was started on the client.
     *
     * @property sequence This message's position in the timeline (see [TimeMachineTimelineMessage]).
     */
    @Serializable
    @SerialName("vm-started")
    public data class ViewModelStarted(
        val info: VIEViewModelInfo,
        val at: Instant,
        override val sequence: TimeMachineSequence,
    ) : TimeMachineTimelineMessage

    /**
     * Describes a ViewModel: the answer to [TimeMachineServerMessage.RequestViewModelInfo], or sent
     * unsolicited for every live ViewModel after the client reconnects, since the server may have
     * restarted and lost them.
     *
     * Carries no timeline position: it neither starts the ViewModel nor places a row. A server that
     * did not know the ViewModel knows it from then on, started before the server saw its history.
     *
     * [info] is `null` only in an answer, when the client no longer knows a ViewModel with that
     * identifier.
     */
    @Serializable
    @SerialName("vm-info")
    public data class ViewModelInfo(
        val viewModelId: Uuid,
        val info: VIEViewModelInfo?,
    ) : TimeMachineClientMessage

    /**
     * A VIE ViewModel was closed on the client.
     *
     * @property sequence This message's position in the timeline (see [TimeMachineTimelineMessage]).
     */
    @Serializable
    @SerialName("vm-closed")
    public data class ViewModelClosed(
        val viewModelId: Uuid,
        val at: Instant,
        override val sequence: TimeMachineSequence,
    ) : TimeMachineTimelineMessage

    /**
     * A VIE object (`Model`, `Intent` or `Signal`) was observed on the client.
     *
     * @property sequence This message's position in the timeline (see [TimeMachineTimelineMessage]).
     */
    @Serializable
    @SerialName("observed")
    public data class Observed(
        val observation: VIEObservation,
        override val sequence: TimeMachineSequence,
    ) : TimeMachineTimelineMessage

    /**
     * Report of the outcome of an intervention.
     *
     * [requestId] echoes [TimeMachineServerMessage.Intervene.requestId].
     */
    @Serializable
    @SerialName("intervention-result")
    public data class InterventionResult(
        val requestId: Uuid,
        val result: VIEInterventionResult,
    ) : TimeMachineClientMessage

    /**
     * Reports that [count] timeline messages for [viewModelId] were dropped by the outbound queue
     * instead of being sent. It takes their place in the timeline: the dropped messages never got a
     * sequence, and leave no gap. [at] is when the drop is reported.
     *
     * [count] covers any [TimeMachineTimelineMessage], not only observations: the
     * [ViewModelStarted] or [ViewModelClosed] bookend of [viewModelId] may be among them.
     *
     * Unlike a gap in the session-wide sequence, it names its ViewModel: the probe sends one such
     * message per affected ViewModel, and keeps the count until the message is actually sent, so a
     * count is never lost.
     *
     * @property sequence This message's position in the timeline (see [TimeMachineTimelineMessage]).
     */
    @Serializable
    @SerialName("observations-dropped")
    public data class ObservationsDropped(
        val viewModelId: Uuid,
        val count: Int,
        val at: Instant,
        override val sequence: TimeMachineSequence,
    ) : TimeMachineTimelineMessage
}
