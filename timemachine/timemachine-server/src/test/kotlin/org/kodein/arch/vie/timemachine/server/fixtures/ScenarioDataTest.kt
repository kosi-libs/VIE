package org.kodein.arch.vie.timemachine.server.fixtures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.server.store.InterventionStatus
import org.kodein.arch.vie.timemachine.server.store.TimelineEntry


class ScenarioDataTest {

    private fun seq(n: Long) = TimeMachineSequence(n)

    @Test
    fun `the catalogue holds the design's 27 scenarios, each with data`() {
        assertEquals(27, ScenarioData.ids.size)
        assertEquals(ScenarioData.ids.size, ScenarioData.ids.toSet().size)
        ScenarioData.ids.forEach { assertEquals(it, ScenarioData.of(it).id) }
    }

    @Test
    fun `every scenario goes through a real store without error`() {
        for (id in ScenarioData.ids) runTest {
            val data = ScenarioData.of(id)
            // `on` throws on a bug in the core; the store would only report it and skip the event.
            val expected = data.state()
            val actual = inspector(id, data).store.state.value
            assertEquals(expected.sessions.keys, actual.sessions.keys, id)
            assertEquals(expected.listener, actual.listener, id)
            assertEquals(expected.rejections.keys, actual.rejections.keys, id)
        }
    }

    @Test
    fun `the base data has the four markers, the design's sequences and an old session`() {
        val state = ScenarioData.of("main").state()
        val session = state.sessions.getValue(Demo.session.id)
        val taskList = session.viewModels.getValue(Demo.taskList)

        assertTrue(taskList.historyUnavailable(session.sequences), "start never seen")
        assertTrue(taskList.evictedCount > 0, "evicted")
        val timeline = state.timeline(Demo.session.id, Demo.taskList)
        assertTrue(timeline.any { it is TimelineEntry.PossiblyIncomplete }, "possibly incomplete")
        assertTrue(timeline.any { (it as? TimelineEntry.Message)?.row?.message?.toString()?.contains("ObservationsDropped") == true }, "gap")
        for (n in listOf(207L, 208L, 251L, 266L, 270L)) assertTrue(taskList.holds(seq(n)), "#$n retained")
        assertEquals(seq(270), taskList.currentModel?.sequence)

        assertTrue(session.viewModels.getValue(Demo.editTaskClosed).closed)
        assertTrue(!state.sessions.values.first { it.id != Demo.session.id }.isConnected)
    }

    @Test
    fun `the Demo has four interventions, the first one's row evicted and the last refused`() {
        val taskList = ScenarioData.of("main").state().sessions.getValue(Demo.session.id).viewModels.getValue(Demo.taskList)
        val statuses = taskList.interventions.values.map { it.status }

        assertEquals(4, statuses.size)
        val first = assertIs<InterventionStatus.Succeeded>(statuses[0])
        assertTrue(!taskList.holds(first.sequence))
        assertIs<InterventionStatus.Succeeded>(statuses[1])
        assertIs<InterventionStatus.Succeeded>(statuses[2])
        assertIs<InterventionStatus.Failed>(statuses[3])
    }

    @Test
    fun `scenario data is the same every time, sparklines included`() {
        val once = ScenarioData.of("main").state().sessions.mapValues { it.value.sparkline(Demo.now) }
        val again = ScenarioData.of("main").state().sessions.mapValues { it.value.sparkline(Demo.now) }

        assertEquals(once, again)
        assertTrue(once.getValue(Demo.session.id).sum() > 0)
    }

    @Test
    fun `the unknown ViewModel is asked for, and the probe answers as the scenario says`() = runTest {
        val identifying = inspector("unknownVM").store.state.value.sessions.getValue(Demo.session.id).viewModels.getValue(Demo.unknown)
        val closed = inspector("unknownClosed").store.state.value.sessions.getValue(Demo.session.id).viewModels.getValue(Demo.unknown)

        assertTrue(identifying.isUnknown && !identifying.closed)
        assertTrue(closed.isUnknown && closed.closed)
    }

    @Test
    fun `the rejected scenario holds one notice for two attempts`() {
        val rejections = ScenarioData.of("rejected").state().rejections.values.toList()
        assertEquals(1, rejections.size)
        assertEquals(2, rejections.single().attempts)
    }
}
