package org.kodein.arch.vie.timemachine.server.ui.sessions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.SampleWorld
import org.kodein.arch.vie.timemachine.server.fixtures.WorldOptions
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope


/** What the compact layout's rail lists, and how each tile reads. */
class RailTest {

    private fun rail(selected: InspectorScope?, options: WorldOptions = WorldOptions()): List<RailItem> =
        railItems(sessionsModel(SampleWorld.state(options), selected, Demo.now))

    private fun List<RailItem>.of(scope: InspectorScope) = single { it.scope == scope }

    @Test
    fun `every session is a tile, and the selected scope's session lists its ViewModels`() {
        val items = rail(SampleWorld.taskListScope)

        // The earlier session first, bare; then Demo, with TaskListVM, SettingsVM and the two EditTaskVMs.
        assertEquals(listOf("De", "De", "TL", "S", "ET", "ET"), items.map { it.label })
        assertEquals(
            listOf(SampleWorld.earlierScope, SampleWorld.demoScope, SampleWorld.taskListScope, SampleWorld.settingsScope, SampleWorld.editTaskScope, SampleWorld.editTaskClosedScope),
            items.map { it.scope },
        )
    }

    @Test
    fun `selecting a ViewModel selects its tile only`() {
        val items = rail(SampleWorld.taskListScope)
        assertEquals(listOf(SampleWorld.taskListScope), items.filter { it.selected }.map { it.scope })
    }

    @Test
    fun `selecting a session selects its tile and lists its ViewModels`() {
        val items = rail(SampleWorld.demoScope)
        assertEquals(listOf(SampleWorld.demoScope), items.filter { it.selected }.map { it.scope })
        assertEquals(6, items.size)
    }

    @Test
    fun `selecting the earlier session lists its ViewModels instead`() {
        val items = rail(SampleWorld.earlierTaskListScope)
        assertEquals(listOf("De", "TL", "De"), items.map { it.label })
        assertEquals(listOf(SampleWorld.earlierTaskListScope), items.filter { it.selected }.map { it.scope })
    }

    @Test
    fun `with nothing selected the first connected session lists its ViewModels, as the empty centre names it`() {
        val items = rail(null)
        assertEquals(listOf("De", "De", "TL", "S", "ET", "ET"), items.map { it.label })
        assertTrue(items.none { it.selected })
    }

    @Test
    fun `with no session connected the first lists its ViewModels`() {
        val items = rail(null, WorldOptions(demoSession = false))
        assertEquals(listOf("De", "TL"), items.map { it.label })
    }

    @Test
    fun `a connected session has a filled dot, a disconnected one a ring, and both of the latter dim`() {
        val items = rail(SampleWorld.taskListScope)
        val earlier = items.of(SampleWorld.earlierScope)
        val demo = items.of(SampleWorld.demoScope)
        assertEquals(RailDot.Filled, demo.dot)
        assertEquals(false, demo.dimmed)
        assertEquals(RailDot.Ring, earlier.dot)
        assertEquals(true, earlier.dimmed)
    }

    @Test
    fun `a ViewModel has no dot, and a closed one dims`() {
        val items = rail(SampleWorld.taskListScope)
        assertEquals(RailDot.None, items.of(SampleWorld.taskListScope).dot)
        assertEquals(false, items.of(SampleWorld.editTaskScope).dimmed)
        assertEquals(true, items.of(SampleWorld.editTaskClosedScope).dimmed)
    }

    @Test
    fun `the ViewModels of a disconnected session dim with it`() {
        val items = rail(null, WorldOptions(disconnected = true))
        assertEquals(RailDot.Ring, items.of(SampleWorld.demoScope).dot)
        assertTrue(items.filter { it.scope is InspectorScope.ViewModel }.all { it.dimmed })
    }

    @Test
    fun `a ViewModel nobody described is a question mark`() {
        val items = rail(SampleWorld.taskListScope, WorldOptions(unknownViewModel = true))
        assertEquals("?", items.of(SampleWorld.unknownScope).label)
        assertEquals("Unknown ViewModel d4c8", items.of(SampleWorld.unknownScope).description)
    }

    @Test
    fun `a tile says what it is in words`() {
        val items = rail(SampleWorld.taskListScope)
        assertEquals("Demo 3f2a…c91e, connected", items.of(SampleWorld.demoScope).description)
        assertEquals("Demo 7b10…51a7, disconnected", items.of(SampleWorld.earlierScope).description)
        assertEquals("TaskListVM a1f4", items.of(SampleWorld.taskListScope).description)
        assertEquals("EditTaskVM b2e6, closed", items.of(SampleWorld.editTaskClosedScope).description)
    }

    @Test
    fun `a ViewModel's initials are what is not a lowercase letter, without its VM`() {
        assertEquals("TL", viewModelMonogram("org.app.vm.TaskListVM"))
        assertEquals("ET", viewModelMonogram("EditTaskVM"))
        assertEquals("S", viewModelMonogram("org.app.SettingsVM"))
        assertEquals("HS", viewModelMonogram("HomeScreenViewModel"), "at most two")
        assertEquals("A", viewModelMonogram("app.A"))
        assertEquals("VM", viewModelMonogram("home"), "no capital: the design's fallback")
        assertEquals("VM", viewModelMonogram("org.app.VM"))
        assertEquals("?", viewModelMonogram(null))
    }

    @Test
    fun `a session's monogram is its first two characters`() {
        assertEquals("De", sessionMonogram("Demo"))
        assertEquals("My", sessionMonogram("My App"))
        assertEquals("x", sessionMonogram("  x"))
        assertEquals("?", sessionMonogram(""))
        assertEquals("?", sessionMonogram("   "))
        assertEquals("🎯D", sessionMonogram("🎯Demo"), "a character outside the basic plane is one")
    }
}
