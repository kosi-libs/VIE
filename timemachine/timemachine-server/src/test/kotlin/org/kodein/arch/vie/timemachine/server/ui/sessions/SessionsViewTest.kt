@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.timemachine.server.fixtures.Rendered
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.state


/** The sessions pane's content on a model: what its rows print. */
class SessionsViewTest {

    private fun sessions(id: String): MockVIE<SessionsVIE.Model, SessionsVIE.Intent, NoSignal> {
        val data = ScenarioData.of(id)
        return MockVIE(sessionsModel(data.state(), selected = null, now = data.now))
    }

    @Test
    fun `a ViewModel nobody described is named by its four digits`() {
        val vie = sessions("unknownVM")
        contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
            assertTrue(shown("TaskListVM"))
            assertTrue(shown("d4c8"))
            assertFalse(shown("d4c8…0006"), "the session's form of an identifier")
        }
    }
}
