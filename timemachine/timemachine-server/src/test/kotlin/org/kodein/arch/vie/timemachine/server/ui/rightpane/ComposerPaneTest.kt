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
import org.kodein.arch.vie.timemachine.server.store.InterventionStatus
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import kotlin.test.Test
import kotlin.uuid.Uuid
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds


class ComposerPaneTest {

    private fun composer(
        scenario: String = "main",
        viewModelId: kotlin.uuid.Uuid = Demo.taskList,
        shown: Boolean = true,
        block: suspend VMTestScope<ComposerVIE.Model, ComposerVIE.Intent, VIE.NoSignal>.(Inspector) -> Unit,
    ): TestResult {
        lateinit var inspector: Inspector
        return runVMTest(
            newVM = {
                inspector = inspector(scenario)
                if (shown) inspector.visibility.show(ComposerVM.key(viewModelId))
                ComposerVM(inspector, Demo.session.id, viewModelId)
            },
            block = {
                runCurrent()
                block(inspector)
            },
        )
    }

    @Test
    fun `it starts from the latest observed Model of the ViewModel`() = composer {
        val target = model.projection.target!!
        assertEquals(VIEObjectKind.Model, target.kind)
        assertEquals(Demo.TaskListModel, target.type)
        assertEquals(PrefillSource.Observed(TimeMachineSequence(270)), target.prefill?.source)
        assertTrue(model.projection.canSend)
        assertEquals(4, model.projection.history.size)
        assertEquals(4, model.projection.history.first().number, "newest first")
    }

    @Test
    fun `typing does not serialize the prefill again`() = composer {
        post(ComposerVIE.Intent.SetText("{\"a\": "))
        runCurrent()
        val firstParse = assertNotNull(model.projection.invalid, "the pane projected, for the first keystroke")
        val first = model.projection.target?.prefill?.json
        assertNotNull(first, "the latest Model of the ViewModel is its prefill")

        post(ComposerVIE.Intent.SetText("{\"a\": 1, \"b\": "))
        runCurrent()

        val secondParse = assertNotNull(model.projection.invalid)
        assertTrue(secondParse.column > firstParse.column, "the pane projected again, for the second keystroke")
        assertSame(first, model.projection.target?.prefill?.json, "the prefill text is the one already built, not written out again")
    }

    @Test
    fun `nothing blocks a live ViewModel, and the target is named`() = composer {
        assertNull(model.projection.blockText)
        assertEquals("TaskListVM", model.projection.name)
        assertEquals("Demo", model.projection.sessionName)
    }

    @Test
    fun `a closed ViewModel says why in plain words`() =
        composer(scenario = "closedVM", viewModelId = Demo.editTaskClosed) {
            val text = assertNotNull(model.projection.blockText)
            assertTrue("closed" in text && "can’t receive interventions" in text, text)
        }

    @Test
    fun `a ViewModel nobody described is named by its short identifier`() =
        composer(scenario = "unknownVM", viewModelId = Demo.unknown) {
            assertEquals("d4c8", model.projection.name)
            val text = assertNotNull(model.projection.blockText)
            assertTrue(text.startsWith("d4c8 isn’t identified yet"), text)
        }

    @Test
    fun `a ViewModel that closed unknown is named by its short identifier`() =
        composer(scenario = "unknownClosed", viewModelId = Demo.unknown) {
            assertEquals("d4c8", model.projection.name)
            val text = assertNotNull(model.projection.blockText)
            assertTrue(text.startsWith("d4c8 closed before"), text)
        }

    @Test
    fun `a disconnected session says so`() = composer(scenario = "disconnected") {
        val text = assertNotNull(model.projection.blockText)
        assertTrue("disconnected" in text && "nothing can reach the app" in text, text)
    }

    @Test
    fun `choosing a kind prefills from the latest of its type`() = composer {
        post(ComposerVIE.Intent.SetKind(VIEObjectKind.Intent))
        runCurrent()
        assertEquals(PrefillSource.Observed(TimeMachineSequence(266)), model.projection.target?.prefill?.source)
        assertTrue(model.editorText.contains("taskId"))
        assertEquals(listOf(Demo.DeleteTask, Demo.UpdateTaskCompletion), model.projection.target?.types)
    }

