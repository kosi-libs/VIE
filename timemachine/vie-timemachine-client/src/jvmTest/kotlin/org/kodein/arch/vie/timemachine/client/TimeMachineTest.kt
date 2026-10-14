package org.kodein.arch.vie.timemachine.client

import androidx.lifecycle.ViewModelStore
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.kodein.arch.vie.VIELoggingMiddleware
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage


/** Installs the middleware as [TimeMachine]'s KDoc shows: its types come from `middlewares`. */
private class SelfObservedVM : VIEViewModel<StressModel, StressIntent, StressSignal>(
    StressModel,
    listOf(TimeMachine.middleware()),
) {
    override suspend fun process(intent: StressIntent) {}
}

/**
 * Never built: that it compiles is the test. [TimeMachine]'s own example: beside a middleware of
 * another class, `listOf` still infers the types of both from `middlewares`.
 */
@Suppress("unused")
private class LoggedAndObservedVM : VIEViewModel<StressModel, StressIntent, StressSignal>(
    StressModel,
    listOf(VIELoggingMiddleware("LoggedAndObserved"), TimeMachine.middleware()),
) {
    override suspend fun process(intent: StressIntent) {}
}

class TimeMachineTest {

    // No inspector listens there: the probe keeps retrying in the background until stopped.
    private fun config(appName: String, enabled: Boolean = true) = TimeMachineConfig(appName = appName, port = 1, enabled = enabled)

    // Nothing gives this call an expected type: it names the three types itself.
    private fun middleware() = TimeMachine.middleware<StressModel, StressIntent, StressSignal>()

    @AfterTest
    fun stopProbe() {
        TimeMachine.stop()
    }

    @Test
    fun aDisabledProbeProvidesTheNoOpMiddleware() {
        TimeMachine.start(config("Disabled", enabled = false))
        assertSame<Any>(NoOpMiddleware, middleware())
    }

    @Test
    fun aStartedProbeProvidesObservingMiddlewaresUntilStopped() {
        TimeMachine.start(config("Started"))
        assertIs<TimeMachineMiddleware<*, *, *>>(middleware())
        TimeMachine.stop()
        assertSame<Any>(NoOpMiddleware, middleware())
    }

    @Test
    fun startingARunningProbeAgainKeepsItRunning() {
        TimeMachine.start(config("First"))
        TimeMachine.start(config("Second"))
        assertIs<TimeMachineMiddleware<*, *, *>>(middleware())
        TimeMachine.stop()
        assertSame<Any>(NoOpMiddleware, middleware(), "The second start must not have left a probe of its own running")
    }

    @Test
    fun aViewModelInstallsTheMiddlewareInItsOwnSuperCall() {
        assertAnnouncedAs(SelfObservedVM::class) { SelfObservedVM() }
    }

    @Test
    fun aViewModelBuiltFromOutsideGetsTheMiddlewareTypedByItsParameter() {
        // As a DI definition builds it: the list's types come from StressVM's constructor parameter.
        assertAnnouncedAs(StressVM::class) { StressVM(listOf(TimeMachine.middleware())) }
    }

    @Test
    fun stoppingAProbeThatDoesNotRunDoesNothing() {
        TimeMachine.stop()
        assertSame<Any>(NoOpMiddleware, middleware())
    }

    /** Starts the probe against an inspector, builds a ViewModel with [newVM], and checks how the inspector sees it. */
    private fun assertAnnouncedAs(type: KClass<*>, newVM: () -> VIEViewModel<StressModel, StressIntent, StressSignal>) {
        FakeInspector().use { inspector ->
            TimeMachine.start(TimeMachineConfig(appName = "Announced", port = inspector.port))
            ViewModelStore().apply { put("vm", newVM()) }.clear()

            val started = runBlocking {
                withTimeout(10.seconds) {
                    inspector.received.consumeAsFlow().filterIsInstance<TimeMachineClientMessage.ViewModelStarted>().first()
                }
            }
            assertEquals(type.displayName, started.info.typeName)
            assertEquals(role<StressModel>().toVIETypeInfo(), started.info.model)
            assertEquals(role<StressIntent>().toVIETypeInfo(), started.info.intent)
            assertEquals(role<StressSignal>().toVIETypeInfo(), started.info.signal)
        }
    }
}
