package org.kodein.arch.vie.timemachine.client

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest


class ProbeLogTest {

    private val said = mutableListOf<String>()
    private val log = ProbeLog { said += it }

    private fun assertSaidOnce(what: String, failure: Throwable) {
        assertTrue(said.single().startsWith("TimeMachine: failed to $what: ") && failure.message!! in said.single(), said.toString())
        said.clear()
    }

    @Test
    fun aNoticeIsHandedToTheLoggerAsItIs() {
        log.notice("TimeMachine: first")
        log.notice("second\nline")
        assertEquals(listOf("TimeMachine: first", "second\nline"), said)
    }

    @Test
    fun aLoggerThatThrowsNeverThrowsIntoTheProbe() {
        val failures = listOf(IllegalStateException("exception"), Error("error"), CancellationException("cancellation"))
        for (failure in failures) {
            // Returning is the assertion: the message is then printed with println, which a test cannot read.
            ProbeLog { throw failure }.notice("TimeMachine: said anyway")
        }
    }

    @Test
    fun aGuardThatDoesNotFailReturnsItsResultAndSaysNothing() = runTest {
        assertEquals(1, log.onAppThread({ "do this" }) { 1 })
        assertEquals(2, log.callingApp({ "call that" }) { 2 })
        assertEquals(3, log.onProbeCoroutine({ "do it" }) { 3 })
        assertEquals(4, callingApp(onFailure = { 0 }) { 4 })
        assertEquals(emptyList<String>(), said)
    }

    @Test
    fun onAnAppThreadNothingLeavesButCancellation() {
        for (failure in listOf(IllegalStateException("exception"), Error("error"))) {
            assertNull(log.onAppThread({ "report a model" }) { throw failure })
            assertSaidOnce("report a model", failure)
        }

        val cancellation = CancellationException("the application's")
        assertSame(cancellation, assertFailsWith<CancellationException> { log.onAppThread({ "report a model" }) { throw cancellation } })
        assertEquals(emptyList<String>(), said)
    }

    @Test
    fun nothingTheApplicationsCodeThrowsLeaves() {
        for (failure in listOf(IllegalStateException("exception"), Error("error"), CancellationException("cancellation"))) {
            assertNull(log.callingApp({ "encode a model" }) { throw failure })
            assertSaidOnce("encode a model", failure)
        }
    }

    @Test
    fun aFailureOfTheApplicationsCodeCanBeReturnedInsteadOfSaid() {
        for (failure in listOf(IllegalStateException("exception"), Error("error"), CancellationException("cancellation"))) {
            assertSame<Throwable?>(failure, callingApp<Throwable?>(onFailure = { it }) { throw failure })
        }
        assertEquals(emptyList<String>(), said)
    }

    @Test
    fun onTheProbesCoroutineAnExceptionDoesNotLeave() = runTest {
        // A cancellation that is not the caller's own is a failure like any other.
        for (failure in listOf(IllegalStateException("exception"), CancellationException("not the probe's"))) {
            assertNull(log.onProbeCoroutine({ "answer a request" }) { throw failure })
            assertSaidOnce("answer a request", failure)
        }
    }

    @Test
    fun onTheProbesCoroutineAnErrorLeaves() = runTest {
        val error = Error("out of memory")
        assertSame(error, assertFailsWith<Error> { log.onProbeCoroutine({ "answer a request" }) { throw error } })
        assertEquals(emptyList<String>(), said)
    }

    @Test
    fun onTheProbesCoroutineItsOwnCancellationEndsTheCaller() = runTest {
        var wentOn = false
        val probe = launch {
            log.onProbeCoroutine({ "answer a request" }) {
                cancel()
                throw CancellationException("the probe's own")
            }
            wentOn = true
        }
        probe.join()

        assertTrue(probe.isCancelled)
        assertFalse(wentOn, "A cancelled probe must not go on")
        assertEquals(emptyList<String>(), said)
    }
}
