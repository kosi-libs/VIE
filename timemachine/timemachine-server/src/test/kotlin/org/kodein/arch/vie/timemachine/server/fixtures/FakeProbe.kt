package org.kodein.arch.vie.timemachine.server.fixtures

import kotlin.time.Duration
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo
import org.kodein.arch.vie.timemachine.protocol.TimeMachineTimelineMessage
import org.kodein.arch.vie.timemachine.server.store.Command
import org.kodein.arch.vie.timemachine.server.store.ConnectionId
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.store.TimeMachineStore
import org.kodein.arch.vie.timemachine.server.store.TransportCommand


/** What a probe is asked for by an intervention. */
internal data class InterventionRequest(
    val requestId: Uuid,
    val viewModelId: Uuid,
    val kind: VIEObjectKind,
    val typeName: String,
    val asJson: JsonElement,
)

/**
 * How the probe answers an intervention: [result] after [resultAfter] (`null`: never), then, when
 * [rowAfter] is not `null`, the observation tagged with the request. The two arrive in either order
 * for a real probe, so a late [rowAfter] or [resultAfter] shows the transitions of the card.
 */
internal class ProbeAnswer(
    val result: VIEInterventionResult?,
    val resultAfter: Duration = Duration.ZERO,
    val rowAfter: Duration? = null,
) {
    companion object {
        /** Success, and the object enters the ViewModel. */
        val Accepted = ProbeAnswer(VIEInterventionResult.Success, rowAfter = Duration.ZERO)

        /** Success, but no row ever follows: the ViewModel closed first, or the Model was equal. */
        val AcceptedNotObserved = ProbeAnswer(VIEInterventionResult.Success)

        /** Not a word: the deadline settles it. */
        val Silent = ProbeAnswer(null)

        fun refused(failure: VIEInterventionResult.Failure, after: Duration = Duration.ZERO) = ProbeAnswer(failure, after)
    }
}

/** What the probe says when asked to describe a ViewModel. */
internal sealed interface InfoAnswer {
    data class Known(val info: VIEViewModelInfo) : InfoAnswer

    /** It no longer knows it: it closed. */
    data object Gone : InfoAnswer

    /** No answer: the ViewModel stays identifying. */
    data object Silent : InfoAnswer
}

internal class ProbeScript(
    val intervene: (InterventionRequest) -> ProbeAnswer = { ProbeAnswer.Accepted },
    val info: (Uuid) -> InfoAnswer = { InfoAnswer.Silent },
)

/**
 * The scripted probe behind a fixture store: the store's `execute`, answering as
 * [ScenarioData.probe] says. Its own rows go on from the highest sequence the scenario's events
 * carry, stamped with the scenario's `now`, so every run is the same.
 *
 * It answers only what the developer sends: the interventions the data already holds are not asked
 * again. Delayed answers wait on [scope], so virtual time drives them in a test.
 */
internal class FakeProbe(private val data: ScenarioData, private val scope: CoroutineScope) {

    private lateinit var store: TimeMachineStore

    private var nextSequence: Long = data.events
        .mapNotNull { ((it as? TimeMachineEvent.MessageReceived)?.message as? TimeMachineTimelineMessage)?.sequence?.value }
        .maxOrNull()?.plus(1) ?: 0

    private val past: Set<Uuid> = data.events.filterIsInstance<TimeMachineEvent.Intervene>().mapTo(HashSet()) { it.requestId }

    /** Wires the probe to the store it was passed to as `execute`. */
    fun attach(store: TimeMachineStore) {
        this.store = store
    }

    fun execute(command: TransportCommand) {
        val send = command as? Command.Send ?: return
        when (val message = send.message) {
            is TimeMachineServerMessage.Intervene -> if (message.requestId !in past) intervene(send.connection, message)
            is TimeMachineServerMessage.RequestViewModelInfo -> describe(send.connection, message.viewModelId)
            else -> Unit
        }
    }

    private fun intervene(connection: ConnectionId, message: TimeMachineServerMessage.Intervene) {
        val answer = data.probe.intervene(InterventionRequest(message.requestId, message.viewModelId, message.kind, message.typeName, message.asJson))
        answer.result?.let { result ->
            later(answer.resultAfter, received(connection, TimeMachineClientMessage.InterventionResult(message.requestId, result), length = 80))
        }
        answer.rowAfter?.let { after ->
            val sequence = TimeMachineSequence(nextSequence++)
            val text = message.asJson.toString()
            val observation = VIEObservation(
                viewModelId = message.viewModelId,
                at = data.now,
                kind = message.kind,
                typeName = message.typeName,
                asString = message.typeName.substringAfterLast('.') + text,
                asJson = message.asJson,
                interventionId = message.requestId,
                delivery = if (message.kind == VIEObjectKind.Signal) VIESignalDelivery.Event else null,
            )
            later(after, received(connection, TimeMachineClientMessage.Observed(observation, sequence), length = text.length + 150))
        }
    }

    private fun describe(connection: ConnectionId, viewModelId: Uuid) {
        val info = when (val answer = data.probe.info(viewModelId)) {
            is InfoAnswer.Known -> answer.info
            InfoAnswer.Gone -> null
            InfoAnswer.Silent -> return
        }
        later(Duration.ZERO, received(connection, TimeMachineClientMessage.ViewModelInfo(viewModelId, info), length = 200))
    }

    private fun received(connection: ConnectionId, message: TimeMachineClientMessage, length: Int) =
        TimeMachineEvent.MessageReceived(connection, message, length, data.now)

    private fun later(after: Duration, event: TimeMachineEvent) {
        if (after <= Duration.ZERO) store.post(event) else scope.launch {
            delay(after)
            store.post(event)
        }
    }
}
