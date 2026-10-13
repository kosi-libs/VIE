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
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import org.kodein.arch.vie.timemachine.server.ui.core.key


class CurrentModelPaneTest {

    private fun current(block: suspend TestScope.(CurrentModelVM, Inspector) -> Unit): TestResult {
        lateinit var inspector: Inspector
        return runVMTest(
            newVM = {
                inspector = inspector("main")
                inspector.visibility.show(CurrentModelVM.key(Demo.taskList))
                CurrentModelVM(inspector, Demo.session.id, Demo.taskList)
            },
            block = { vm, _ -> runCurrent(); block(vm, inspector) },
        )
    }

    private val CurrentModelVM.projection get() = modelFlow.value.projection

    @Test
    fun `it pins the latest Model, and the nodes that changed from the one before`() = current { vm, _ ->
        assertEquals(TimeMachineSequence(270), vm.projection.sequence)
        assertEquals(Demo.TaskListModel, vm.projection.typeName)
        assertEquals(TimeMachineSequence(238), vm.projection.changedFrom)
        // #238 (the third intervention) had two tasks; #270 has the milk task only.
        assertTrue(vm.projection.changedPaths.isNotEmpty())
        assertNull(vm.projection.rawText)
    }

    @Test
    fun `raw asks for the text, and a tree node toggles`() = current { vm, _ ->
        vm.post(CurrentModelVIE.Intent.SetRaw(true))
        vm.post(CurrentModelVIE.Intent.ToggleNode(listOf("tasks")))
        runCurrent()
        assertTrue(vm.projection.rawText!!.contains("\"tasks\""))
        assertEquals(setOf(emptyList(), listOf("tasks")), vm.modelFlow.value.controls.expanded)

        vm.post(CurrentModelVIE.Intent.ToggleNode(listOf("tasks")))
        runCurrent()
        assertEquals(setOf(emptyList()), vm.modelFlow.value.controls.expanded)
    }

    @Test
    fun `Edit and replace opens the Composer on the current Model`() = current { vm, inspector ->
        vm.post(CurrentModelVIE.Intent.EditAndReplace)
        runCurrent()
        val request = inspector.selection.state.value.composerRequest!!
        assertEquals(Demo.taskList, request.viewModelId)
        assertEquals(VIEObjectKind.Model, request.kind)
        assertEquals(Demo.TaskListModel, request.type)
        assertEquals(RightTab.Composer, inspector.selection.state.value.tab)
    }
}
