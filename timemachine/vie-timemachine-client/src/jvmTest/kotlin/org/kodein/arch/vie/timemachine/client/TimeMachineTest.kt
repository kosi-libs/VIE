package org.kodein.arch.vie.timemachine.client

import androidx.lifecycle.ViewModelStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIELoggingMiddleware
import org.kodein.arch.vie.VIEViewModel
import org.kodein.arch.vie.timemachine.client.fixtures.FakeInspector
import org.kodein.arch.vie.timemachine.client.fixtures.role
import org.kodein.arch.vie.timemachine.client.role.displayName
import org.kodein.arch.vie.timemachine.client.viewmodel.StressIntent
import org.kodein.arch.vie.timemachine.client.viewmodel.StressModel
import org.kodein.arch.vie.timemachine.client.viewmodel.StressSignal
import org.kodein.arch.vie.timemachine.client.viewmodel.StressVM
import org.kodein.arch.vie.timemachine.client.viewmodel.TimeMachineMiddleware
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo


/** Installs the middleware as [TimeMachine]'s KDoc shows: its types come from `middlewares`. */
private class SelfObservedVM : VIEViewModel<StressModel, StressIntent, StressSignal>(
    StressModel(),
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
    StressModel(),
    listOf(VIELoggingMiddleware("LoggedAndObserved"), TimeMachine.middleware()),
) {
    override suspend fun process(intent: StressIntent) {}
}

/** Its serializer cannot be introspected: reading its descriptor throws an `Error`. */
@Serializable(with = UndescribableModelSerializer::class)
private class UndescribableModel : VIE.Model

private object UndescribableModelSerializer : KSerializer<UndescribableModel> {
    override val descriptor: SerialDescriptor get() = TODO()
    override fun serialize(encoder: Encoder, value: UndescribableModel) = error("not needed")
    override fun deserialize(decoder: Decoder): UndescribableModel = error("not needed")
}

private class UndescribableVM : VIEViewModel<UndescribableModel, StressIntent, StressSignal>(
    UndescribableModel(),
    listOf(TimeMachine.middleware()),
) {
    override suspend fun process(intent: StressIntent) {}
}

class TimeMachineTest {

    // No inspector listens there: the probe keeps retrying in the background until stopped.
    private fun config(appName: String, enabled: Boolean = true, logger: (String) -> Unit = ::println) =
        TimeMachineConfig(appName = appName, port = 1, enabled = enabled, logger = logger)

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

    @Test
    fun aViewModelWhoseModelCannotBeIntrospectedIsStillBuiltAndObserved() {
        val info = announcedInfoOf { UndescribableVM() }

        assertEquals(UndescribableVM::class.displayName, info.typeName)
        assertFalse(info.model.isSerializable, "The model's role must have fallen back to one without a serializer")
        assertTrue(info.model.typeName.endsWith("UndescribableModel"), info.model.typeName)
        assertEquals(role<StressIntent>().toVIETypeInfo(), info.intent)
        assertEquals(role<StressSignal>().toVIETypeInfo(), info.signal)
    }

    @Test
    fun aProbeWhoseConnectionLoopDiesOfAnErrorIsRetiredAndCanBeStartedAgain() {
        val config = config("Retired")
        val retired = CompletableDeferred<Unit>()
        TimeMachine.start(config) { onRetired ->
            TimeMachineProbe(
                config,
                onRetired = {
                    onRetired(it)
                    retired.complete(Unit)
                },
                // Thrown on the probe's own coroutine, where its connection loop runs.
                newConnection = { _, _, _ -> throw LinkageError("from the test") },
            )
        }
        runBlocking { withTimeout(10.seconds) { retired.await() } }

        assertSame<Any>(NoOpMiddleware, middleware(), "A retired probe must no longer provide observing middlewares")
        TimeMachine.start(config("Again"))
        assertIs<TimeMachineMiddleware<*, *, *>>(middleware())
    }

    @Test
    fun anErrorWhileBuildingTheProbeDoesNotReachTheCaller() {
        TimeMachine.start(config("Unbuildable")) { throw NoClassDefFoundError("from the test") }
        assertSame<Any>(NoOpMiddleware, middleware())
    }

    @Test
    fun everythingTheProbeSaysGoesToTheLoggerOfItsConfiguration() {
        val said = CopyOnWriteArrayList<String>()
        TimeMachine.start(config("Logged", logger = { said += it }))
        assertTrue("TimeMachine probe started for \"Logged\"" in said.first(), said.toString())

        TimeMachine.start(config("Second", logger = { said += it }))
        assertTrue("already runs" in said.last(), said.toString())

        TimeMachine.stop()
        said.clear()
        TimeMachine.start(TimeMachineConfig(appName = "Invalid", enabled = true, port = 0, logger = { said += it }))
        assertTrue("does not start" in said.single() && "port" in said.single(), said.toString())
    }

    @Test
    fun aDisabledProbeNeverCallsItsLogger() {
        val said = CopyOnWriteArrayList<String>()
        // Invalid too: a disabled probe does not even say what is wrong with its configuration.
        TimeMachine.start(TimeMachineConfig(appName = "Disabled", enabled = false, port = 0, logger = { said += it }))
        middleware()
        TimeMachine.stop()
        assertEquals(emptyList<String>(), said.toList())
    }

    @Test
    fun aLoggerThatThrowsDoesNotKeepTheProbeFromRunning() {
        TimeMachine.start(config("Unloggable", logger = { throw LinkageError("from the test") }))
        assertIs<TimeMachineMiddleware<*, *, *>>(middleware())
        TimeMachine.stop()
        assertSame<Any>(NoOpMiddleware, middleware())
    }

    /** Starts the probe against an inspector, builds a ViewModel with [newVM], and checks how the inspector sees it. */
    private fun assertAnnouncedAs(type: KClass<*>, newVM: () -> VIEViewModel<StressModel, StressIntent, StressSignal>) {
        val info = announcedInfoOf(newVM)

        assertEquals(type.displayName, info.typeName)
        assertEquals(role<StressModel>().toVIETypeInfo(), info.model)
        assertEquals(role<StressIntent>().toVIETypeInfo(), info.intent)
        assertEquals(role<StressSignal>().toVIETypeInfo(), info.signal)
    }

    /** Starts the probe against an inspector, builds a ViewModel with [newVM], and returns how it was announced. */
    private fun announcedInfoOf(newVM: () -> VIEViewModel<*, *, *>): VIEViewModelInfo =
        FakeInspector().use { inspector ->
            TimeMachine.start(TimeMachineConfig(appName = "Announced", enabled = true, port = inspector.port))
            ViewModelStore().apply { put("vm", newVM()) }.clear()

            runBlocking {
                withTimeout(10.seconds) {
                    inspector.received.consumeAsFlow().filterIsInstance<TimeMachineClientMessage.ViewModelStarted>().first()
                }
            }.info
        }
}
