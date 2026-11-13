package org.kodein.arch.vie.timemachine.client

import kotlin.concurrent.atomics.AtomicReference
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEMiddleware


/**
 * The TimeMachine probe's entry point: streams the ViewModels of this application to the
 * TimeMachine inspector, and lets the inspector drive them.
 *
 * [start] it once, early, behind a debug flag; then install [middleware] *last* in each observed
 * ViewModel's `middlewares`, either in the ViewModel's own super-constructor call:
 *
 * ```kotlin
 * class EditTaskVM : VIEViewModel<EditTaskVIE.Model, EditTaskVIE.Intent, EditTaskVIE.Signal>(
 *     firstModel = EditTaskVIE.Model(),
 *     middlewares = listOf(
 *         VIELoggingMiddleware("EditTask"),
 *         TimeMachine.middleware(),
 *     ),
 * )
 * ```
 *
 * or wherever the ViewModel is built — a DI definition, or any factory — when it takes its
 * `middlewares` as a constructor parameter:
 *
 * ```kotlin
 * class EditTaskVM(
 *     middlewares: List<VIEMiddleware<EditTaskVIE.Model, EditTaskVIE.Intent, EditTaskVIE.Signal>>,
 * ) : VIEViewModel<EditTaskVIE.Model, EditTaskVIE.Intent, EditTaskVIE.Signal>(EditTaskVIE.Model(), middlewares)
 *
 * EditTaskVM(listOf(VIELoggingMiddleware("EditTask"), TimeMachine.middleware()))
 * ```
 *
 * The probe has one session per process, and one WebSocket to the inspector, which it keeps
 * reconnecting for as long as it runs: the app and the inspector can start in either order. It
 * retries within seconds at first, then every half minute once no inspector has been there for a
 * minute: an inspector opened that late may wait that long for the app to show up.
 * Everything is streamed in cleartext, and the inspector can make any ViewModel emit or receive any
 * object: never leave the probe enabled in a release build.
 */
public object TimeMachine {

    private val running = AtomicReference<TimeMachineProbe?>(null)

    /** Whether the probe runs, i.e. whether [middleware] returns a middleware that observes. */
    @PublishedApi
    internal val isStarted: Boolean get() = running.load() != null

    /**
     * Starts the probe, unless [config] is not [TimeMachineConfig.enabled] or the probe already runs:
     * a running probe must be [stop]ped before it can start with another configuration.
     *
     * Never throws: an invalid [config] is printed, and so is a probe that fails to build or start;
     * neither runs. Nor does a probe keep running once its connection to the inspector fails beyond
     * recovery, on an `Error` such as running out of memory while encoding a model: it is printed
     * and stopped, as by [stop], and can be started again.
     */
    public fun start(config: TimeMachineConfig) {
        start(config) { onRetired -> TimeMachineProbe(config, onRetired = onRetired) }
    }

    /** [start], with the probe built by [newProbe], which hands it what to call once retired. */
    internal fun start(config: TimeMachineConfig, newProbe: (onRetired: (TimeMachineProbe) -> Unit) -> TimeMachineProbe) {
        if (!config.enabled) return
        // Only once enabled: a disabled probe must not even print about a configuration it never uses.
        config.problem?.let { return println("TimeMachine: the probe does not start: $it.") }
        // Checked before building a probe; compareAndSet still settles a race.
        if (running.load() != null) return printAlreadyRunning()
        var probe: TimeMachineProbe? = null
        try {
            probe = newProbe { retired -> running.compareAndSet(retired, null) }
            if (running.compareAndSet(null, probe)) {
                probe.start()
            } else {
                probe.stop()
                printAlreadyRunning()
            }
        } catch (e: Throwable) {
            // An Error too: it must not reach the application's startup. The HTTP engine is not loaded
            // here but on the probe's own coroutine, where an Error retires the probe.
            println("TimeMachine: failed to start the probe: $e")
            if (probe != null) {
                running.compareAndSet(probe, null)
                try {
                    probe.stop()
                } catch (e: Throwable) {
                    println("TimeMachine: failed to stop the probe: $e")
                }
            }
        }
    }

    private fun printAlreadyRunning() {
        println("TimeMachine: the probe already runs; stop() it before starting it again.")
    }

    /**
     * Stops the probe: closes its connection and discards what it had not sent yet. The middlewares
     * it provided stay installed, and inert: a probe started again opens a new session, which only
     * ViewModels created from then on report to.
     */
    public fun stop() {
        val probe = running.exchange(null) ?: return
        guarded({ "stop the probe" }) { probe.stop() }
    }

    /**
     * The middleware that observes the ViewModel it is installed in. It reports that ViewModel under
     * the name of its class, read once attached. While the probe does not run, it is a middleware that
     * does nothing, and stays so: a probe started later only observes ViewModels built from then on.
     *
     * Its model, intent and signal types are inferred from the list it is put in: the `middlewares`
     * of a ViewModel, or a constructor parameter of that type. Where nothing gives the call an expected
     * type — a local `val`, the left side of `+` — name them:
     * `TimeMachine.middleware<Model, Intent, Signal>()`.
     *
     * Install it *last* in `middlewares`, nearest the source: it then reports intents as the view posted
     * them and signals as the ViewModel emitted them, before any other middleware can filter or transform
     * them. Models are reported as `update {}` produced them, wherever it sits.
     *
     * Build the list once per ViewModel, and never share it (e.g. as a DI singleton): while the probe
     * runs, each call returns a new middleware, and an instance serves exactly one ViewModel.
     *
     * The probe does not keep an observed ViewModel alive: one that is never cleared — built by hand,
     * or dropped by a scope that does not clear it — is still garbage-collected, and the inspector
     * then shows it closed, some time later.
     *
     * Never throws, since it runs inside the ViewModel's constructor: a model, intent or signal type
     * whose serializer cannot be introspected is printed, and its objects are reported without JSON.
     */
    public inline fun <reified Model : VIE.Model, reified Intent : VIE.Intent, reified Signal : VIE.Signal> middleware(): VIEMiddleware<Model, Intent, Signal> {
        // Checked first: a probe that does not run must not even introspect the roles.
        if (!isStarted) return NoOpMiddleware.typed()
        return middleware(typeOf<Model>(), typeOf<Intent>(), typeOf<Signal>())
    }

    @PublishedApi
    internal fun <Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> middleware(
        model: KType,
        intent: KType,
        signal: KType,
    ): VIEMiddleware<Model, Intent, Signal> =
        // Stopped since isStarted was checked: the no-op, as if it had been checked a moment later.
        // So is a middleware that fails to build: this runs inside a ViewModel's constructor.
        guarded({ "provide a middleware" }) { running.load()?.middleware<Model, Intent, Signal>(model, intent, signal) }
            ?: NoOpMiddleware.typed()
}
