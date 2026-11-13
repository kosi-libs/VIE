package org.kodein.arch.vie.timemachine.client

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive


/**
 * Everything the probe has to say, and the one place it says it: the
 * [TimeMachineConfig.logger] of the configuration the probe was started with.
 *
 * It also holds the probe's failure guards, since a failure the probe survives is printed: a guard
 * cannot be called without the log it prints to.
 *
 * @param logger Receives each message. It is the application's code: see [notice].
 */
internal class ProbeLog(private val logger: (String) -> Unit) {

    /**
     * Hands [text] to the logger. Never throws: a logger that fails, an `Error` or a
     * `CancellationException` included, is the application's code failing, wherever the probe has
     * something to say. [text] is then printed with `println`, along with that failure.
     */
    fun notice(text: String) {
        try {
            logger(text)
        } catch (e: Throwable) {
            try {
                println("$text (the TimeMachine logger failed: $e)")
            } catch (_: Throwable) {
                // Nowhere left to say it.
            }
        }
    }

    /** Runs [block], printing and swallowing any failure but cancellation: nothing the probe does may
     *  throw into the host app. For a [block] that suspends, use [guardedSuspending]. */
    inline fun <R> guarded(what: () -> String, block: () -> R): R? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice("TimeMachine: failed to ${what()}: $e")
            null
        }

    /** Like [guarded] for a [block] that suspends: a `CancellationException` ends the caller only if the
     *  caller itself was cancelled, otherwise it is a failure of [block] like any other. */
    suspend inline fun <R> guardedSuspending(what: () -> String, block: () -> R): R? =
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            notice("TimeMachine: failed to ${what()}: $e")
            null
        }

    /** Runs [block], app code describing an object or one of its types, printing and swallowing anything
     *  it throws: unlike [guarded], errors and cancellation too, since they cannot come from the probe
     *  itself. */
    inline fun <R> describing(what: () -> String, block: () -> R): R? =
        try {
            block()
        } catch (e: Throwable) {
            notice("TimeMachine: failed to ${what()}: $e")
            null
        }
}
