package org.kodein.arch.vie

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach


/**
 * Intercepts a [VIEViewModel]'s model, intent and signal flows as plain [Flow] transformations —
 * logging, filtering, replay — without the view knowing. Every `intercept*` method defaults to
 * identity, [onAttached] and [onModelUpdated] to a no-op, so a middleware only needs to override what it cares
 * about. A middleware sees the ViewModel itself only if it overrides [onAttached].
 *
 * Several middlewares are folded from the end of the list: the *last* one wraps the raw source
 * flow, the *first* sits closest to the ViewModel and view.
 *
 * @param Model The state the view renders.
 * @param Intent The events the view sends up.
 * @param Signal The one-offs the view reacts to but does not render.
 */
public interface VIEMiddleware<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> {

    /**
     * Transforms the model flow. Identity by default.
     *
     * @param flow The model flow as produced by the next middleware down (or the ViewModel).
     * @return The transformed model flow.
     */
    public fun interceptModels(flow: Flow<Model>): Flow<Model> = flow

    /**
     * Transforms the intent flow. Identity by default.
     *
     * @param flow The intent flow as produced by the next middleware down (or the view).
     * @return The transformed intent flow.
     */
    public fun interceptIntents(flow: Flow<Intent>): Flow<Intent> = flow

    /**
     * Transforms the signal flow. Identity by default.
     *
     * @param flow The signal flow as produced by the next middleware down (or the ViewModel).
     * @return The transformed signal flow.
     */
    public fun interceptSignals(flow: Flow<Signal>): Flow<Signal> = flow

    /**
     * Hands the middleware the [VIEViewModel] it is installed on — for example to drive it from
     * the outside, rather than only observing its flows. Does nothing by default.
     *
     * Called once from [VIEViewModel]'s `init`, after every middleware has been folded into the
     * flows. [VIEViewModel]'s own setup is complete at this point, but its subclass has not been
     * constructed yet: nothing done here may reach subclass code — [VIEViewModel.post] (which runs
     * `process`), `update`, overridden members or subclass state.
     *
     * Storing [vm], registering a closeable on it, and launching into its `viewModelScope` are all
     * fine. A launched coroutine may start running right away, though (on
     * `Dispatchers.Main.immediate`, until its first suspension), so the launched code must follow
     * the same rule until construction ends.
     *
     * A middleware overriding this must not be attached to more than one [VIEViewModel]: a second
     * [onAttached] call overwrites whatever the middleware stored from the first, so an instance
     * shared across ViewModels (e.g. a DI singleton) would silently start driving the wrong one.
     *
     * @param vm The ViewModel this middleware was installed on.
     */
    public fun onAttached(vm: VIEViewModel<Model, Intent, Signal>) {}

    /**
     * Called with each model [VIEViewModel.update] produces, right after it was applied and before
     * `update` returns. Does nothing by default.
     *
     * Unlike [interceptModels], whose flow only runs once a view collects and which a `StateFlow`
     * conflates, this sees every transition, in order, with or without a view — including one that
     * leaves the model equal. It receives the model as `update` produced it, before any middleware's
     * [interceptModels] transformed it. The first model is not reported, as no `update` produced it.
     *
     * Runs on the thread of the ViewModel's update loop, one call at a time: it must be quick, and
     * must not throw — a failure here ends the loop, and every later `update` would suspend forever.
     *
     * @param model The model that was just applied.
     */
    public fun onModelUpdated(model: Model) {}

}

/**
 * A ready-made [VIEMiddleware] that prints every model, intent and signal, plus `START` / `END`
 * markers, each line prefixed with [name].
 *
 * @param Model The state the view renders.
 * @param Intent The events the view sends up.
 * @param Signal The one-offs the view reacts to but does not render.
 * @param name The prefix printed on every logged line.
 */
public class VIELoggingMiddleware<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    private val name: String,
) : VIEMiddleware<Model, Intent, Signal> {

    init {
        println("$name START")
    }

    /** Prints every model, and `$name END` once the flow completes. */
    override fun interceptModels(flow: Flow<Model>): Flow<Model> =
        flow
            .onEach { println("$name MODEL: $it") }
            .onCompletion { println("$name END") }

    /** Prints every intent. */
    override fun interceptIntents(flow: Flow<Intent>): Flow<Intent> = flow.onEach { println("$name INTENT: $it") }

    /** Prints every signal. */
    override fun interceptSignals(flow: Flow<Signal>): Flow<Signal>  = flow.onEach { println("$name SIGNAL: $it") }
}
