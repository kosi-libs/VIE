package org.kodein.arch.vie.timemachine.client.connection

import kotlin.time.Duration
import kotlin.time.TimeMark


/**
 * How one connection to the inspector ended.
 *
 * [held] tells whether it lasted long enough to end the [Outage]: only a [Dropped] one can.
 */
internal sealed interface Attempt {

    val held: Boolean get() = false

    /** Never welcomed, for [cause]: nothing listens there, or the connection broke before the
     *  inspector answered. */
    class Unreachable(val cause: Exception) : Attempt

    /** Reached, but not welcomed: rejected, not answered in time, or closed during the handshake.
     *  [reason] is the problem, as it is printed. */
    class Refused(val reason: String) : Attempt

    /** Welcomed, then ended. [cause] is `null` when the inspector closed the connection. */
    class Dropped(override val held: Boolean, val cause: Exception?) : Attempt
}

/**
 * What the connection loop remembers since the last connection that held — or, before the first
 * one, since the probe started. A connection that holds ends the outage: the loop starts a new one,
 * and so forgets all of this at once.
 *
 * Each failed [Attempt] is retried after a capped exponential backoff. A welcome alone does not
 * start it over: an inspector that fails on what the probe sends first drops every connection right
 * after welcoming it, since the probe sends that first again each time. Such an inspector is
 * retried ever less often.
 *
 * A problem is printed once, not on every retry, for as long as it repeats.
 *
 * Once the outage has lasted [quietAfter], the backoff is capped at [quietRetryDelay] instead: most
 * debug sessions run with no inspector open, and an unattended app should not attempt a connection
 * every few seconds for the life of its process. This is printed once.
 *
 * @param since When the outage started.
 */
internal class Outage(
    private val since: TimeMark,
    private val address: String,
    private val firstRetryDelay: Duration,
    private val maxRetryDelay: Duration,
    private val quietAfter: Duration,
    private val quietRetryDelay: Duration,
    private val notice: (String) -> Unit,
) {

    private var failures = 0
    private var lastProblem: String? = null
    private var quiet = false

    /** Prints what is news about [end], and returns how long to wait before the next attempt. */
    fun failed(end: Attempt): Duration {
        val problem = problem(end)
        if (problem != lastProblem) {
            notice("TimeMachine: $problem; retrying.")
            lastProblem = problem
        }
        if (!quiet && since.elapsedNow() >= quietAfter) {
            quiet = true
            notice("TimeMachine: no connection to the inspector at $address has held for $quietAfter; retrying every $quietRetryDelay from now on.")
        }
        return retryDelay(failures++, cap = if (quiet) quietRetryDelay else maxRetryDelay)
    }

    private fun problem(end: Attempt): String = when (end) {
        is Attempt.Unreachable -> "cannot reach the inspector at $address (${end.cause})"
        is Attempt.Refused -> end.reason
        is Attempt.Dropped ->
            if (end.cause == null) "the inspector at $address closed the connection"
            else "lost the connection to the inspector at $address (${end.cause})"
    }

    private fun retryDelay(failures: Int, cap: Duration): Duration =
        if (failures >= maxDoublings) cap
        else (firstRetryDelay * (1 shl failures).toDouble()).coerceAtMost(cap)
}

/** Past it, `1 shl failures` overflows an `Int`: not a setting, and any cap is reached long before. */
private const val maxDoublings = 30
