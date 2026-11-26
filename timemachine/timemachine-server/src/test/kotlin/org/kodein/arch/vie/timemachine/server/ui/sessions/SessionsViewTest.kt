@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
import org.kodein.arch.vie.timemachine.server.fixtures.press
import org.kodein.arch.vie.timemachine.server.fixtures.levelsAbove
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.state
import org.kodein.arch.vie.timemachine.server.fixtures.tabTo
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.theme.LightInspectorScheme
import kotlin.math.abs
import kotlin.uuid.Uuid


/** The sessions pane's content on a model: what its rows print. */
class SessionsViewTest {

    private fun sessions(id: String): MockVIE<SessionsVIE.Model, SessionsVIE.Intent, NoSignal> {
        val data = ScenarioData.of(id)
        return MockVIE(sessionsModel(data.state(), selected = null, now = data.now))
    }

    private fun sessions(id: String, selected: InspectorScope): MockVIE<SessionsVIE.Model, SessionsVIE.Intent, NoSignal> {
        val data = ScenarioData.of(id)
        return MockVIE(sessionsModel(data.state(), selected = selected, now = data.now))
    }

    /** Whether every row that names [name] says it is selected, as [selected]: a ViewModel's name is in more than one session. */
    private fun DesktopComposeUiTest.assertRowsSelected(name: String, selected: Boolean) {
        val rows = onAllNodes(hasText(name) and hasClickAction()).fetchSemanticsNodes()
        assertTrue(rows.isNotEmpty(), "a row of $name")
        rows.forEach { assertEquals(selected, it.config[SemanticsProperties.Selected], "the row of $name, selected") }
    }

