@file:OptIn(ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.store.InterventionStatus
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.key


class ComposerPaneTest {

    private fun composer(
        scenario: String = "main",
        viewModelId: kotlin.uuid.Uuid = Demo.taskList,
        shown: Boolean = true,
        block: suspend TestScope.(ComposerVM, Inspector) -> Unit,
    ): TestResult {
        lateinit var inspector: Inspector
        return runVMTest(
            newVM = {
                inspector = inspector(scenario)
                if (shown) inspector.visibility.show(ComposerVM.key(viewModelId))
                ComposerVM(inspector, Demo.session.id, viewModelId)
            },
            block = { vm, _ -> runCurrent(); block(vm, inspector) },
        )
    }

    private val ComposerVM.projection get() = modelFlow.value.projection

    private val ComposerVM.controls get() = modelFlow.value.controls

    @Test
    fun `it starts from the latest observed Model of the ViewModel`() = composer { vm, _ ->
        val target = vm.projection.target!!
        assertEquals(VIEObjectKind.Model, target.kind)
        assertEquals(Demo.TaskListModel, target.type)
        assertEquals(PrefillSource.Observed(TimeMachineSequence(270)), target.prefill?.source)
        assertTrue(vm.projection.canSend)
        assertEquals(4, vm.projection.history.size)
        assertEquals(4, vm.projection.history.first().number, "newest first")
    }

    @Test
    fun `the type menu opens, and choosing a type or a kind closes it`() = composer { vm, _ ->
        vm.post(ComposerVIE.Intent.SetKind(VIEObjectKind.Intent))
        vm.post(ComposerVIE.Intent.SetTypeMenu(true))
        runCurrent()
        assertTrue(vm.controls.typeMenuOpen)

        vm.post(ComposerVIE.Intent.SetType(Demo.DeleteTask))
        runCurrent()
        assertTrue(!vm.controls.typeMenuOpen)
    }

    @Test
    fun `nothing blocks a live ViewModel, and the target is named`() = composer { vm, _ ->
        assertNull(vm.projection.blockText)
        assertEquals("TaskListVM", vm.projection.name)
        assertEquals("Demo", vm.projection.sessionName)
    }

    @Test
    fun `a closed ViewModel says why in plain words`() = composer(scenario = "closedVM", viewModelId = Demo.editTaskClosed) { vm, _ ->
        val text = assertNotNull(vm.projection.blockText)
        assertTrue("closed" in text && "can’t receive interventions" in text, text)
    }

    @Test
    fun `a disconnected session says so`() = composer(scenario = "disconnected") { vm, _ ->
        val text = assertNotNull(vm.projection.blockText)
        assertTrue("disconnected" in text && "nothing can reach the app" in text, text)
    }

    @Test
    fun `choosing a kind prefills from the latest of its type`() = composer { vm, _ ->
        vm.post(ComposerVIE.Intent.SetKind(VIEObjectKind.Intent))
        runCurrent()
        assertEquals(PrefillSource.Observed(TimeMachineSequence(266)), vm.projection.target?.prefill?.source)
        assertTrue(vm.projection.text.contains("taskId"))
        assertEquals(listOf(Demo.DeleteTask, Demo.UpdateTaskCompletion), vm.projection.target?.types)
    }

    @Test
    fun `a request is applied once, and the holder forgets it only after`() = composer { vm, inspector ->
        inspector.selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, "{\"#type\": \"x\"}")
        assertTrue(inspector.selection.state.value.composerRequest != null, "kept until process has run")

        runCurrent()

        assertEquals("{\"#type\": \"x\"}", vm.controls.draft)
        assertEquals(VIEObjectKind.Intent, vm.controls.kind)
        assertEquals(Demo.DeleteTask, vm.controls.chosenType)
        assertNull(inspector.selection.state.value.composerRequest)

        // The developer types; nothing applies the request a second time.
        vm.post(ComposerVIE.Intent.SetText("{}"))
        runCurrent()
        assertEquals("{}", vm.controls.draft)
    }

    @Test
    fun `a request for another ViewModel is neither applied nor taken`() = composer { vm, inspector ->
        inspector.selection.composeFrom(Demo.editTask, VIEObjectKind.Model, Demo.EditTaskModel, "{}")
        runCurrent()

        assertNull(vm.controls.draft)
        assertEquals(Demo.editTask, inspector.selection.state.value.composerRequest?.viewModelId)
    }

    @Test
    fun `a request made while the pane is hidden is applied when it is shown`() = composer(shown = false) { vm, inspector ->
        inspector.selection.composeFrom(Demo.taskList, VIEObjectKind.Intent, Demo.DeleteTask, "{\"#type\": \"x\"}")
        runCurrent()
        assertEquals("", vm.projection.text, "nothing is projected while hidden")

        inspector.visibility.show(ComposerVM.key(Demo.taskList))
        runCurrent()

        assertEquals("{\"#type\": \"x\"}", vm.projection.text)
        assertNull(inspector.selection.state.value.composerRequest)
    }

    @Test
    fun `Send is disabled until the JSON parses, and sends nothing meanwhile`() = composer { vm, _ ->
        vm.post(ComposerVIE.Intent.SetText("{\"title\": "))
        runCurrent()
        assertIs<JsonParse.Invalid>(vm.projection.parse)
        assertTrue(!vm.projection.canSend)

        vm.post(ComposerVIE.Intent.Send)
        runCurrent()

        assertNull(vm.controls.requestId)
        assertEquals(4, vm.projection.history.size)
    }

    @Test
    fun `Send asks the store, and the probe's tagged row turns the card into a success`() = composer { vm, _ ->
        vm.post(ComposerVIE.Intent.Send)
        runCurrent()

        val current = vm.projection.current!!
        assertEquals(5, current.number, "added to the history right away")
        assertEquals(VIEObjectKind.Model, current.kind)
        assertEquals(InterventionStatus.Succeeded(TimeMachineSequence(272)), current.status)
        assertTrue(current.rowRetained)
        assertEquals(5, vm.projection.history.first().number)
    }

    @Test
    fun `an intervention the probe never answers times out at its deadline`() = composer("compTimeout") { vm, _ ->
        vm.post(ComposerVIE.Intent.Send)
        runCurrent()
        assertEquals(InterventionStatus.Pending, vm.projection.current?.status)

        advanceTimeBy(5.seconds + 1.seconds)
        runCurrent()

        assertEquals(InterventionStatus.TimedOut, vm.projection.current?.status)
    }

    @Test
    fun `a refusal shows its reason`() = composer("compFailure") { vm, _ ->
        vm.post(ComposerVIE.Intent.Send)
        runCurrent()

        val status = assertIs<InterventionStatus.Failed>(vm.projection.current?.status)
        assertTrue(status.failure.message!!.contains("taskId"))
    }

    @Test
    fun `a success whose row never comes is accepted, not observed, after the deadline`() = composer("compApplied") { vm, _ ->
        vm.post(ComposerVIE.Intent.Send)
        runCurrent()
        advanceTimeBy(6.seconds)
        runCurrent()

        assertEquals(InterventionStatus.AcceptedNotObserved, vm.projection.current?.status)
    }

    @Test
    fun `a disconnected session blocks the composer and Send does nothing`() = composer("disconnected") { vm, _ ->
        assertEquals(ComposerBlock.SessionDisconnected, vm.projection.target?.block)
        assertTrue(!vm.projection.canSend)
        vm.post(ComposerVIE.Intent.Send)
        runCurrent()
        assertNull(vm.controls.requestId)
    }

    @Test
    fun `an intervention not sent shows its card when asked for`() = composer("compNotSent") { vm, _ ->
        vm.post(ComposerVIE.Intent.ShowResult(Demo.requestNotSent))
        runCurrent()
        assertEquals(InterventionStatus.NotSent, vm.projection.current?.status)
    }

    @Test
    fun `a non-serializable kind is disabled`() = composer("compDisabled", Demo.settings) { vm, _ ->
        vm.post(ComposerVIE.Intent.SetKind(VIEObjectKind.Signal))
        runCurrent()
        assertTrue(VIEObjectKind.Signal !in vm.projection.target!!.enabledKinds)
        assertTrue(!vm.projection.canSend)
        // The Model of this ViewModel is sealed: its subtypes, and a `#type` prefill.
        vm.post(ComposerVIE.Intent.SetKind(VIEObjectKind.Model))
        runCurrent()
        assertEquals(listOf(Demo.SettingsLoading, Demo.SettingsLoaded), vm.projection.target?.types)
    }

    @Test
    fun `formatting pretty-prints the draft, and reset goes back to the prefill`() = composer { vm, _ ->
        vm.post(ComposerVIE.Intent.SetText("{\"a\":1}"))
        vm.post(ComposerVIE.Intent.Format)
        runCurrent()
        assertEquals("{\n    \"a\": 1\n}", vm.controls.draft)

        vm.post(ComposerVIE.Intent.Reset)
        runCurrent()
        assertNull(vm.controls.draft)
    }

    @Test
    fun `an intervention of the history can be loaded and sent again`() = composer { vm, _ ->
        val first = vm.projection.history.last()
        vm.post(ComposerVIE.Intent.LoadFromHistory(first.requestId))
        runCurrent()
        assertEquals(first.kind, vm.controls.kind)
        assertEquals(first.typeName, vm.controls.chosenType)

        vm.post(ComposerVIE.Intent.Resend(first.requestId))
        runCurrent()
        assertEquals(5, vm.projection.history.size)
        assertEquals(first.asJson, vm.projection.history.first().asJson)
    }
}
