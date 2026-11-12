@file:OptIn(ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.test.VMTestScope
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.demoSession
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.RightTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


class RightPaneVMTest {

    private fun rightPane(
        id: String = "main",
        block: suspend VMTestScope<RightPaneVIE.Model, RightPaneVIE.Intent, VIE.NoSignal>.(Inspector) -> Unit,
    ): TestResult {
        lateinit var inspector: Inspector
        return runVMTest(
            newVM = {
                inspector = inspector(id)
                inspector.visibility.show(RightPaneVM.Key)
                RightPaneVM(inspector)
            },
            block = {
                runCurrent()
                block(inspector)
            },
        )
    }

    @Test
    fun `a ViewModel's scope is the target, with the interventions asked of it`() = rightPane { inspector ->
        assertEquals(RightPaneVIE.Model(), model)

        inspector.selection.selectScope(taskListScope)
        runCurrent()

        val asked = inspector.store.state.value.sessions.getValue(Demo.session.id).viewModels.getValue(Demo.taskList).interventions.size
        assertTrue(asked > 0)
        assertEquals(taskListScope, model.scope)
        assertEquals(RightTarget(Demo.session.id, Demo.taskList, asked), model.target)
    }

    @Test
    fun `a session's scope has no target until a row is selected, then the row's ViewModel`() = rightPane { inspector ->
        inspector.selection.selectScope(demoSession)
        runCurrent()
        assertEquals(demoSession, model.scope)
        assertNull(model.target)

        inspector.selection.selectRow(TimeMachineSequence(208))
        runCurrent()
        assertEquals(Demo.taskList, model.target?.viewModelId)
    }

    @Test
    fun `the tab is the selection's, and selecting one changes the selection`() = rightPane { inspector ->
        assertEquals(RightTab.Detail, model.tab)

        post(RightPaneVIE.Intent.SelectTab(RightTab.Composer))
        runCurrent()

        assertEquals(RightTab.Composer, inspector.selection.state.value.tab)
        assertEquals(RightTab.Composer, model.tab)
    }

    @Test
    fun `a scope the store dropped is none, and has no target`() = rightPane("disconnected") { inspector ->
        inspector.selection.selectScope(taskListScope)
        runCurrent()
        assertEquals(Demo.taskList, model.target?.viewModelId)

        inspector.store.post(TimeMachineEvent.ClearSession(Demo.session.id))
        runCurrent()

        assertNull(model.scope)
        assertNull(model.target)
    }

    @Test
    fun `the first model is what the store and the selection hold, before the pane is shown`() = runVMTest(
        newVM = {
            RightPaneVM(inspector("main").also {
                it.selection.selectScope(taskListScope)
                it.selection.selectTab(RightTab.Composer)
            })
        },
        block = {
            assertEquals(taskListScope, model.scope)
            assertEquals(Demo.taskList, model.target?.viewModelId)
            assertEquals(RightTab.Composer, model.tab)
        },
    )
}
