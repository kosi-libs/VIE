@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.test.assertEquals
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.Rendered
import org.kodein.arch.vie.timemachine.server.fixtures.SampleWorld
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.countingRecompositions


/** What the sessions pane recomposes when the store changes one session: the block of that session. */
class SessionsRecompositionTest {

    @Test
    fun `an observation on one ViewModel recomposes the block of its session, and no other`() {
        val data = ScenarioData.of("main")
        // The projection builds every item again: equal to the one before where nothing changed.
        val before = sessionsModel(SampleWorld.state(data.events, data.maxRows), selected = null, now = data.now)
        val observed = Demo.Builder(Demo.connection).apply {
            observed(Demo.taskList, Demo.NextSequence, Demo.at(29_000), VIEObjectKind.Intent, Demo.DeleteTask, Demo.deleteTask(Demo.Brief))
        }.events
        val after = sessionsModel(SampleWorld.state(data.events + observed, data.maxRows), selected = null, now = data.now)
        assertEquals(before.sessions.map { it.id }, after.sessions.map { it.id })
        assertEquals(1, before.sessions.zip(after.sessions).count { (old, new) -> old != new }, "the observation changes one session")

        val vie = MockVIE<SessionsVIE.Model, SessionsVIE.Intent, NoSignal>(before)
        countingRecompositions(SessionBlock) { recompositions ->
            contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
                assertEquals(before.sessions.size, recompositions[SessionBlock], "each session's block, composed once")
                recompositions.reset()

                vie.modelFlow.value = after
                waitForIdle()

                assertEquals(1, recompositions[SessionBlock], "the block of the session the observation is in")
            }
        }
    }

    private companion object {
        /** As the compiler names it for the tracer. */
        const val SessionBlock = "org.kodein.arch.vie.timemachine.server.ui.sessions.SessionBlock"
    }
}
