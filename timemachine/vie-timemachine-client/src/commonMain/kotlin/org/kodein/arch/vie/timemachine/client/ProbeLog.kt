package org.kodein.arch.vie.timemachine.client

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive


/**
 * Everything the probe has to say, and the one place it says it: the
 * [TimeMachineConfig.logger] of the configuration the probe was started with.
 *
 * It also holds the probe's failure guards, since a failure the probe survives is printed: a guard
 * cannot be called without the log it prints to. Which guard applies depends on where the code runs,
 * and on whose code it is:
 *
 * - [onAppThread] guards the probe's own code on a thread of the application: a ViewModel's
 *   constructor or dispatcher, the caller of [TimeMachine.start]. Nothing leaves it but cancellation.
 * - [callingApp] guards the application's code when the probe calls it: an object's `toString()`,
 *   its serializers. Nothing leaves it.
 * - [onProbeCoroutine] guards the probe's own code on its own coroutine. An `Exception` does not
 *   leave it; an `Error` does, and retires the probe.
 *
 * Two places catch by hand, being neither of those. `TimeMachineProbe.retire` is the exception
 * handler of the probe's scope, past which nothing may go and where nothing is left to print to.
 * `InspectorConnection.attempt` does not survive a failure but reports it: how a connection ended
 * is its result.
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

    /**
     * Runs [block], the probe's own code on a thread of the application, printing and swallowing
     * whatever it throws but a `CancellationException`: nothing the probe does may throw into the
     * host app, an `Error` included. Running out of memory or failing to link a class is then
     * printed like any other failure, and the probe goes on: it is not retired, as it is by an
     * `Error` on its own coroutine (see [onProbeCoroutine]). An `Error` that comes back for every
     * object is therefore printed for every object.
     *
     * A `CancellationException` leaves: on such a thread it is the cancellation of a coroutine of
     * the application, which is not the probe's to stop.
     */
    inline fun <R> onAppThread(what: () -> String, block: () -> R): R? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            failed(what(), e)
        }

    /**
     * Runs [block], the application's code called by the probe, printing and swallowing anything it
     * throws: an `Error` and a `CancellationException` too, since neither can be the probe's own.
     * That holds wherever it runs: on the probe's coroutine, which must not end because of an object
     * the application produced, or in a ViewModel's constructor.
     *
     * For a failure that is reported otherwise than by printing it, see the top-level [callingApp].
     */
    inline fun <R> callingApp(what: () -> String, block: () -> R): R? =
        callingApp(onFailure = { failed(what(), it) }, block = block)

    /**
     * Runs [block], the probe's own code on its own coroutine, printing and swallowing an
     * `Exception`. An `Error` leaves it, and so the connection loop, and retires the probe: the same
     * `Error` may well come back on every attempt (see [TimeMachineProbe]).
     *
     * A `CancellationException` ends the caller only if the caller itself was cancelled: otherwise
     * it is a failure of [block] like any other.
     */
    suspend inline fun <R> onProbeCoroutine(what: () -> String, block: () -> R): R? =
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            failed(what(), e)
        }

    /** Says that the probe failed to do [what], and returns the `null` a guard then returns. */
    fun failed(what: String, failure: Throwable): Nothing? {
        notice("TimeMachine: failed to $what: $failure")
        return null
    }
}

/**
 * Runs [block], the application's code called by the probe, and returns what [onFailure] makes of
 * anything it throws, an `Error` and a `CancellationException` included. Prints nothing: it is for
 * a failure that is reported in what is returned, where [ProbeLog.callingApp] prints it.
 */
internal inline fun <R> callingApp(onFailure: (Throwable) -> R, block: () -> R): R =
    try {
        block()
    } catch (e: Throwable) {
        onFailure(e)
    }
