package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind


@OptIn(ExperimentalCoroutinesApi::class)
class InterventionDeadlineTest {

    private val c1 = ConnectionId(1)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()

    @Test
    fun theStoreRunsTheDeadlineAndHandsTheTransportOnlyItsCommands() = runTest {
        val executed = mutableListOf<TransportCommand>()
        val store = TimeMachineStore(backgroundScope, interventionTimeout = 5.seconds) { executed += it }
        store.post(hello(c1, session))
        store.post(receive(c1, started(vm, 0)))
        runCurrent()

        val request = store.intervene(session.id, vm, VIEObjectKind.Intent, "I", JsonPrimitive(1))
        runCurrent()
        val status = { store.state.value.vm(session.id, vm).interventions.getValue(request).status }
        assertEquals(InterventionStatus.Pending, status())
        assertEquals(TimeMachineServerMessage.Intervene(request, vm, VIEObjectKind.Intent, "I", JsonPrimitive(1)), (executed.last() as Command.Send).message)

        advanceTimeBy(5.seconds - 1.milliseconds)
        runCurrent()
        assertEquals(InterventionStatus.Pending, status())

        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(InterventionStatus.TimedOut, status())
    }
}
