package org.kodein.arch.vie.test

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.kodein.arch.vie.VIE


/**
 * A full [org.kodein.arch.vie.VIE] implementation for tests: holds a model, records every posted intent, and lets a
 * test emit signals — built for assertions, not for previews (use [org.kodein.arch.vie.EmptyVIE] for that).
 *
 * ```kotlin
 * val vie = MockVIE<CounterVIE.Model, CounterVIE.Intent, CounterVIE.Signal>(
 *     CounterVIE.Model(count = 0),
 * )
 * // render the view against `vie`, then simulate the user tapping "+" twice …
 * vie.post(CounterVIE.Intent.Increment)
 * vie.post(CounterVIE.Intent.Increment)
 * assertEquals(
 *     listOf(CounterVIE.Intent.Increment, CounterVIE.Intent.Increment),
 *     vie.pendingIntents(),
 * )
 * ```
 *
 * @param Model The state the view renders.
 * @param Intent The events the view sends up.
 * @param Signal The one-offs the view reacts to but does not render.
 * @param model The model to seed [modelFlow] with.
 */
public class MockVIE<Model: VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    model: Model,
) : VIE<Model, Intent, Signal> {

    /** The current model — shorthand for `modelFlow.value`. */
    public val model: Model get() = modelFlow.value

    /**
     * Mutable: assign to it, then render `modelFlow.collectAsState()`, to re-render the view with a new model.
     * A view that reads [model] alone does not recompose.
     */
    override val modelFlow: MutableStateFlow<Model> = MutableStateFlow(model)

    /** The intents the view posted and nobody received or drained yet, in order. Drain with [pendingIntents]. */
    public val intentsChannel: ReceiveChannel<Intent> field = Channel(Channel.UNLIMITED)

    /** Records [intent] into [intentsChannel]. Nothing processes it — no model update, no signal. */
    override fun post(intent: Intent) {
        intentsChannel.trySend(intent)
    }

    /**
     * Mutable: `emit` into it (or call [signal]) to drive the view's [org.kodein.arch.vie.SignalEffect].
     * A signal sent while nothing collects is lost: send it after the view has composed.
     */
    override val signalFlow: MutableSharedFlow<Signal> = MutableSharedFlow(extraBufferCapacity = 64)

    /** Non-suspending shorthand for `signalFlow.tryEmit(signal)`, handy from test bodies. */
    public fun signal(signal: Signal) {
        signalFlow.tryEmit(signal)
    }
}

/**
 * Drains [MockVIE.intentsChannel] and returns everything posted since the previous call, in
 * order. This *drains* — it is not an accumulating log: calling it twice in a row without an
 * intervening [VIE.post] returns an empty list the second time.
 *
 * @receiver The mock to drain posted intents from.
 * @return Every intent posted since the previous call, in order; an empty list if none were.
 */
public fun <Model: VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> MockVIE<Model, Intent, Signal>.pendingIntents(): List<Intent> =
    intentsChannel.drain()

/** Receives everything already buffered in this channel, in order, without suspending. */
internal fun <T> ReceiveChannel<T>.drain(): List<T> {
    val list = mutableListOf<T>()
    while (true) {
        val r = tryReceive()
        if (!r.isSuccess) return list
        list.add(r.getOrThrow())
    }
}