    @Test
    fun `the selected ViewModel's row says it is selected to accessibility, and the other rows do not`() {
        val vie = sessions("main", InspectorScope.ViewModel(Demo.session.id, Demo.settings))
        contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
            onNode(hasText("SettingsVM") and hasClickAction()).assertIsSelected()
            assertRowsSelected("TaskListVM", selected = false)
            onNodeWithText(vie.model.sessions.first { it.id == Demo.session.id }.shortId).assertIsNotSelected()
        }
    }

    @Test
    fun `the selected session's row says it is selected to accessibility, and its ViewModels' rows do not`() {
        val vie = sessions("main", InspectorScope.Session(Demo.session.id))
        contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
            onNodeWithText(vie.model.sessions.first { it.id == Demo.session.id }.shortId).assertIsSelected()
            onNode(hasText("SettingsVM") and hasClickAction()).assertIsNotSelected()
            assertRowsSelected("TaskListVM", selected = false)
        }
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

    // What the pane's buttons post, and what its rows say.

    /** [model] as the pane shows it, with the session [id] given the changes of [change]. */
    private fun SessionsVIE.Model.withSession(id: Uuid, change: (SessionItem) -> SessionItem) =
        copy(sessions = sessions.map { if (it.id == id) change(it) else it })

    @Test
    fun `Clear disconnected posts ClearDisconnected, and is greyed when no session is disconnected`() {
        val vie = sessions("main")
        contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
            onNodeWithText("Clear disconnected").assertIsEnabled().performClick()
            assertEquals(listOf<SessionsVIE.Intent>(SessionsVIE.Intent.ClearDisconnected), vie.pendingIntents())

            vie.modelFlow.value = vie.model.copy(disconnectedSessions = 0)
            waitForIdle()
            onNodeWithText("Clear disconnected").assertIsNotEnabled().performClick()
            assertEquals(emptyList<SessionsVIE.Intent>(), vie.pendingIntents(), "a greyed button was clicked")
        }
    }

    @Test
    fun `a session's clear action posts ClearSession for that session, and not for the other`() {
        val vie = sessions("main")
        contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
            onNodeWithText("Clear 1 closed ViewModel").assertIsEnabled().performClick()
            assertEquals(listOf<SessionsVIE.Intent>(SessionsVIE.Intent.ClearSession(Demo.session.id)), vie.pendingIntents())

            onNodeWithText("Clear session").assertIsEnabled().performClick()
            assertEquals(listOf<SessionsVIE.Intent>(SessionsVIE.Intent.ClearSession(Demo.oldSession.id)), vie.pendingIntents())
        }
    }

    @Test
    fun `a session's clear action says what the session says, and takes no click when it clears nothing`() {
        val vie = sessions("main")
        vie.modelFlow.value = vie.model.withSession(Demo.session.id) { it.copy(clearLabel = "Clear 3 closed ViewModels") }
        contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
            assertTrue(shown("Clear 3 closed ViewModels"))
            assertFalse(shown("Clear 1 closed ViewModel"))

            vie.modelFlow.value = vie.model.withSession(Demo.session.id) { it.copy(clearLabel = "Clear closed ViewModels", clearEnabled = false) }
            waitForIdle()
            onNodeWithText("Clear closed ViewModels").assertIsNotEnabled().performClick()
            assertEquals(emptyList<SessionsVIE.Intent>(), vie.pendingIntents(), "a greyed button was clicked")
            // The other session's is its own.
            onNodeWithText("Clear session").assertIsEnabled()
        }
    }

    @Test
    fun `a click on a ViewModel's row picks its scope and says so, and the chevron does not`() {
        val vie = sessions("main")
        var picked = 0
        val live = vie.model.sessions.indexOfFirst { it.id == Demo.session.id }
        contentTest(content = { vie.Rendered { SessionsContent(it, onScopePicked = { picked++ }) } }) {
            onNode(hasText("SettingsVM") and hasClickAction()).performClick()
            assertEquals(
                listOf<SessionsVIE.Intent>(SessionsVIE.Intent.SelectScope(InspectorScope.ViewModel(Demo.session.id, Demo.settings))),
                vie.pendingIntents(),
            )
            assertEquals(1, picked)

            onAllNodesWithContentDescription(CollapseSessionDescription)[live].performClick()
            assertEquals(listOf<SessionsVIE.Intent>(SessionsVIE.Intent.ToggleSession(Demo.session.id)), vie.pendingIntents())
            assertEquals(1, picked, "a fold picked a scope")

            onNodeWithText(vie.model.sessions[live].shortId).performClick()
            assertEquals(listOf<SessionsVIE.Intent>(SessionsVIE.Intent.SelectScope(InspectorScope.Session(Demo.session.id))), vie.pendingIntents())
            assertEquals(2, picked)
        }
    }

    @Test
    fun `only the selected session says it selects all its ViewModels`() {
        fun DesktopComposeUiTest.mentions() = onAllNodesWithText("all ViewModels").fetchSemanticsNodes().size

        for ((selected, mentions) in listOf(
            null to 0,
            InspectorScope.ViewModel(Demo.session.id, Demo.settings) to 0,
            InspectorScope.Session(Demo.session.id) to 1,
            InspectorScope.Session(Demo.oldSession.id) to 1,
        )) {
            val vie = if (selected == null) sessions("main") else sessions("main", selected)
            contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
                assertEquals(mentions, mentions(), "\"all ViewModels\" with $selected selected")
            }
        }
    }

    /** A node whose text starts with [prefix]: a ViewModel's row, for the status it prints under its name. */
    private fun textStartingWith(prefix: String) = SemanticsMatcher("has a text starting with \"$prefix\"") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith(prefix) } == true
    }

    @Test
    fun `a closed ViewModel's row is dimmed to six tenths of its colours, and a live one is not`() {
        /** The pixel of the selected row's end margin, where it is only its background, over the pane's surface. */
        fun rowBackground(selected: InspectorScope, row: SemanticsMatcher): Color {
            var pixel = Color.Unspecified
            val vie = sessions("main", selected)
            contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
                val image = onNode(hasClickAction() and row).captureToImage().toPixelMap()
                pixel = image[image.width - 3, image.height / 2]
            }
            return pixel
        }

        val surface = LightInspectorScheme.surfaceContainerLow
        val selection = LightInspectorScheme.primaryContainer

        val live = rowBackground(InspectorScope.ViewModel(Demo.session.id, Demo.settings), hasText("SettingsVM"))
        assertEquals(emptyList(), levelsAbove(selection, live).filter { abs(it) > 1 }, "a live row is not dimmed: $live, not $selection")

        val closed = rowBackground(InspectorScope.ViewModel(Demo.session.id, Demo.editTaskClosed), textStartingWith("closed "))
        val dimmed = lerp(surface, selection, 0.6f)
        assertEquals(emptyList(), levelsAbove(dimmed, closed).filter { abs(it) > 2 }, "a closed row is at six tenths: $closed, not $dimmed")
    }

    // The keyboard: the controls are reached with Tab, and done with Enter.

    @Test
    fun `Tab reaches Clear disconnected, a session's chevron and its clear action, and Enter does what a click does`() {
        val vie = sessions("main")
        val live = vie.model.sessions.indexOfFirst { it.id == Demo.session.id }
        contentTest(content = { vie.Rendered { SessionsContent(it) } }) {
            tabTo(onNodeWithText("Clear disconnected")).assertIsFocused()
            press(Key.Enter)
            assertEquals(listOf<SessionsVIE.Intent>(SessionsVIE.Intent.ClearDisconnected), vie.pendingIntents())

            tabTo(onAllNodesWithContentDescription(CollapseSessionDescription)[live]).assertIsFocused()
            press(Key.Enter)
            assertEquals(listOf<SessionsVIE.Intent>(SessionsVIE.Intent.ToggleSession(Demo.session.id)), vie.pendingIntents(), "Enter on the chevron")

            tabTo(onNodeWithText("Clear 1 closed ViewModel")).assertIsFocused()
            press(Key.Enter)
            assertEquals(listOf<SessionsVIE.Intent>(SessionsVIE.Intent.ClearSession(Demo.session.id)), vie.pendingIntents())
        }
    }

    @Test
    fun `Tab reaches a session's row and a ViewModel's row, and Enter or Space selects it and picks its scope`() {
        val vie = sessions("main")
        var picked = 0
        contentTest(content = { vie.Rendered { SessionsContent(it, onScopePicked = { picked++ }) } }) {
            val settings = onNode(hasText("SettingsVM") and hasClickAction())
            tabTo(settings).assertIsFocused()
            press(Key.Enter)
            assertEquals(
                listOf<SessionsVIE.Intent>(SessionsVIE.Intent.SelectScope(InspectorScope.ViewModel(Demo.session.id, Demo.settings))),
                vie.pendingIntents(),
            )
            assertEquals(1, picked)

            press(Key.Spacebar)
            assertEquals(2, picked, "Space does what Enter does")
            assertEquals(1, vie.pendingIntents().size)
        }
    }
}
