package org.kodein.arch.vie.timemachine.server.ui.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.server.store.Row
import org.kodein.arch.vie.timemachine.server.store.TimelineEntry
import org.kodein.arch.vie.timemachine.server.store.dropped
import org.kodein.arch.vie.timemachine.server.store.seq
import org.kodein.arch.vie.timemachine.server.store.started
import org.kodein.arch.vie.timemachine.server.store.t0


class TimelineFilterTest {

    private val vm = Uuid.random()

    private fun observation(
        sequence: Long,
        kind: VIEObjectKind = VIEObjectKind.Model,
        typeName: String = "app.Type",
        asString: String = "text",
        intervened: Boolean = false,
    ) = TimelineEntry.Message(
        Row(
            TimeMachineClientMessage.Observed(
                VIEObservation(vm, t0, kind, typeName, asString, interventionId = if (intervened) Uuid.random() else null),
                seq(sequence),
            ),
            length = 10,
        )
    )

    private fun marker() = TimelineEntry.Evicted(3)

    private fun TimelineFilter.run(entries: List<TimelineEntry>, json: (Row) -> String? = { null }) = apply(entries, { it }, json)

    @Test
    fun anUntouchedFilterIsNotActive() {
        val entries = listOf(observation(1), observation(2, VIEObjectKind.Intent))
        val result = TimelineFilter().run(entries)

        assertEquals(entries, result.items)
        assertEquals(0, result.hidden)
        assertFalse(result.active)
    }

    @Test
    fun aKindThatIsOffHidesItsObservationsAndMakesTheFilterActive() {
        val result = TimelineFilter(kinds = setOf(VIEObjectKind.Model)).run(listOf(observation(1), observation(2, VIEObjectKind.Intent), observation(3, VIEObjectKind.Signal)))

        assertEquals(1, result.items.size)
        assertEquals(2, result.hidden)
        assertTrue(result.active)
    }

    @Test
    fun aKindOffIsActiveEvenWhenItHidesNothing() {
        val result = TimelineFilter(kinds = setOf(VIEObjectKind.Model)).run(listOf(observation(1)))

        assertEquals(0, result.hidden)
        assertTrue(result.active)
    }

    @Test
    fun interventionsOnlyKeepsTheIntervenedObservations() {
        val intervened = observation(2, intervened = true)
        val result = TimelineFilter(intervenedOnly = true).run(listOf(observation(1), intervened))

        assertEquals(listOf(intervened), result.items)
        assertEquals(1, result.hidden)
    }

    @Test
    fun theQueryMatchesTheTypeNameTheStringAndTheJsonIgnoringCase() {
        val byType = observation(1, typeName = "app.DeleteTask")
        val byString = observation(2, asString = "Task(title=Buy MILK)")
        val byJson = observation(3)
        val none = observation(4)
        val json = { row: Row -> if (row.sequence == seq(3)) """{"title":"Buy milk"}""" else """{"x":1}""" }

        assertEquals(listOf(byType), TimelineFilter(query = "deleteTASK").run(listOf(byType, byString, byJson, none), json).items)
        assertEquals(listOf(byString, byJson), TimelineFilter(query = "buy milk").run(listOf(byType, byString, byJson, none), json).items)
    }

    @Test
    fun theJsonIsOnlyAskedForWhenTheQueryHasToLookInIt() {
        var asked = 0
        val json = { _: Row -> asked++; "{}" }

        TimelineFilter(query = "text").run(listOf(observation(1)), json)
        assertEquals(0, asked)
        TimelineFilter(query = "nope").run(listOf(observation(1)), json)
        assertEquals(1, asked)
    }

    @Test
    fun filtersCombine() {
        val entries = listOf(
            observation(1, VIEObjectKind.Intent, intervened = true),
            observation(2, VIEObjectKind.Model, intervened = true),
            observation(3, VIEObjectKind.Intent),
        )
        val result = TimelineFilter(kinds = setOf(VIEObjectKind.Intent), intervenedOnly = true, query = "text").run(entries)

        assertEquals(listOf(entries[0]), result.items)
        assertEquals(2, result.hidden)
    }

    @Test
    fun markersAndBookendsAlwaysPassAndAreNotCounted() {
        val others = listOf(
            marker(),
            TimelineEntry.HistoryUnavailable,
            TimelineEntry.Message(Row(started(vm, 0), 10)),
            TimelineEntry.Message(Row(dropped(vm, 5), 10)),
        )
        val result = TimelineFilter(kinds = emptySet(), query = "zzz").run(others + observation(9))

        assertEquals(others, result.items)
        assertEquals(1, result.hidden)
    }

    @Test
    fun anItemWithNoEntryPasses() {
        val result = TimelineFilter(kinds = emptySet()).apply(listOf("divider"), { null }, { null })

        assertEquals(listOf("divider"), result.items)
        assertEquals(0, result.hidden)
    }

    @Test
    fun aHitPastIndex40IsBroughtIntoViewAfterAnEllipsis() {
        val text = "a".repeat(50) + "needle and the rest"

        assertEquals("…" + "a".repeat(28) + "needle and the rest", searchSnippet(text, "NEEDLE"))
    }

    @Test
    fun aHitUpToIndex40LeavesTheTextAlone() {
        val text = "a".repeat(40) + "needle"

        assertEquals(text, searchSnippet(text, "needle"))
        assertEquals(text, searchSnippet(text, ""))
        assertEquals(text, searchSnippet(text, "absent"))
    }
}
