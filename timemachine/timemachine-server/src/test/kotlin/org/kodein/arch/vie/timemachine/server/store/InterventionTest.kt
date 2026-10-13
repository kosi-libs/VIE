package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind


class InterventionTest {

    private val c1 = ConnectionId(1)
    private val c2 = ConnectionId(2)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()
    private val request = Uuid.random()
    private val json = JsonPrimitive(100)
    private val refused = VIEInterventionResult.Failure(VIEInterventionResult.Reason.BadJson, "nope")

    private val connected = TimeMachineState().after(hello(c1, session), receive(c1, started(vm, 0))).state

    private fun intervene(sessionId: Uuid = session.id, viewModelId: Uuid = vm) =
        TimeMachineEvent.Intervene(sessionId, viewModelId, VIEObjectKind.Model, "M", json, request, at(1))

    private fun result(result: VIEInterventionResult, connection: ConnectionId = c1) =
        receive(connection, TimeMachineClientMessage.InterventionResult(request, result))

    private fun tagged(sequence: Long, requestId: Uuid = request, connection: ConnectionId = c1) =
        receive(connection, observed(vm, sequence).let { it.copy(observation = it.observation.copy(interventionId = requestId)) })

    private val deadline = TimeMachineEvent.DeadlinePassed(request, at(6))

    private fun TimeMachineState.status() = vm(session.id, vm).interventions.getValue(request).status

    // Sending

    @Test
    fun aConnectedSessionIsSentTheRequestAndItsDeadlineStarts() {
        val (state, commands) = connected.on(intervene())

        assertEquals(
            listOf(
                Command.Send(c1, TimeMachineServerMessage.Intervene(request, vm, VIEObjectKind.Model, "M", json)),
                Command.StartDeadline(request),
            ),
            commands,
        )
        assertEquals(InterventionStatus.Pending, state.status())
        assertEquals(at(1), state.vm(session.id, vm).interventions.getValue(request).requestedAt)
    }

    @Test
    fun aDisconnectedSessionIsSentNothingAndTheInterventionIsNotSent() {
        val (state, commands) = connected.after(TimeMachineEvent.Disconnected(c1, t0), intervene())

        assertEquals(emptyList(), commands)
        assertEquals(InterventionStatus.NotSent, state.status())
    }

    @Test
    fun aSessionOrViewModelNoLongerHeldMakesNothingHappen() {
        assertEquals(Transition(connected), connected.on(intervene(sessionId = Uuid.random())))
        assertEquals(Transition(connected), connected.on(intervene(viewModelId = Uuid.random())))
    }

    @Test
    fun aClosedViewModelIsStillSentTheRequest() {
        val closedVm = connected.after(receive(c1, closed(vm, 1))).state
        val (_, commands) = closedVm.on(intervene())

        assertTrue(commands.any { it is Command.Send && it.message is TimeMachineServerMessage.Intervene })
    }

    // Status

    @Test
    fun aTaggedRowIsASuccessWhicheverOrderTheResultArrivesIn() {
        val sent = connected.on(intervene()).state

        assertEquals(InterventionStatus.Succeeded(seq(1)), sent.after(result(VIEInterventionResult.Success), tagged(1)).state.status())
        assertEquals(InterventionStatus.Succeeded(seq(1)), sent.after(tagged(1), result(VIEInterventionResult.Success)).state.status())
    }

    @Test
    fun aTaggedRowOutranksAFailureAndTheDeadline() {
        val sent = connected.on(intervene()).state

        assertEquals(InterventionStatus.Succeeded(seq(1)), sent.after(result(refused), tagged(1)).state.status())
        assertEquals(InterventionStatus.Succeeded(seq(1)), sent.after(deadline, tagged(1)).state.status())
        assertEquals(InterventionStatus.Succeeded(seq(1)), sent.after(tagged(1), deadline).state.status())
    }

    @Test
    fun aFailureIsFailedBeforeOrAfterTheDeadline() {
        val sent = connected.on(intervene()).state

        assertEquals(InterventionStatus.Failed(refused), sent.after(result(refused)).state.status())
        assertEquals(InterventionStatus.Failed(refused), sent.after(deadline, result(refused)).state.status())
    }

    @Test
    fun aSuccessIsPendingUntilTheDeadlineThenAcceptedNotObserved() {
        val accepted = connected.after(intervene(), result(VIEInterventionResult.Success)).state

        assertEquals(InterventionStatus.Pending, accepted.status())
        assertEquals(InterventionStatus.AcceptedNotObserved, accepted.after(deadline).state.status())
    }

    @Test
    fun nothingBeforeTheDeadlineTimesOutAndALateSuccessIsAcceptedNotObserved() {
        val timedOut = connected.after(intervene(), deadline).state

        assertEquals(InterventionStatus.TimedOut, timedOut.status())
        assertEquals(InterventionStatus.AcceptedNotObserved, timedOut.after(result(VIEInterventionResult.Success)).state.status())
    }

    @Test
    fun onlyTheFirstResultCounts() {
        val (state) = connected.after(intervene(), result(VIEInterventionResult.Success), result(refused), deadline)

        assertEquals(InterventionStatus.AcceptedNotObserved, state.status())
    }

    @Test
    fun aResultLostWithItsConnectionWhileItsRowArrivesOnTheNextIsASuccess() {
        val (state) = connected.after(
            intervene(),
            TimeMachineEvent.Disconnected(c1, t0),
            hello(c2, session),
            tagged(1, connection = c2),
            deadline,
        )

        assertEquals(InterventionStatus.Succeeded(seq(1)), state.status())
    }

    // Storage

    @Test
    fun aTaggedRowCountedAsEvictedStillLinksAndTheLinkSaysSo() {
        val (state) = TimeMachineState(limits = Limits(maxRows = 2)).after(
            hello(c1, session),
            receive(c1, started(vm, 0)),
            intervene(),
            receive(c1, observed(vm, 2)),
            receive(c1, observed(vm, 3)),
            tagged(1), // late, older than the full timeline's oldest
        )

        assertEquals(InterventionStatus.Succeeded(seq(1)), state.status())
        assertFalse(state.vm(session.id, vm).holds(seq(1)))
        assertTrue(state.vm(session.id, vm).holds(seq(3)))
    }

    @Test
    fun anObservationTaggedWithAnUnknownRequestIsPlacedWithNoLink() {
        val other = Uuid.random()
        val (state) = connected.after(intervene(), tagged(1, requestId = other))

        assertEquals(other, (state.vm(session.id, vm).rows.last().message as TimeMachineClientMessage.Observed).observation.interventionId)
        assertEquals(InterventionStatus.Pending, state.status())
    }

    @Test
    fun aResultOrDeadlineForAnUnknownRequestChangesNothing() {
        val (state) = connected.after(intervene())
        val unknown = Uuid.random()

        assertEquals(state, state.after(receive(c1, TimeMachineClientMessage.InterventionResult(unknown, refused))).state)
        assertEquals(state, state.after(TimeMachineEvent.DeadlinePassed(unknown, at(9))).state)
    }

    @Test
    fun clearingTheViewModelDropsItsIndexEntry() {
        val (state) = connected.after(intervene(), receive(c1, closed(vm, 1)), TimeMachineEvent.ClearSession(session.id))

        val record = state.session(session.id)
        assertEquals(emptyMap(), record.interventionIndex)
        assertNull(record.viewModels[vm])
        assertEquals(state, state.after(result(refused), deadline).state)
    }
}
