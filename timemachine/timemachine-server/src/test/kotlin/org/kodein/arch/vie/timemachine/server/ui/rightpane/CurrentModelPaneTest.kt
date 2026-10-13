package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlinx.coroutines.test.TestResult
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.test.VMTestScope
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


class CurrentModelPaneTest {

    private fun current(block: suspend VMTestScope<CurrentModelVIE.Model, CurrentModelVIE.Intent, VIE.NoSignal>.(Inspector) -> Unit): TestResult {
        lateinit var inspector: Inspector
        return runVMTest(
            newVM = {
                inspector = inspector("main")
                inspector.visibility.show(CurrentModelVM.key(Demo.taskList))
                CurrentModelVM(inspector, Demo.session.id, Demo.taskList)
            },
            block = {
                runCurrent();
                block(inspector)
            },
        )
    }

    @Test
    fun `it pins the latest Model, and the nodes that changed from the one before`() = current {
        assertEquals(TimeMachineSequence(270), model.projection.sequence)
        assertEquals(Demo.TaskListModel, model.projection.typeName)
        assertEquals(TimeMachineSequence(238), model.projection.changedFrom)
        // #238 (the third intervention) had two tasks; #270 has the milk task only.
        assertTrue(model.projection.changedPaths.isNotEmpty())
        assertNull(model.projection.rawText)
    }

    @Test
    fun `raw asks for the text, and a tree node toggles`() = current {
        post(CurrentModelVIE.Intent.SetRaw(true))
        post(CurrentModelVIE.Intent.ToggleNode(listOf("tasks")))
        runCurrent()
        assertTrue(model.projection.rawText!!.contains("\"tasks\""))
        assertEquals(setOf(emptyList(), listOf("tasks")), model.controls.expanded)

        post(CurrentModelVIE.Intent.ToggleNode(listOf("tasks")))
        runCurrent()
        assertEquals(setOf(emptyList()), model.controls.expanded)
    }

    @Test
    fun `Edit and replace opens the Composer on the current Model`() = current { inspector ->
        post(CurrentModelVIE.Intent.EditAndReplace)
        runCurrent()
        val request = inspector.selection.state.value.composerRequest!!
        assertEquals(Demo.taskList, request.viewModelId)
        assertEquals(VIEObjectKind.Model, request.kind)
        assertEquals(Demo.TaskListModel, request.type)
        assertEquals(RightTab.Composer, inspector.selection.state.value.tab)
    }
}
