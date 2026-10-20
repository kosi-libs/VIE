package org.kodein.arch.vie.timemachine.server.fixtures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class SampleWorldTest {

    private fun demoSession(options: WorldOptions) = SampleWorld.state(options).sessions.getValue(Demo.session.id)

    @Test
    fun `the base world is the Demo app, connected, and an earlier session that is gone`() {
        val state = SampleWorld.state()
        assertEquals(2, state.sessions.size)
        assertTrue(state.sessions.getValue(Demo.session.id).isConnected)
        assertFalse(state.sessions.getValue(Demo.oldSession.id).isConnected)

        val viewModels = state.sessions.getValue(Demo.session.id).viewModels
        assertEquals(setOf(Demo.taskList, Demo.settings, Demo.editTask, Demo.editTaskClosed), viewModels.keys)
        assertTrue(viewModels.getValue(Demo.editTaskClosed).closed)
        assertFalse(viewModels.getValue(Demo.taskList).closed)
    }

    @Test
    fun `either session can be left out`() {
        assertEquals(setOf(Demo.session.id), SampleWorld.state(WorldOptions(earlierSession = false)).sessions.keys)
        assertEquals(setOf(Demo.oldSession.id), SampleWorld.state(WorldOptions(demoSession = false)).sessions.keys)
        assertTrue(SampleWorld.state(WorldOptions(demoSession = false, earlierSession = false)).sessions.isEmpty())
    }

    @Test
    fun `the Demo session can be gone`() {
        assertFalse(demoSession(WorldOptions(disconnected = true)).isConnected)
    }

    @Test
    fun `an unknown ViewModel is being identified, until the probe says it never knew it`() {
        val asked = demoSession(WorldOptions(unknownViewModel = true)).viewModels.getValue(Demo.unknown)
        assertNull(asked.info)
        assertFalse(asked.closed)

        val gone = demoSession(WorldOptions(unknownViewModel = true, unknownClosed = true)).viewModels.getValue(Demo.unknown)
        assertTrue(gone.closed)
    }

    @Test
    fun `a ViewModel can be described and silent`() {
        val silent = demoSession(WorldOptions(silentViewModel = true)).viewModels.getValue(Demo.silent)
        assertEquals(0, silent.rowCount)
        assertFalse(silent.closed)
    }
}
