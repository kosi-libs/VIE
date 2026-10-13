package org.kodein.arch.vie.timemachine.server.ui.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.demoSession
import org.kodein.arch.vie.timemachine.server.fixtures.state
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState


class InspectorSelectionTest {

    private val state: TimeMachineState = ScenarioData.of("main").state()

    private val editScope = InspectorScope.ViewModel(Demo.session.id, Demo.editTask)

    private fun seq(n: Long) = TimeMachineSequence(n)

    @Test
    fun `a scope change clears the row, keeps the tab and drops a request for another ViewModel`() {
        val selection = InspectorSelection()
        selection.selectScope(taskListScope)
        selection.selectRow(seq(208))
        selection.composeFrom(Demo.taskList, VIEObjectKind.Model, Demo.TaskListModel, "{}")

        selection.selectScope(editScope)

        assertEquals(Selection(scope = editScope, row = null, tab = RightTab.Composer, composerRequest = null), selection.state.value)
    }

    @Test
    fun `a scope change keeps a request for the new scope's ViewModel`() {
        val selection = InspectorSelection()
        selection.composeFrom(Demo.editTask, VIEObjectKind.Intent, Demo.EditTaskEdit, "{}")

        selection.selectScope(editScope)

        assertEquals(Demo.editTask, selection.state.value.composerRequest?.viewModelId)
    }

    @Test
    fun `selecting the scope already selected keeps the row`() {
        val selection = InspectorSelection()
        selection.selectScope(taskListScope)
        selection.selectRow(seq(208))

        selection.selectScope(taskListScope)

        assertEquals(seq(208), selection.state.value.row)
    }

    @Test
    fun `composeFrom opens the Composer tab and records the request`() {
        val selection = InspectorSelection()
        selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, "{\"a\":1}")

        assertEquals(RightTab.Composer, selection.state.value.tab)
        assertEquals(ComposerRequest(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, "{\"a\":1}"), selection.state.value.composerRequest)
    }

    @Test
    fun `takeRequest answers once, and only its ViewModel`() {
        val selection = InspectorSelection()
        selection.composeFrom(Demo.taskList, VIEObjectKind.Model, Demo.TaskListModel, "{}")

        assertNull(selection.takeRequest(Demo.editTask))
        assertEquals(Demo.taskList, selection.takeRequest(Demo.taskList)?.viewModelId)
        assertNull(selection.takeRequest(Demo.taskList))
        assertNull(selection.state.value.composerRequest)
    }

    @Test
    fun `resolvedIn turns a scope the store no longer holds into none, and its row with it`() {
        val gone = InspectorScope.ViewModel(Demo.session.id, Uuid.random())
        assertEquals(Selection(), Selection(scope = gone, row = seq(208)).resolvedIn(state))
        assertEquals(Selection(), Selection(scope = InspectorScope.Session(Uuid.random()), row = seq(208)).resolvedIn(state))
    }

    @Test
    fun `resolvedIn turns a row the scope does not retain into none`() {
        // 208 is TaskListVM's; 258 is EditTaskVM's; 200 was evicted.
        assertEquals(seq(208), Selection(scope = taskListScope, row = seq(208)).resolvedIn(state).row)
        assertNull(Selection(scope = taskListScope, row = seq(258)).resolvedIn(state).row)
        assertNull(Selection(scope = taskListScope, row = seq(200)).resolvedIn(state).row)
        assertNull(Selection(scope = taskListScope, row = seq(9999)).resolvedIn(state).row)
        // In a session scope, any of its ViewModels' rows will do.
        assertEquals(seq(258), Selection(scope = demoSession, row = seq(258)).resolvedIn(state).row)
    }

    @Test
    fun `the target is the scope's ViewModel, or in a session scope the selected row's`() {
        assertEquals(Demo.taskList, Selection(scope = taskListScope).target(state)?.vm?.id)
        assertEquals(Demo.taskList, Selection(scope = taskListScope, row = seq(208)).target(state)?.vm?.id)
        assertNull(Selection(scope = demoSession).target(state))
        assertEquals(Demo.editTask, Selection(scope = demoSession, row = seq(258)).target(state)?.vm?.id)
        assertNull(Selection().target(state))
    }
}
