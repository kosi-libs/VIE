package org.kodein.arch.vie.timemachine.server.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.store.ConnectionId
import org.kodein.arch.vie.timemachine.server.store.Limits
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.store.after
import org.kodein.arch.vie.timemachine.server.store.hello
import org.kodein.arch.vie.timemachine.server.store.observed
import org.kodein.arch.vie.timemachine.server.store.receive
import org.kodein.arch.vie.timemachine.server.store.seq
import org.kodein.arch.vie.timemachine.server.store.t0
import org.kodein.arch.vie.timemachine.server.store.vm


class ModelDiffsTest {

    private val c1 = ConnectionId(1)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()

    private fun json(n: Int) = JsonObject(mapOf("n" to JsonPrimitive(n)))

    /** A Model whose one field `n` is [n], observed at [sequence]. */
    private fun model(sequence: Long, n: Int, length: Int = 10) =
        receive(c1, observed(vm, sequence, VIEObjectKind.Model, json = json(n)), length = length)

    private fun connected(limits: Limits = Limits()) = TimeMachineState(limits = limits).after(hello(c1, session)).state

    private fun TimeMachineState.record() = vm(session.id, vm)

    /** The diff of a Model whose `n` went [from] → [to], against the one at [previous]. */
    private fun changed(previous: Long, from: Int, to: Int) =
        ModelDiff(seq(previous), listOf(DiffOp(DiffKind.Changed, listOf("n"), JsonPrimitive(from), JsonPrimitive(to))))

    @Test
    fun `a Model's diff is against the Model observed before it, and there is none before the first`() {
        val state = connected().after(model(0, 0), receive(c1, observed(vm, 1, VIEObjectKind.Intent)), model(2, 2)).state
        assertEquals(changed(0, 0, 2), modelDiff(state.record(), seq(2)))
        assertNull(modelDiff(state.record(), seq(0)))
    }

    @Test
    fun `the pinned Model's diff is the current Model's, even when its row was never placed for being too heavy`() {
        val state = connected(Limits(maxChars = 50)).after(model(0, 0), model(1, 1, length = 100)).state
        assertNull(state.record().row(seq(1)))
        assertEquals(changed(0, 0, 1), modelDiff(state.record(), seq(1)))
    }

    @Test
    fun `the panes share a diff, computed once while its rows hold`() {
        val diffs = ModelDiffs()
        val state = connected().after(model(0, 0), model(1, 1)).state
        val first = diffs.of(state.record(), seq(1))
        assertEquals(changed(0, 0, 1), first)
        assertSame(first, diffs.of(state.record(), seq(1)))
        assertSame(first, diffs.of(state.after(model(2, 2)).state.record(), seq(1)), "a later row changes nothing to it")
    }

    @Test
    fun `a Model placed late between a row and its previous Model replaces the diff`() {
        val diffs = ModelDiffs()
        val before = connected().after(model(0, 0), model(4, 4)).state
        assertEquals(changed(0, 0, 4), diffs.of(before.record(), seq(4)))

        val after = before.after(model(2, 2)).state
        assertEquals(changed(2, 2, 4), diffs.of(after.record(), seq(4)))
    }

    @Test
    fun `a row whose previous Model was evicted has no diff any more`() {
        val diffs = ModelDiffs()
        val full = connected(Limits(maxRows = 2)).after(model(0, 0), model(1, 1)).state
        assertEquals(changed(0, 0, 1), diffs.of(full.record(), seq(1)))

        val evicted = full.after(model(2, 2)).state
        assertNull(diffs.of(evicted.record(), seq(1)))
        assertEquals(changed(1, 1, 2), diffs.of(evicted.record(), seq(2)))
    }

    @Test
    fun `retain drops the diffs of the rows evicted, then of the ViewModels the store dropped`() {
        val diffs = ModelDiffs()
        val full = connected(Limits(maxRows = 3)).after(model(0, 0), model(1, 1), model(2, 2)).state
        diffs.of(full.record(), seq(1))
        diffs.of(full.record(), seq(2))
        assertEquals(setOf(seq(1), seq(2)), diffs.cachedRows(vm))

        val evicted = full.after(model(3, 3), model(4, 4)).state // retains 2, 3 and 4
        diffs.retain(evicted)
        assertEquals(setOf(seq(2)), diffs.cachedRows(vm))

        diffs.retain(evicted.after(TimeMachineEvent.Disconnected(c1, t0), TimeMachineEvent.ClearSession(session.id)).state)
        assertEquals(emptySet(), diffs.cachedRows(vm))
    }
}
