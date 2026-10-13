@file:OptIn(ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.test.VMTestScope
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
import org.kodein.arch.vie.timemachine.server.ui.common.DiffKind
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


class DetailPaneTest {

    private fun detail(block: suspend VMTestScope<DetailVIE.Model, DetailVIE.Intent, VIE.NoSignal>.(Inspector) -> Unit): TestResult {
        lateinit var inspector: Inspector
        return runVMTest(
            newVM = {
                inspector = inspector("main")
                inspector.visibility.show(DetailVM.Key)
                DetailVM(inspector)
            },
            block = {
                runCurrent();
                block(inspector)
            },
        )
    }

    @Test
    fun `it shows the selected row, a Model's diff against the one before, and an intervention's number`() =
        detail { inspector ->
            assertNull(model.projection.row)

            inspector.selection.selectScope(taskListScope)
            inspector.selection.selectRow(TimeMachineSequence(208))
            runCurrent()
            assertEquals(TimeMachineSequence(208), model.projection.row?.sequence)
            assertEquals(Demo.taskList, model.projection.viewModelId)
            assertEquals(TimeMachineSequence(206), model.projection.diff?.previous)
            assertEquals(listOf(DiffKind.Added), model.projection.diff?.ops?.map { it.kind })

            inspector.selection.selectRow(TimeMachineSequence(238))
            runCurrent()
            assertEquals(3, model.projection.interventionNumber)
        }

    @Test
    fun `Edit and intervene opens the Composer on the selected row, and only when it can intervene`() =
        detail { inspector ->
            inspector.selection.selectScope(taskListScope)
            inspector.selection.selectRow(TimeMachineSequence(266))
            runCurrent()
            assertTrue(model.projection.canIntervene)

            post(DetailVIE.Intent.EditAndIntervene)
            runCurrent()

            val state = inspector.selection.state.value
            assertEquals(RightTab.Composer, state.tab)
            assertEquals(Demo.DeleteTask, state.composerRequest?.type)
            assertEquals(VIEObjectKind.Intent, state.composerRequest?.kind)
        }

    @Test
    fun `Show on an intervened row goes to the Composer tab`() = detail { inspector ->
        post(DetailVIE.Intent.ShowInterventions)
        runCurrent()
        assertEquals(RightTab.Composer, inspector.selection.state.value.tab)
    }

    @Test
    fun `a row the store evicted is none`() = detail { inspector ->
        inspector.selection.selectScope(taskListScope)
        inspector.selection.selectRow(TimeMachineSequence(200))
        runCurrent()
        assertNull(model.projection.row)
    }
}
