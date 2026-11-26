package org.kodein.arch.vie.timemachine.server.ui.timeline

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence


/** Whether the timeline scrolls to new entries. */
sealed interface FollowState {

    /** New entries scroll into view. */
    data object Following : FollowState

    /**
     * The developer scrolled up when the timeline ended at [after], `null` when it had no row: whatever
     * comes after it is new. It is where the pause began, not a count, so that what is new can be
     * counted again whenever the filters change.
     */
    data class Paused(val after: TimeMachineSequence?) : FollowState

    /** The state once the developer scrolled away from the end of a timeline whose last row is [last]. */
    fun scrolledAway(last: TimeMachineSequence?): FollowState = if (this is Following) Paused(last) else this

    /** The state once the developer resumed: following. */
    fun resumed(): FollowState = Following
}
