package org.kodein.arch.vie.timemachine.server.ui.timeline


/** Whether the timeline scrolls to new entries. */
sealed interface FollowState {

    /** New entries scroll into view. */
    data object Following : FollowState

    /** The developer scrolled up: [newCount] entries arrived since. */
    data class Paused(val newCount: Int = 0) : FollowState

    /** The state once the developer scrolled away from the end. */
    fun scrolledAway(): FollowState = if (this is Following) Paused() else this

    /** The state once [count] new entries arrived. */
    fun received(count: Int): FollowState = if (this is Paused) Paused(newCount + count) else this

    /** The state once the developer resumed: following, with the count zeroed. */
    fun resumed(): FollowState = Following
}
