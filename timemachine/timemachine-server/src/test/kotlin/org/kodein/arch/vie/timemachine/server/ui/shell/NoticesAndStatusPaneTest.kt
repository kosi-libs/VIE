@file:OptIn(ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.FixedClock
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.fixtures.newInspector
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector


class NoticesAndStatusPaneTest {

    @Test
    fun `the empty and cannot-listen screens, and a retry that goes to the bundle`() {
        var retried = 0
        lateinit var inspector: Inspector
        runVMTest(
            newVM = {
                inspector = ScenarioData.of("cannotListen").newInspector(
                    backgroundScope, StandardTestDispatcher(testScheduler), FixedClock(Demo.now), retry = { retried++ },
                )
                inspector.visibility.show(NoticesVM.Key)
                NoticesVM(inspector)
            },
            block = { vm, _ ->
                runCurrent()
                assertEquals(NoticesVIE.Screen.CannotListen("Address already in use"), vm.modelFlow.value.screen)
                vm.post(NoticesVIE.Intent.Retry)
                runCurrent()
                assertEquals(1, retried)
            },
        )
    }

    @Test
    fun `no session yet is the empty screen`() = runVMTest(
        newVM = {
            val inspector = inspector("empty")
            inspector.visibility.show(NoticesVM.Key)
            NoticesVM(inspector)
        },
        block = { vm, _ ->
            runCurrent()
            assertEquals(NoticesVIE.Screen.Empty, vm.modelFlow.value.screen)
        },
    )

    @Test
    fun `a rejected probe is one notice however often it retried, until it is dismissed`() {
        lateinit var inspector: Inspector
        runVMTest(
            newVM = {
                inspector = inspector("rejected")
                inspector.visibility.show(NoticesVM.Key)
                NoticesVM(inspector)
            },
            block = { vm, _ ->
                runCurrent()
                val notice = vm.modelFlow.value.rejections.single()
                assertEquals(2, notice.attempts)
                assertEquals("Demo", notice.name)

                vm.post(NoticesVIE.Intent.DismissRejection(notice.sessionId))
                runCurrent()
                assertTrue(vm.modelFlow.value.rejections.isEmpty())
            },
        )
    }

    @Test
    fun `the selected scope's disconnected session has a banner`() = runVMTest(
        newVM = {
            val inspector = inspector("disconnected")
            inspector.visibility.show(NoticesVM.Key)
            inspector.selection.selectScope(taskListScope)
            NoticesVM(inspector)
        },
        block = { vm, _ ->
            runCurrent()
            assertEquals("Demo", vm.modelFlow.value.disconnected?.name)
            assertEquals(NoticesVIE.Screen.None, vm.modelFlow.value.screen)
        },
    )

    @Test
    fun `the status bar shows the address, the protocol and the connected sessions`() = runVMTest(
        newVM = {
            val inspector = inspector("main")
            inspector.visibility.show(StatusBarVM.Key)
            StatusBarVM(inspector)
        },
        block = { vm, _ ->
            runCurrent()
            val model = vm.modelFlow.value
            assertEquals(ListenerState.Listening(13229), model.listener)
            assertEquals("ws://localhost:13229/timemachine", model.address)
            assertEquals(1, model.connectedSessions)
        },
    )
}
