@file:OptIn(ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.fixtures.state
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import org.kodein.arch.vie.timemachine.server.ui.core.Selection
import org.kodein.arch.vie.timemachine.server.ui.common.DiffKind


class TimelinePaneTest {

    private fun seq(n: Long) = TimeMachineSequence(n)

    /** A Timeline of TaskListVM over the base data, shown. */
    private fun timeline(
        scenario: String = "main",
        scope: InspectorScope = taskListScope,
        block: suspend TestScope.(TimelineVM, Inspector) -> Unit,
    ): TestResult {
        lateinit var inspector: Inspector
        return runVMTest(
            newVM = {
                inspector = inspector(scenario)
                inspector.visibility.show(TimelineVM.key(scope))
                TimelineVM(inspector, scope).also { inspector.selection.selectScope(scope) }
            },
            block = { vm, _ -> runCurrent(); block(vm, inspector) },
        )
    }

    private val TimelineVM.projection get() = modelFlow.value.projection

    private val TimelineVM.controls get() = modelFlow.value.controls

    @Test
    fun `it shows its ViewModel's header and entries`() = timeline { vm, _ ->
        val header = assertIs<TimelineHeader.ViewModel>(vm.projection.header)
        assertEquals(Demo.taskList, header.id)
        assertEquals(false, header.startSeen)
        assertTrue(vm.projection.items.any { it.sequence == seq(208) })
        // 208 added an entry to work: the Δ line.
        val diff = vm.projection.items.first { it.sequence == seq(208) }.diff
        assertEquals(seq(206), diff?.previous)
        assertEquals(listOf(DiffKind.Added), diff?.ops?.map { it.kind })
    }

    @Test
    fun `it counts the observations the filters hide, and opens Edit only on a live ViewModel`() = timeline("filtered") { vm, _ ->
        assertEquals(7, vm.projection.kindCounts[VIEObjectKind.Model])
        assertEquals(2, vm.projection.intervenedCount)
        assertTrue(vm.projection.items.first { it.observation != null }.canIntervene)
    }

    @Test
    fun `a session scope names each row's ViewModel, and a closed one cannot be edited`() = runVMTest(
        newVM = {
            val inspector = inspector("main")
            val scope = InspectorScope.Session(Demo.session.id)
            inspector.visibility.show(TimelineVM.key(scope))
            TimelineVM(inspector, scope)
        },
        block = { vm, _ ->
            runCurrent()
            val items = vm.modelFlow.value.projection.items.filter { it.observation != null }
            assertTrue(items.all { it.source != null })
            assertTrue(items.any { !it.canIntervene })
        },
    )

    @Test
    fun `a session scope merges its ViewModels' entries and names its session`() = runVMTest(
        newVM = {
            val inspector = inspector("main")
            val scope = InspectorScope.Session(Demo.session.id)
            inspector.visibility.show(TimelineVM.key(scope))
            TimelineVM(inspector, scope)
        },
        block = { vm, _ ->
            runCurrent()
            val header = assertIs<TimelineHeader.Session>(vm.modelFlow.value.projection.header)
            assertEquals("Demo", header.name)
            assertEquals(4, header.viewModelCount)
        },
    )

    @Test
    fun `ten steps down select the tenth row`() = timeline { vm, inspector ->
        repeat(10) { vm.post(TimelineVIE.Intent.Step(+1)) }
        runCurrent()
        val rows = vm.projection.items.mapNotNull { it.sequence }
        assertEquals(rows[9], inspector.selection.state.value.row)
        vm.post(TimelineVIE.Intent.Step(-1))
        runCurrent()
        assertEquals(rows[8], inspector.selection.state.value.row)
    }

    @Test
    fun `stepping stops at the ends`() = timeline { vm, inspector ->
        val rows = vm.projection.items.mapNotNull { it.sequence }
        repeat(rows.size + 5) { vm.post(TimelineVIE.Intent.Step(+1)) }
        runCurrent()
        assertEquals(rows.last(), inspector.selection.state.value.row)
    }

    @Test
    fun `Edit and intervene selects the row and records the request for its ViewModel`() = timeline { vm, inspector ->
        vm.post(TimelineVIE.Intent.ComposeFrom(seq(208)))
        runCurrent()
        val selection = inspector.selection.state.value
        assertEquals(seq(208), selection.row)
        assertEquals(RightTab.Composer, selection.tab)
        val request = selection.composerRequest
        assertEquals(Demo.taskList, request?.viewModelId)
        assertEquals(VIEObjectKind.Model, request?.kind)
        assertEquals(Demo.TaskListModel, request?.type)
        assertTrue(request!!.json.contains("Deleting"))
    }

    @Test
    fun `a query updates the controls, and the rows once the projection lands`() = timeline { vm, _ ->
        val before = vm.projection.items.size
        vm.post(TimelineVIE.Intent.SetQuery("network unreachable"))
        runCurrent()

        assertEquals("network unreachable", vm.controls.filter.query)
        assertTrue(vm.projection.filterActive)
        assertTrue(vm.projection.hidden > 0)
        assertTrue(vm.projection.items.size < before)
        // The search hit is the Error signal, found in its toString.
        // (The drop report is a bookend: it always passes.)
        assertEquals(listOf(seq(251)), vm.projection.items.filter { it.observation != null }.mapNotNull { it.sequence })
    }

    @Test
    fun `turning Signal off hides its rows`() = timeline("filtered") { vm, _ ->
        vm.post(TimelineVIE.Intent.ToggleKind(VIEObjectKind.Signal))
        runCurrent()
        assertTrue(vm.projection.items.none { it.observation?.kind == VIEObjectKind.Signal })
        assertEquals(1, vm.projection.hidden)
        vm.post(TimelineVIE.Intent.ClearFilters)
        runCurrent()
        assertEquals(0, vm.projection.hidden)
        assertTrue(!vm.projection.filterActive)
    }

    @Test
    fun `a selected row the filters hide is offered, and show clears the filters that hide it`() = timeline { vm, inspector ->
        inspector.selection.selectRow(seq(208))
        vm.post(TimelineVIE.Intent.ToggleKind(VIEObjectKind.Model))
        vm.post(TimelineVIE.Intent.ToggleKind(VIEObjectKind.Signal))
        runCurrent()
        assertTrue(vm.projection.selectedHidden)

        vm.post(TimelineVIE.Intent.ShowSelectedRow)
        runCurrent()

        assertTrue(!vm.projection.selectedHidden)
        assertEquals(setOf(VIEObjectKind.Model, VIEObjectKind.Intent), vm.controls.filter.kinds, "only the filter that hid the row was cleared")
    }

    @Test
    fun `entries that arrive while the developer scrolled away are counted, and resuming zeroes them`() = timeline { vm, inspector ->
        vm.post(TimelineVIE.Intent.ScrolledAway)
        runCurrent()
        assertEquals(FollowState.Paused(0), vm.controls.follow)

        inspector.store.post(rowOfTaskList(272))
        runCurrent()
        assertEquals(FollowState.Paused(1), vm.controls.follow)

        vm.post(TimelineVIE.Intent.Resume)
        runCurrent()
        assertEquals(FollowState.Following, vm.controls.follow)
    }

    @Test
    fun `a hidden Timeline's model does not change on a publication, and catches up once shown`() = timeline { vm, inspector ->
        val key = TimelineVM.key(taskListScope)
        inspector.visibility.hide(key)
        runCurrent()
        val before = vm.modelFlow.value

        inspector.store.post(rowOfTaskList(272))
        runCurrent()
        assertEquals(before, vm.modelFlow.value)

        inspector.visibility.show(key)
        runCurrent()
        assertTrue(vm.projection.items.any { it.sequence == seq(272) })
    }

    @Test
    fun `the controls survive switching away and back`() = timeline { vm, inspector ->
        val key = TimelineVM.key(taskListScope)
        vm.post(TimelineVIE.Intent.SetQuery("Error"))
        vm.post(TimelineVIE.Intent.ToggleExpanded(seq(208)))
        runCurrent()
        inspector.visibility.hide(key)
        runCurrent()
        inspector.visibility.show(key)
        runCurrent()

        assertEquals("Error", vm.controls.filter.query)
        assertEquals(setOf(seq(208)), vm.controls.expanded)
    }

    @Test
    fun `a cache entry goes with its evicted row`() {
        val data = ScenarioData.of("main")
        val projector = TimelineProjector(taskListScope)
        val controls = TimelineVIE.Controls(filter = TimelineFilter(query = "no row has this"))
        val state = data.state()
        projector.project(state, Selection(), controls)
        val oldest = state.sessions.getValue(Demo.session.id).viewModels.getValue(Demo.taskList).rows.first().sequence
        assertTrue(oldest in projector.cachedRows)

        // One more row: the bounds evict the oldest.
        val after = state.on(rowOfTaskList(272)).state
        projector.project(after, Selection(), controls)

        assertTrue(oldest !in projector.cachedRows)
        assertTrue(seq(272) in projector.cachedRows)
    }

    @Test
    fun `a ViewModel with no row yet has an empty timeline under its header`() =
        timeline("noEntries", InspectorScope.ViewModel(Demo.session.id, Demo.silent)) { vm, _ ->
            assertEquals(0, vm.projection.total)
            assertTrue(vm.projection.items.none { it.row != null })
            assertIs<TimelineHeader.ViewModel>(vm.projection.header)
        }

    private fun rowOfTaskList(sequence: Long): TimeMachineEvent = TimeMachineEvent.MessageReceived(
        Demo.connection,
        org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage.Observed(
            org.kodein.arch.vie.timemachine.protocol.VIEObservation(
                Demo.taskList, Demo.now, VIEObjectKind.Model, Demo.TaskListModel, "Model(tasks=[], work=[])",
                Demo.taskListModel(emptyList()),
            ),
            seq(sequence),
        ),
        length = 100,
        now = Demo.now,
    )
}
