@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.sessions

import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.test.assertFalse
import org.kodein.arch.vie.timemachine.server.fixtures.paneTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.until


/** The sessions pane's view over a live inspector: what its rows print. */
class SessionsViewTest {

    @Test
    fun `a ViewModel nobody described is named by its four digits`() =
        paneTest(id = "unknownVM", content = { SessionsView() }) {
            until { shown("TaskListVM") }
            until { shown("d4c8") }

            assertFalse(shown("d4c8…0006"), "the session's form of an identifier")
        }
}
