package org.kodein.arch.vie.timemachine.server.ui.timeline

import kotlin.test.Test
import kotlin.test.assertEquals


class FollowStateTest {

    @Test
    fun scrollingAwayPausesFollowingWithNothingNew() {
        assertEquals(FollowState.Paused(0), FollowState.Following.scrolledAway())
    }

    @Test
    fun newEntriesOnlyCountWhilePaused() {
        assertEquals(FollowState.Following, FollowState.Following.received(3))
        assertEquals(FollowState.Paused(5), FollowState.Following.scrolledAway().received(2).received(3))
    }

    @Test
    fun scrollingAwayWhilePausedKeepsTheCount() {
        assertEquals(FollowState.Paused(2), FollowState.Paused(2).scrolledAway())
    }

    @Test
    fun resumingZeroesTheCount() {
        assertEquals(FollowState.Following, FollowState.Paused(7).resumed())
    }
}
