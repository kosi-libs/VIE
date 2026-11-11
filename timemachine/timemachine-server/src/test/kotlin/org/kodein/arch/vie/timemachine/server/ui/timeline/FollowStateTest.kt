package org.kodein.arch.vie.timemachine.server.ui.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence


class FollowStateTest {

    private val last = TimeMachineSequence(270)

    @Test
    fun scrollingAwayPausesFollowingAtTheLastRow() {
        assertEquals(FollowState.Paused(last), FollowState.Following.scrolledAway(last))
    }

    @Test
    fun scrollingAwayFromATimelineWithNoRowPausesBeforeAnyRow() {
        assertEquals(FollowState.Paused(null), FollowState.Following.scrolledAway(null))
    }

    @Test
    fun scrollingAwayWhilePausedKeepsWhereThePauseBegan() {
        assertEquals(FollowState.Paused(last), FollowState.Paused(last).scrolledAway(TimeMachineSequence(300)))
    }

    @Test
    fun resumingFollowsAgain() {
        assertEquals(FollowState.Following, FollowState.Paused(last).resumed())
    }
}
