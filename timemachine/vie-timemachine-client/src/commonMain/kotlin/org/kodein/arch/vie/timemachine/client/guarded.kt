package org.kodein.arch.vie.timemachine.client

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive


/** Runs [block], printing and swallowing any failure but cancellation: nothing the probe does may
 *  throw into the host app. For a [block] that suspends, use [guardedSuspending]. */
internal inline fun <R> guarded(what: () -> String, block: () -> R): R? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("TimeMachine: failed to ${what()}: $e")
        null
    }

/** Like [guarded] for a [block] that suspends: a `CancellationException` ends the caller only if the
 *  caller itself was cancelled, otherwise it is a failure of [block] like any other. */
internal suspend inline fun <R> guardedSuspending(what: () -> String, block: () -> R): R? =
    try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) currentCoroutineContext().ensureActive()
        println("TimeMachine: failed to ${what()}: $e")
        null
    }

/** Runs [block], app code describing an object or one of its types, printing and swallowing anything
 *  it throws: unlike [guarded], errors and cancellation too, since they cannot come from the probe
 *  itself. */
internal inline fun <R> describing(what: () -> String, block: () -> R): R? =
    try {
        block()
    } catch (e: Throwable) {
        println("TimeMachine: failed to ${what()}: $e")
        null
    }
