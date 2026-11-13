package org.kodein.arch.vie.timemachine.server.ui.core

import org.kodein.arch.vie.timemachine.server.store.SessionRecord
import org.kodein.arch.vie.timemachine.server.store.ViewModelRecord
import org.kodein.arch.vie.timemachine.server.ui.common.clock
import kotlin.time.Instant

/**
 * What a ViewModel is, as every pane tells it: its own state and, while it is not known to be closed,
 * its session's connection.
 *
 * @property gone Whether nothing more will come of it: it closed, or its session is disconnected. What a
 *   gone ViewModel shows is dimmed.
 */
internal enum class ViewModelStatus(val gone: Boolean) {

    /** Described, not closed, and its session is connected. */
    Alive(gone = false),

    /** Unknown, and not known to be closed yet, in a connected session: the inspector asked the probe. */
    Identifying(gone = false),

    /** Not known to be closed, but its session is disconnected. */
    SessionDisconnected(gone = true),

    /** Known not to be alive, whatever its session. */
    Closed(gone = true);

    /** This status in a few words, as the panes print it; [closedAt] is when a [Closed] one closed, if known. */
    fun label(closedAt: Instant?): String = when (this) {
        Alive -> "alive"
        Identifying -> "identifying…"
        SessionDisconnected -> "session disconnected"
        Closed -> closedAt?.let { "closed ${clock(it)}" } ?: "closed · time unknown"
    }
}

/** The status of [vm] in [session]: its close first, then its session's connection, then whether anything described it. */
internal fun viewModelStatus(session: SessionRecord, vm: ViewModelRecord): ViewModelStatus = when {
    vm.closed -> ViewModelStatus.Closed
    !session.isConnected -> ViewModelStatus.SessionDisconnected
    vm.isUnknown -> ViewModelStatus.Identifying
    else -> ViewModelStatus.Alive
}
