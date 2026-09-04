package org.kodein.arch.vie.tm.protocol

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * A message sent from a client (an application being inspected) to the TimeMachine server.
 */
@Serializable
public sealed interface TimeMachineClientMessage {

    /** First frame of a connection: identifies the session. */
    @Serializable
    @SerialName("hello")
    public data class Hello(
        val session: TimeMachineSession,
    ) : TimeMachineClientMessage

    /** An VIE ViewModel was started on the client. */
    @Serializable
    @SerialName("vm-started")
    public data class ViewModelStarted(
        val info: VIEViewModelInfo,
        val at: Instant,
    ) : TimeMachineClientMessage

    /**
     * Answer to [TimeMachineServerMessage.RequestViewModelInfo].
     *
     * [info] is `null` when the client no longer knows a ViewModel with that identifier.
     */
    @Serializable
    @SerialName("vm-info")
    public data class ViewModelInfo(
        val viewModelId: Uuid,
        val info: VIEViewModelInfo?,
    ) : TimeMachineClientMessage

    /** An VIE ViewModel was closed on the client. */
    @Serializable
    @SerialName("vm-closed")
    public data class ViewModelClosed(
        val viewModelId: Uuid,
        val at: Instant,
    ) : TimeMachineClientMessage

    /** An VIE object (`Model`, `Intent` or `Signal`) was observed on the client. */
    @Serializable
    @SerialName("observed")
    public data class Observed(
        val observation: VIEObservation,
    ) : TimeMachineClientMessage

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
}
