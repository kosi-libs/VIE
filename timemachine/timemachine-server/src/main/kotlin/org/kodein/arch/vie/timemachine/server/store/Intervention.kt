package org.kodein.arch.vie.timemachine.server.store

import kotlinx.serialization.json.JsonElement
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import kotlin.time.Instant
import kotlin.uuid.Uuid


/**
 * An intervention the inspector asked for, and the facts recorded about it since.
 *
 * The probe answers with its result right away and never again, while the observation it tags goes
 * through the probe's queue: they arrive in either order, and either can arrive without the other.
 * So [status] is a function of the facts, not of the order they arrived in.
 *
 * @property sent Whether the [TimeMachineServerMessage.Intervene] was sent: not when the session was
 *   disconnected.
 * @property result The probe's first answer.
 * @property taggedSequence The sequence of the observation tagged with [requestId], whether it was
 *   placed or counted as evicted.
 * @property deadlinePassedAt When its deadline passed.
 */
data class Intervention(
    val requestId: Uuid,
    val kind: VIEObjectKind,
    val typeName: String,
    val asJson: JsonElement,
    val requestedAt: Instant,
    val sent: Boolean,
    val result: VIEInterventionResult? = null,
    val taggedSequence: TimeMachineSequence? = null,
    val deadlinePassedAt: Instant? = null,
) {

    /** What the facts say, a tagged row outranking everything once the request was sent. */
    val status: InterventionStatus
        get() = when {
            !sent -> InterventionStatus.NotSent
            taggedSequence != null -> InterventionStatus.Succeeded(taggedSequence)
            result is VIEInterventionResult.Failure -> InterventionStatus.Failed(result)
            deadlinePassedAt == null -> InterventionStatus.Pending
            result is VIEInterventionResult.Success -> InterventionStatus.AcceptedNotObserved
            else -> InterventionStatus.TimedOut
        }
}

/** Where an [Intervention] stands. Eviction never changes it. */
sealed interface InterventionStatus {

    /** The session was disconnected: nothing was sent. */
    data object NotSent : InterventionStatus

    /**
     * The object entered the ViewModel, as the row at [sequence] shows. That row may have been
     * evicted since: see [ViewModelRecord.holds].
     */
    data class Succeeded(val sequence: TimeMachineSequence) : InterventionStatus

    /** The probe refused it. */
    data class Failed(val failure: VIEInterventionResult.Failure) : InterventionStatus

    /** Neither a tagged row nor a refusal yet, and the deadline has not passed. */
    data object Pending : InterventionStatus

    /**
     * The probe accepted it, but no tagged row arrived before the deadline: the ViewModel may have
     * been closed first, or an injected model equal to the current one, or conflated away, is
     * applied but never observed.
     */
    data object AcceptedNotObserved : InterventionStatus

    /** Neither a result nor a tagged row before the deadline. */
    data object TimedOut : InterventionStatus
}
