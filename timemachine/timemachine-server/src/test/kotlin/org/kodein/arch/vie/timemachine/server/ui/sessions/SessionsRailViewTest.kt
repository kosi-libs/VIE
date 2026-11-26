@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.test.pendingIntents
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.Rendered
import org.kodein.arch.vie.timemachine.server.fixtures.ScenarioData
import org.kodein.arch.vie.timemachine.server.fixtures.assertActivatedByEnterAndSpace
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.press
import org.kodein.arch.vie.timemachine.server.fixtures.state
import org.kodein.arch.vie.timemachine.server.fixtures.tabTo
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope


/** The compact layout's rail on a model, driven by the keyboard: what [RailTest] reads from its items, as it is composed. */
class SessionsRailViewTest {

    private fun rail(): MockVIE<SessionsVIE.Model, SessionsVIE.Intent, NoSignal> {
        val data = ScenarioData.of("main")
        return MockVIE(sessionsModel(data.state(), selected = null, now = data.now))
    }

    @Test
    fun `Tab reaches the menu button, and Enter and Space open the drawer`() {
        val vie = rail()
        var opened = 0
        contentTest(content = { vie.Rendered { model -> SessionsRail(model, onMenu = { opened++ }) { intent -> post(intent) } } }) {
            tabTo(onNodeWithContentDescription(RailMenuDescription)).assertIsFocused()
            assertActivatedByEnterAndSpace { opened }
        }
    }

    @Test
    fun `Tab reaches a ViewModel's tile, and Enter selects its scope`() {
        val vie = rail()
        contentTest(content = { vie.Rendered { model -> SessionsRail(model, onMenu = {}) { intent -> post(intent) } } }) {
            tabTo(onNodeWithContentDescription("SettingsVM c3d7")).assertIsFocused()
            press(Key.Enter)
            assertEquals(
                listOf<SessionsVIE.Intent>(SessionsVIE.Intent.SelectScope(InspectorScope.ViewModel(Demo.session.id, Demo.settings))),
                vie.pendingIntents(),
            )
        }
    }

    @Test
    fun `Tab reaches a session's tile, and Space selects its whole timeline`() {
        val vie = rail()
        contentTest(content = { vie.Rendered { model -> SessionsRail(model, onMenu = {}) { intent -> post(intent) } } }) {
            tabTo(onNodeWithContentDescription("Demo 3f2a…c91e, connected")).assertIsFocused()
            press(Key.Spacebar)
            assertEquals(
                listOf<SessionsVIE.Intent>(SessionsVIE.Intent.SelectScope(InspectorScope.Session(Demo.session.id))),
                vie.pendingIntents(),
            )
        }
    }
}
