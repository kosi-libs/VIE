package org.kodein.arch.vie.timemachine.server.fixtures

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent


/**
 * The data of one design scenario: the events its store goes through, which are the sample world's,
 * and how the fake probe answers what the developer sends. It is the fixture set of the ViewModel
 * tests.
 *
 * @property id The design's own scenario id.
 * @property events Applied to the store in order. Times are relative to [Demo.now], so each
 *   scenario's sparklines are the same every time.
 * @property probe How the probe answers an intervention, and a request for a ViewModel's description.
 * @property maxRows The store's bound on each ViewModel's rows: low, so the base data evicts some.
 * @property interventionTimeout How long an intervention waits before its deadline.
 */
internal class ScenarioData(
    val id: String,
    val events: List<TimeMachineEvent>,
    val probe: ProbeScript = ProbeScript(),
    val maxRows: Int = BaseMaxRows,
    val interventionTimeout: Duration = 5.seconds,
    val now: Instant = Demo.now,
) {
    companion object {

        const val BaseMaxRows: Int = SampleWorld.BaseMaxRows

        /** The design's scenario ids, in the order of its `scenario` enum. */
        val ids: List<String> = listOf(
            "empty", "cannotListen", "main", "detailIntent", "compModel", "compIntent", "compSignal", "compDisabled",
            "compJsonError", "compPending", "compSuccess", "compApplied", "compFailure", "compFailType", "compTimeout",
            "compNotSent", "disconnected", "closedVM", "unknownVM", "unknownClosed", "filtered", "rejected", "compact",
            "noVM", "noEntries", "noMatch", "session",
        )

        fun of(id: String): ScenarioData = when (id) {
            "empty" -> ScenarioData(id, listening())
            "cannotListen" -> ScenarioData(id, listOf(TimeMachineEvent.ListenerChanged(ListenerState.Failed("Address already in use"))))
            "compPending" -> ScenarioData(id, base(), ProbeScript(intervene = { ProbeAnswer.Silent }), interventionTimeout = 1.hours)
            "compTimeout" -> ScenarioData(id, base(), ProbeScript(intervene = { ProbeAnswer.Silent }))
            "compSuccess" -> ScenarioData(id, base(), ProbeScript(intervene = { ProbeAnswer.Accepted }))
            "compApplied" -> ScenarioData(id, base(), ProbeScript(intervene = { ProbeAnswer.AcceptedNotObserved }))
            "compFailure" -> ScenarioData(
                id, base(),
                ProbeScript(intervene = { ProbeAnswer.refused(VIEInterventionResult.Failure(VIEInterventionResult.Reason.BadJson, "Unexpected JSON token at offset 61: Expected string literal for 'taskId'")) }),
            )
            "compFailType" -> ScenarioData(
                id, base(),
                ProbeScript(intervene = { ProbeAnswer.refused(VIEInterventionResult.Failure(VIEInterventionResult.Reason.UnknownType, "${it.typeName} is not a subtype of the Intent")) }),
            )
            "disconnected" -> ScenarioData(id, base(disconnected = true))
            "compNotSent" -> ScenarioData(id, base(disconnected = true, notSent = true))
            "rejected" -> ScenarioData(id, base(rejected = true))
            "unknownVM" -> ScenarioData(id, base(unknown = true))
            "unknownClosed" -> ScenarioData(id, base(unknown = true), ProbeScript(info = { InfoAnswer.Gone }))
            "noEntries" -> ScenarioData(id, base(silent = true))
            "main", "detailIntent", "compModel", "compIntent", "compSignal", "compDisabled", "compJsonError", "filtered", "closedVM",
            "compact", "noVM", "noMatch", "session" -> ScenarioData(id, base())
            else -> error("Unknown scenario $id")
        }

        private fun listening() = SampleWorld.listening()

        private fun base(
            disconnected: Boolean = false,
            notSent: Boolean = false,
            rejected: Boolean = false,
            unknown: Boolean = false,
            silent: Boolean = false,
        ): List<TimeMachineEvent> = SampleWorld.events(
            WorldOptions(
                disconnected = disconnected, notSentIntervention = notSent, rejected = rejected,
                unknownViewModel = unknown, silentViewModel = silent,
            ),
        )
    }
}
