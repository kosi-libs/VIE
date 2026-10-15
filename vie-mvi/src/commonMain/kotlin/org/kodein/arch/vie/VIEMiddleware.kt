package org.kodein.arch.vie

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach


/**
 * Intercepts a [VIEViewModel]'s model, intent and signal flows; every member defaults to identity
 * or no-op. Folded from the end of the list: the last wraps the source, the first sits closest to
 * the view.
 *
 * @param Model The state the view renders.
 * @param Intent The events the view sends up.
 * @param Signal The one-offs the view reacts to but does not render.
 */
public interface VIEMiddleware<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> {

    /**
     * Transforms the model flow. Identity by default. It receives the current model, then every model
     * `update` applies, in order and unconflated (equal ones included), whether or not a view collects;
     * only [VIEViewModel.modelFlow] conflates. `update` does not wait for it: unread models are buffered.
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
     * Hands the middleware its [VIEViewModel], once, first thing in `init`: no flow has started and
     * [VIEViewModel.modelFlow] is not assigned yet. The subclass is not constructed either, so don't
     * reach `post`, `update` or subclass members, even from a coroutine launched here. Don't share a
     * middleware that overrides this across ViewModels.
     *
     * @param vm The ViewModel this middleware was installed on.
     */
    public fun onAttached(vm: VIEViewModel<Model, Intent, Signal>) {}

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
