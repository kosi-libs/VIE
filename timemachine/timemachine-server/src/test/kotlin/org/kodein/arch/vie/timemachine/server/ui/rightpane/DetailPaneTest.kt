@file:OptIn(ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
import org.kodein.arch.vie.timemachine.server.ui.common.interventionNumber
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import org.kodein.arch.vie.timemachine.server.ui.common.DiffKind


class DetailPaneTest {

    private fun detail(block: suspend TestScope.(DetailVM, Inspector) -> Unit): TestResult {
        lateinit var inspector: Inspector
        return runVMTest(
            newVM = {
                inspector = inspector("main")
                inspector.visibility.show(DetailVM.Key)
                DetailVM(inspector)
            },
            block = { vm, _ -> runCurrent(); block(vm, inspector) },
        )
    }

    private val DetailVM.projection get() = modelFlow.value.projection

    @Test
    fun `it shows the selected row, a Model's diff against the one before, and an intervention's number`() = detail { vm, inspector ->
        assertNull(vm.projection.row)

        inspector.selection.selectScope(taskListScope)
        inspector.selection.selectRow(TimeMachineSequence(208))
        runCurrent()
        assertEquals(TimeMachineSequence(208), vm.projection.row?.sequence)
        assertEquals(Demo.taskList, vm.projection.viewModelId)
        assertEquals(TimeMachineSequence(206), vm.projection.diff?.previous)
        assertEquals(listOf(DiffKind.Added), vm.projection.diff?.ops?.map { it.kind })

        inspector.selection.selectRow(TimeMachineSequence(238))
        runCurrent()
        assertEquals(3, vm.projection.interventionNumber)
    }

    @Test
    fun `Edit and intervene opens the Composer on the selected row, and only when it can intervene`() = detail { vm, inspector ->
        inspector.selection.selectScope(taskListScope)
        inspector.selection.selectRow(TimeMachineSequence(266))
        runCurrent()
        assertTrue(vm.projection.canIntervene)

        vm.post(DetailVIE.Intent.EditAndIntervene)
        runCurrent()

        val state = inspector.selection.state.value
        assertEquals(RightTab.Composer, state.tab)
        assertEquals(Demo.DeleteTask, state.composerRequest?.type)
        assertEquals(VIEObjectKind.Intent, state.composerRequest?.kind)
    }

    @Test
    fun `Show on an intervened row goes to the Composer tab`() = detail { vm, inspector ->
        vm.post(DetailVIE.Intent.ShowInterventions)
        runCurrent()
        assertEquals(RightTab.Composer, inspector.selection.state.value.tab)
    }

    @Test
    fun `a row the store evicted is none`() = detail { vm, inspector ->
        inspector.selection.selectScope(taskListScope)
        inspector.selection.selectRow(TimeMachineSequence(200))
        runCurrent()
        assertNull(vm.projection.row)
    }
}
