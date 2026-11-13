@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.test.pendingIntents
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.Rendered
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.state
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope


/** The sessions pane's content on a model: what its rows print. */
class SessionsViewTest {

    private fun sessions(id: String): MockVIE<SessionsVIE.Model, SessionsVIE.Intent, NoSignal> {
        val data = ScenarioData.of(id)
        return MockVIE(sessionsModel(data.state(), selected = null, now = data.now))
    }

    @Test
    fun `a folded session says how many ViewModels it hides, and shows none of them nor its clear action`() {
        val vie = sessions("main")
        vie.modelFlow.value = vie.model.copy(controls = SessionsVIE.Controls(collapsed = setOf(Demo.session.id)))
        contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
            assertEquals(1, onAllNodes(hasText("· 4 ViewModels", substring = true)).fetchSemanticsNodes().size)
            assertFalse(shown("SettingsVM"), "a ViewModel of the folded session")
            assertFalse(shown("Clear 1 closed ViewModel"), "the folded session's clear action")
            // The other session is as it was.
            assertTrue(shown("TaskListVM"))
            assertTrue(shown("Clear session"))
            assertEquals(1, onAllNodesWithContentDescription(ExpandSessionDescription).fetchSemanticsNodes().size)
            assertEquals(1, onAllNodesWithContentDescription(CollapseSessionDescription).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `a session's chevron folds it, and the rest of its row still selects it`() {
        val vie = sessions("main")
        val live = vie.model.sessions.indexOfFirst { it.id == Demo.session.id }
        contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
            assertTrue(shown("SettingsVM"))
            assertFalse(onAllNodes(hasText("· 4 ViewModels", substring = true)).fetchSemanticsNodes().isNotEmpty(), "an unfolded session hides nothing to count")

            onAllNodesWithContentDescription(CollapseSessionDescription)[live].performClick()
            assertEquals(listOf<SessionsVIE.Intent>(SessionsVIE.Intent.ToggleSession(Demo.session.id)), vie.pendingIntents())

            onNodeWithText(vie.model.sessions[live].shortId).performClick()
            assertEquals(listOf<SessionsVIE.Intent>(SessionsVIE.Intent.SelectScope(InspectorScope.Session(Demo.session.id))), vie.pendingIntents())
        }
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

    @Test
    fun `a ViewModel nobody described yet says its session is being asked for its type`() {
        val vie = sessions("unknownVM")
        contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
            assertTrue(shown("Unknown · asking Demo for its type…"))
        }
    }
}
