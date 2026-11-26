package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.runtime.Immutable

/**
 * The opacities the views draw something at, one per role, as [InspectorDimens] has the sizes. The design's token
 * table has no opacities: these are the values the views were written with, and they are here so that they are
 * decided, or changed, in one place with the design, and not found again in each view. Each is the share of the
 * thing that shows: 1 is opaque.
 */
@Immutable
object InspectorAlpha {
    /** An option of a segmented control that cannot be chosen, and a rail tile for what is gone: dimmed, still readable. */
    val unavailable: Float = 0.55f

    /** The block of a session that is disconnected, in the sessions pane. */
    val disconnectedSession: Float = 0.62f

    /** The row of a closed ViewModel in a session that is connected. */
    val closedViewModel: Float = 0.6f

    /** The sparkline of a ViewModel or a session that is gone, and of one that is alive: a little under opaque, so that it does not shout. */
    val deadSparkline: Float = 0.5f
    val liveSparkline: Float = 0.9f

    /** The composer's controls while a block says it cannot send. */
    val blockedComposer: Float = 0.45f

    /** The count of a toggle chip: it stands back from the label. */
    val chipCount: Float = 0.8f

    /** How much of what is under a modal sheet shows through its scrim. */
    val scrim: Float = 0.32f
}
