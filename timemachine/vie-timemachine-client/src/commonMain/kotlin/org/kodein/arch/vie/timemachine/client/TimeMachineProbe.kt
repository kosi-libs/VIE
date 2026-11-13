package org.kodein.arch.vie.timemachine.client

import kotlin.reflect.KType
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.timemachine.client.connection.InspectorConnection
import org.kodein.arch.vie.timemachine.client.role.VIERoles
import org.kodein.arch.vie.timemachine.client.timeline.TimeMachineOutbox
import org.kodein.arch.vie.timemachine.client.timeline.TimeMachineSink
import org.kodein.arch.vie.timemachine.client.viewmodel.TimeMachineMiddleware
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession


/**
 * One run of the probe, from [start] to [stop]: the session of this application process, its sink
 * and outbound queue, the roles of the types it observes, and its [InspectorConnection], which it
 * runs for as long as it runs itself.
 *
 * Never throws into the host app: every failure is printed and swallowed. An `Error` in the
 * connection loop — running out of memory while encoding a model, an engine class that fails to
 * link — is not retried: the outbound queue may have been left half-way through a message, and the
 * same `Error` may well come back on every attempt. It retires the probe instead: the probe is
 * [stop]ped, and `onRetired` is told, so that nothing keeps providing its middlewares.
 *
 * @param notice Prints what the probe and its connection have to say.
 * @param onRetired Called with this probe once an `Error` stopped it for good. Must not throw.
 * @param newConnection Builds the connection that reads this probe's session, sink and outbound queue.
 */
internal class TimeMachineProbe(
    private val config: TimeMachineConfig,
    private val notice: (String) -> Unit = ::println,
    private val onRetired: (TimeMachineProbe) -> Unit = {},
    newConnection: (TimeMachineSession, TimeMachineSink, TimeMachineOutbox) -> InspectorConnection =
        { session, sink, outbox -> InspectorConnection(config, session, sink, outbox, notice = notice) },
) {

    private val session = TimeMachineSession(id = Uuid.random(), name = config.appName)

    private val outbox = TimeMachineOutbox(config.bufferSize)

    private val sink = TimeMachineSink(outbox::offer)

    private val roles = VIERoles()

    // The handler is the backstop behind "never throws into the host app": without it, a failure the
    // connection loop does not catch would reach the thread's uncaught-exception handler, which on
    // Android kills the app.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> retire(e) })

    private val connection = newConnection(session, sink, outbox)

    fun <Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> middleware(
        model: KType,
        intent: KType,
        signal: KType,
    ): TimeMachineMiddleware<Model, Intent, Signal> =
        TimeMachineMiddleware(roles[model], roles[intent], roles[signal], sink)

    fun start() {
        printBanner()
        scope.launch { connection.run() }
    }

    fun stop() {
        scope.cancel()
        sink.close()
        outbox.close()
        connection.close()
    }

    /** Stops this probe for good after [failure] ended its connection loop, rather than leave it
     *  started, buffering for a connection that will never come back. */
    private fun retire(failure: Throwable) {
        // Each step runs whatever the one before it throws, and nothing leaves: this is the scope's
        // exception handler, past which lies the thread's uncaught-exception handler.
        try {
            try {
                println("TimeMachine: the connection to the inspector stopped for good, and the probe with it: $failure")
            } finally {
                try {
                    stop()
                } finally {
                    onRetired(this)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun printBanner() {
        val lines = listOf(
            "TimeMachine probe started for \"${config.appName}\"",
            "Inspector: ${config.inspectorAddress}",
            "Keeps up to ${config.bufferSize} messages the inspector has not taken yet.",
            "",
            "This probe streams every Model, Intent and Signal of this app",
            "in cleartext, and lets the inspector drive its ViewModels.",
            "Never enable it in a release build.",
        )
        val width = lines.maxOf { it.length }
        val border = "─".repeat(width + 2)
        notice(buildString {
            appendLine("┌$border┐")
            lines.forEach { appendLine("│ ${it.padEnd(width)} │") }
            append("└$border┘")
        })
    }
}
