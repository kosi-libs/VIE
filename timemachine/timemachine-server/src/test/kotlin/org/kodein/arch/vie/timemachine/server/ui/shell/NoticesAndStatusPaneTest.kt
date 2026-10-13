@file:OptIn(ExperimentalCoroutinesApi::class)

package org.kodein.arch.vie.timemachine.server.ui.shell

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.kodein.arch.vie.test.runVMTest
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.FixedClock
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.inspector
import org.kodein.arch.vie.timemachine.server.fixtures.newInspector
import org.kodein.arch.vie.timemachine.server.fixtures.taskListScope
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


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
            block = {
                runCurrent()
                assertEquals(NoticesVIE.Screen.CannotListen("Address already in use"), model.screen)
                post(NoticesVIE.Intent.Retry)
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
        block = {
            runCurrent()
            assertEquals(NoticesVIE.Screen.Empty, model.screen)
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
            block = {
                runCurrent()
                val notice = model.rejections.single()
                assertEquals(2, notice.attempts)
                assertEquals("Demo", notice.name)

                post(NoticesVIE.Intent.DismissRejection(notice.sessionId))
                runCurrent()
                assertTrue(model.rejections.isEmpty())
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
        block = {
            runCurrent()
            assertEquals("Demo", model.disconnected?.name)
            assertEquals(NoticesVIE.Screen.None, model.screen)
        },
    )

    @Test
    fun `the status bar shows the address, the protocol and the connected sessions`() = runVMTest(
        newVM = {
            val inspector = inspector("main")
            inspector.visibility.show(StatusBarVM.Key)
            StatusBarVM(inspector)
        },
        block = {
            runCurrent()
            val model = model
            assertEquals(ListenerState.Listening(13229), model.listener)
            assertEquals("ws://localhost:13229/timemachine", model.address)
            assertEquals(1, model.connectedSessions)
        },
    )
}
