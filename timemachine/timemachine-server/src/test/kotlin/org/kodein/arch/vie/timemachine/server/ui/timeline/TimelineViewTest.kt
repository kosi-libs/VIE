@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.kodein.arch.vie.timemachine.server.fixtures.Demo
import org.kodein.arch.vie.timemachine.server.fixtures.paneTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.fixtures.until
import org.kodein.arch.vie.timemachine.server.ui.core.Inspector
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.viewModel


/**
 * The timeline's view over a live inspector of the main scenario, in a scene 300 px high, which its
 * rows overflow: what only the composition does, the scroll and what follows from it.
 */
class TimelineViewTest {

    private val taskList = InspectorScope.ViewModel(Demo.session.id, Demo.taskList)
    private val session = InspectorScope.Session(Demo.session.id)

    /** What only the header of each says: which timeline is composed. A session's rows name TaskListVM with its identifier. */
    private val taskListTitle = "TaskListVM"
    private val sessionTitle = "Session · 4 ViewModels"

    private fun Inspector.follow(scope: InspectorScope): FollowState = viewModel(timelinePane(scope)).modelFlow.value.controls.follow

    /** The sequence the topmost row in view shows, as `#270`. */
    private fun DesktopComposeUiTest.topRow(): String? {
        val list = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        return onAllNodes(hasText("#", substring = true)).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= list.top && it.boundsInRoot.bottom <= list.bottom }
            .minByOrNull { it.boundsInRoot.top }
            ?.config?.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.let(AnnotatedString::text)
    }

    @Test
    fun `scrolling the timeline shown second pauses that one, not the first`() {
        var scope by mutableStateOf<InspectorScope>(taskList)
        paneTest(height = 300, content = { TimelineView(scope) }) { inspector ->
            until { shown(taskListTitle) && topRow() != null }
            scope = session
            until { shown(sessionTitle) && topRow() != null }

            onNode(hasScrollAction()).performTouchInput { swipeDown() }
            until { inspector.follow(session) is FollowState.Paused || inspector.follow(taskList) is FollowState.Paused }

            assertEquals(FollowState.Following, inspector.follow(taskList), "the scroll paused the timeline that is not shown")
            assertIs<FollowState.Paused>(inspector.follow(session))
        }
    }

    @Test
    fun `a paused timeline is shown again where it was left`() {
        var scope by mutableStateOf<InspectorScope>(session)
        paneTest(height = 300, content = { TimelineView(scope) }) { inspector ->
            until { shown(sessionTitle) && topRow() != null }
            val followed = topRow()
            onNode(hasScrollAction()).performTouchInput { swipeDown() }
            until { inspector.follow(session) is FollowState.Paused }
            waitForIdle()
            val left = topRow()
            assertNotEquals(null, left)
            assertNotEquals(followed, left, "the swipe moved the list")

            scope = taskList
            until { shown(taskListTitle) && topRow() != null }
            scope = session
            until { shown(sessionTitle) && topRow() != null }
            waitForIdle()

            assertEquals(left, topRow())
            assertIs<FollowState.Paused>(inspector.follow(session))
        }
    }
}