    @Test
    fun `a request is applied once, and the holder forgets it only after`() = composer { inspector ->
        inspector.selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, "{\"#type\": \"x\"}")
        assertTrue(inspector.selection.state.value.composerRequest != null, "kept until process has run")

        runCurrent()

        assertEquals("{\"#type\": \"x\"}", model.controls.draft)
        assertEquals(VIEObjectKind.Intent, model.controls.kind)
        assertEquals(Demo.DeleteTask, model.controls.chosenType)
        assertNull(inspector.selection.state.value.composerRequest)

        // The developer types; nothing applies the request a second time.
        post(ComposerVIE.Intent.SetText("{}"))
        runCurrent()
        assertEquals("{}", model.controls.draft)
    }

    @Test
    fun `a request for another ViewModel is neither applied nor taken`() = composer { inspector ->
        inspector.selection.composeFrom(Demo.editTask, VIEObjectKind.Model, Demo.EditTaskModel, "{}")
        runCurrent()

        assertNull(model.controls.draft)
        assertEquals(Demo.editTask, inspector.selection.state.value.composerRequest?.viewModelId)
    }

    @Test
    fun `a request made while the pane is hidden is applied when it is shown`() =
        composer(shown = false) { inspector ->
            inspector.selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, "{\"#type\": \"x\"}")
            runCurrent()
            assertEquals(VIEObjectKind.Model, model.projection.target?.kind, "nothing is projected while hidden")

            inspector.visibility.show(ComposerVM.key(Demo.taskList))
            runCurrent()

            assertEquals(VIEObjectKind.Intent, model.projection.target?.kind)
            assertEquals("{\"#type\": \"x\"}", model.editorText)
            assertNull(inspector.selection.state.value.composerRequest)
        }

    @Test
    fun `Send is disabled until the JSON parses, and sends nothing meanwhile`() = composer {
        post(ComposerVIE.Intent.SetText("{\"title\": "))
        runCurrent()
        assertNotNull(model.projection.invalid)
        assertTrue(!model.projection.canSend)

        post(ComposerVIE.Intent.Send)
        runCurrent()

        assertNull(model.controls.requestId)
        assertEquals(4, model.projection.history.size)
    }

    @Test
    fun `Send asks the store, and the probe's tagged row turns the card into a success`() = composer {
        post(ComposerVIE.Intent.Send)
        runCurrent()

        val current = model.projection.current!!
        assertEquals(5, current.number, "added to the history right away")
        assertEquals(VIEObjectKind.Model, current.kind)
        assertEquals(InterventionStatus.Succeeded(TimeMachineSequence(272)), current.status)
        assertTrue(current.rowRetained)
        assertEquals(5, model.projection.history.first().number)
    }

    @Test
    fun `an intervention the probe never answers times out at its deadline`() = composer("compTimeout") {
        post(ComposerVIE.Intent.Send)
        runCurrent()
        assertEquals(InterventionStatus.Pending, model.projection.current?.status)

        advanceTimeBy(5.seconds + 1.seconds)
        runCurrent()

        assertEquals(InterventionStatus.TimedOut, model.projection.current?.status)
    }

    @Test
    fun `the projection carries the timeout of the store it reads`() = composer("compPending") {
        assertEquals(1.hours, model.projection.timeout)
    }

    @Test
    fun `a refusal shows its reason`() = composer("compFailure") {
        post(ComposerVIE.Intent.Send)
        runCurrent()

        val status = assertIs<InterventionStatus.Failed>(model.projection.current?.status)
        assertTrue(status.failure.message!!.contains("taskId"))
    }

    @Test
    fun `a success whose row never comes is accepted, not observed, after the deadline`() = composer("compApplied") {
        post(ComposerVIE.Intent.Send)
        runCurrent()
        advanceTimeBy(6.seconds)
        runCurrent()

        assertEquals(InterventionStatus.AcceptedNotObserved, model.projection.current?.status)
    }

    @Test
    fun `a disconnected session blocks the composer and Send does nothing`() = composer("disconnected") {
        assertEquals(ComposerBlock.SessionDisconnected, model.projection.target?.block)
        assertTrue(!model.projection.canSend)
        post(ComposerVIE.Intent.Send)
        runCurrent()
        assertNull(model.controls.requestId)
    }

    @Test
    fun `an intervention not sent shows its card when asked for`() = composer("compNotSent") {
        post(ComposerVIE.Intent.ShowResult(Demo.requestNotSent))
        runCurrent()
        assertEquals(InterventionStatus.NotSent, model.projection.current?.status)
    }

    @Test
    fun `a non-serializable kind is disabled`() = composer("compDisabled", Demo.settings) {
        post(ComposerVIE.Intent.SetKind(VIEObjectKind.Signal))
        runCurrent()
        assertTrue(VIEObjectKind.Signal !in model.projection.target!!.enabledKinds)
        assertTrue(!model.projection.canSend)
        // The Model of this ViewModel is sealed: its subtypes, and a `#type` prefill.
        post(ComposerVIE.Intent.SetKind(VIEObjectKind.Model))
        runCurrent()
        assertEquals(listOf(Demo.SettingsLoading, Demo.SettingsLoaded), model.projection.target?.types)
    }

    @Test
    fun `formatting pretty-prints the draft, and reset goes back to the prefill`() = composer {
        post(ComposerVIE.Intent.SetText("{\"a\":1}"))
        post(ComposerVIE.Intent.Format)
        runCurrent()
        assertEquals("{\n    \"a\": 1\n}", model.controls.draft)

        post(ComposerVIE.Intent.Reset)
        runCurrent()
        assertNull(model.controls.draft)
    }

    private val taskListScope = InspectorScope.ViewModel(Demo.session.id, Demo.taskList)

    @Test
    fun `a typed draft stays the draft of the type it was typed for when another row is selected`() = composer { inspector ->
        post(ComposerVIE.Intent.SetKind(VIEObjectKind.Intent))
        runCurrent()
        assertNull(model.controls.chosenType)
        assertEquals(Demo.DeleteTask, model.projection.target?.type)

        // With no draft, the type follows the selected row: it is what the draft must not do.
        inspector.selection.selectRowIn(taskListScope, TimeMachineSequence(203))
        runCurrent()
        assertEquals(Demo.UpdateTaskCompletion, model.projection.target?.type)

        val draft = "{\"#type\": \"${Demo.UpdateTaskCompletion}\", \"taskId\": 1, \"isCompleted\": true}"
        post(ComposerVIE.Intent.SetText(draft))
        runCurrent()
        assertEquals(Demo.UpdateTaskCompletion, model.controls.chosenType, "the draft is of the type the editor showed")

        inspector.selection.selectRowIn(taskListScope, TimeMachineSequence(207))
        runCurrent()
        assertEquals(Demo.UpdateTaskCompletion, model.projection.target?.type)
        assertEquals(draft, model.editorText)

        post(ComposerVIE.Intent.Send)
        runCurrent()
        assertEquals(Demo.UpdateTaskCompletion, model.projection.history.first().typeName, "the draft was sent as another type")
    }

    @Test
    fun `formatting also keeps the type the draft is of, and a type chosen by hand is kept`() = composer { inspector ->
        post(ComposerVIE.Intent.SetKind(VIEObjectKind.Intent))
        inspector.selection.selectRowIn(taskListScope, TimeMachineSequence(203))
        runCurrent()
        post(ComposerVIE.Intent.Format)
        runCurrent()
        assertEquals(Demo.UpdateTaskCompletion, model.controls.chosenType)

        inspector.selection.selectRowIn(taskListScope, TimeMachineSequence(207))
        runCurrent()
        assertEquals(Demo.UpdateTaskCompletion, model.projection.target?.type)

        post(ComposerVIE.Intent.SetType(Demo.DeleteTask))
        post(ComposerVIE.Intent.SetText("{}"))
        runCurrent()
        assertEquals(Demo.DeleteTask, model.controls.chosenType, "an explicit choice is not replaced")
    }

    @Test
    fun `the history is the 20 latest interventions, and counts the older ones`() = composer { inspector ->
        assertEquals(0, model.projection.olderInterventions)

        Demo.Builder(Demo.connection).apply {
            repeat(25) {
                intervene(Demo.taskList, Uuid.fromLongs(7L, it.toLong()), VIEObjectKind.Intent, Demo.DeleteTask, Demo.deleteTask(Demo.Milk), Demo.at(29_000L + it))
            }
        }.events.forEach(inspector.store::post)
        runCurrent()

        // 4 in the scenario and 25 more: #29 down to #10 are listed, #9 down to #1 are counted.
        assertEquals((29 downTo 10).toList(), model.projection.history.map { it.number })
        assertEquals(9, model.projection.olderInterventions)
    }

    @Test
    fun `an intervention of the history can be loaded and sent again`() = composer {
        val first = model.projection.history.last()
        post(ComposerVIE.Intent.LoadFromHistory(first.requestId))
        runCurrent()
        assertEquals(first.kind, model.controls.kind)
        assertEquals(first.typeName, model.controls.chosenType)

        post(ComposerVIE.Intent.Resend(first.requestId))
        runCurrent()
        assertEquals(5, model.projection.history.size)
        assertEquals(first.asJson, model.projection.history.first().asJson)
    }

    @Test
    fun `two Sends in a row ask the store once, and Send waits for the answer`() = composer("compPending") {
        post(ComposerVIE.Intent.Send)
        post(ComposerVIE.Intent.Send)
        runCurrent()

        assertEquals(5, model.projection.history.size, "one intervention was added, not two")
        assertEquals(InterventionStatus.Pending, model.projection.current?.status)
        assertTrue(!model.projection.canSend, "Send stays disabled while the intervention is pending")

        post(ComposerVIE.Intent.Send)
        runCurrent()
        assertEquals(5, model.projection.history.size, "a Send once the card shows is still refused")
    }

    @Test
    fun `Send is possible again once the intervention timed out`() = composer("compTimeout") {
        post(ComposerVIE.Intent.Send)
        runCurrent()
        assertTrue(!model.projection.canSend)

        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(InterventionStatus.TimedOut, model.projection.current?.status)
        assertTrue(model.projection.canSend)

        post(ComposerVIE.Intent.Send)
        runCurrent()
        assertEquals(6, model.projection.history.size)
    }

    @Test
    fun `showing the result of an older intervention does not free Send`() = composer("compPending") {
        post(ComposerVIE.Intent.Send)
        runCurrent()
        post(ComposerVIE.Intent.ShowResult(model.projection.history.last().requestId))
        runCurrent()
        assertTrue(!model.projection.canSend, "the one just sent is still pending")

        post(ComposerVIE.Intent.Send)
        runCurrent()
        assertEquals(5, model.projection.history.size)
    }

    @Test
    fun `two Resends in a row send once`() = composer("compPending") {
        val first = model.projection.history.last()
        post(ComposerVIE.Intent.Resend(first.requestId))
        post(ComposerVIE.Intent.Resend(first.requestId))
        runCurrent()

        assertEquals(5, model.projection.history.size)
    }

    @Test
    fun `Resend with nothing able to send leaves the draft alone`() = composer("disconnected") {
        post(ComposerVIE.Intent.SetText("{\"typed\": true}"))
        runCurrent()
        val draft = model.controls.draft

        post(ComposerVIE.Intent.Resend(model.projection.history.last().requestId))
        runCurrent()

        assertEquals(draft, model.controls.draft, "the draft is not replaced by a request that cannot go")
        assertEquals(4, model.projection.history.size)
    }

    @Test
    fun `its first model is the target the store holds, before the pane is shown`() = composer(shown = false) {
        val target = assertNotNull(model.projection.target)
        assertEquals(VIEObjectKind.Model, target.kind)
        assertEquals(Demo.TaskListModel, target.type)
        assertEquals("TaskListVM", model.projection.name)
        assertEquals(4, model.projection.history.size)
    }

    @Test
    fun `a request the holder has when the composer is created is in its first model`() = runVMTest(
        newVM = {
            val inspector = inspector("main")
            inspector.selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, "{\"#type\": \"x\"}")
            ComposerVM(inspector, Demo.session.id, Demo.taskList)
        },
        block = {
            // The first model a view gets: the one it was created with, before any of its coroutines ran.
            assertEquals(VIEObjectKind.Intent, pendingModels().first().projection.target?.kind)
            assertEquals("{\"#type\": \"x\"}", model.editorText)
        },
    )
}
