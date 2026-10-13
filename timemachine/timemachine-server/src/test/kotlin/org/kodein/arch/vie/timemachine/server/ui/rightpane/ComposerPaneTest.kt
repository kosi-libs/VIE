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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    fun `the type menu opens, and choosing a type or a kind closes it`() = composer {
        post(ComposerVIE.Intent.SetKind(VIEObjectKind.Intent))
        post(ComposerVIE.Intent.SetTypeMenu(true))
        runCurrent()
        assertTrue(model.controls.typeMenuOpen)

        post(ComposerVIE.Intent.SetType(Demo.DeleteTask))
        runCurrent()
        assertTrue(!model.controls.typeMenuOpen)
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
    fun `a disconnected session says so`() = composer(scenario = "disconnected") {
        val text = assertNotNull(model.projection.blockText)
        assertTrue("disconnected" in text && "nothing can reach the app" in text, text)
    }

    @Test
    fun `choosing a kind prefills from the latest of its type`() = composer {
        post(ComposerVIE.Intent.SetKind(VIEObjectKind.Intent))
        runCurrent()
        assertEquals(PrefillSource.Observed(TimeMachineSequence(266)), model.projection.target?.prefill?.source)
        assertTrue(model.projection.text.contains("taskId"))
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
            assertEquals("", model.projection.text, "nothing is projected while hidden")

            inspector.visibility.show(ComposerVM.key(Demo.taskList))
            runCurrent()

            assertEquals("{\"#type\": \"x\"}", model.projection.text)
            assertNull(inspector.selection.state.value.composerRequest)
        }

    @Test
    fun `Send is disabled until the JSON parses, and sends nothing meanwhile`() = composer {
        post(ComposerVIE.Intent.SetText("{\"title\": "))
        runCurrent()
        assertIs<JsonParse.Invalid>(model.projection.parse)
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
}
