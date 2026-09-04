package org.kodein.arch.vie

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow


/**
 * A full [VIE] implementation for tests: holds a model, records every posted intent, and lets a
 * test emit signals — built for assertions, not for previews (use [EmptyVIE] for that).
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

    /** Mutable: assign to it to re-render the view with a new model. */
    override val modelFlow: MutableStateFlow<Model> = MutableStateFlow(model)

    /** Every intent the view has posted, in the order it posted them. Drain with [pendingIntents]. */
    public val intentsChannel: ReceiveChannel<Intent> field = Channel(Channel.UNLIMITED)

    /** Records [intent] into [intentsChannel]. Nothing processes it — no model update, no signal. */
    override fun post(intent: Intent) {
        intentsChannel.trySend(intent)
    }

    /**
     * Mutable: `emit` into it to drive the view's [SignalEffect]. Has no replay, so a signal
     * emitted before the composition starts collecting is dropped — emit it after the view has
     * composed.
     */
    override val signalFlow: MutableSharedFlow<Signal> = MutableSharedFlow()
}

/**
 * Drains [MockVIE.intentsChannel] and returns everything posted since the previous call, in
 * order. This *drains* — it is not an accumulating log: calling it twice in a row without an
 * intervening [VIE.post] returns an empty list the second time.
 *
 * @receiver The mock to drain posted intents from.
 * @return Every intent posted since the previous call, in order; an empty list if none were.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <Model: VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> MockVIE<Model, Intent, Signal>.pendingIntents(): List<Intent> {
    if (intentsChannel.isEmpty) return emptyList()
    val list = mutableListOf<Intent>()
    do {
        val r = intentsChannel.tryReceive()
        if (r.isSuccess) list.add(r.getOrThrow())
    } while (r.isSuccess)
    return list
}

/**
 * A [VIEViewScope] with nothing behind it, for previews: [post] discards the intent, and
 * [signalFlow] is an always-empty [Flow], so a [SignalEffect] in the view never fires.
 *
 * It carries no model — unlike [MockVIE], there is nothing to seed — so the model goes to the
 * view's own `model` parameter, once:
 *
 * ```kotlin
 * @Preview
 * @Composable
 * fun CounterPreview() {
 *     EmptyVIE<CounterVIE.Intent, CounterVIE.Signal>().Counter(
 *         model = CounterVIE.Model(count = 3),
 *     )
 * }
 * ```
 *
 * @param Intent The events the view can send up (discarded).
 * @param Signal The one-offs the view can react to (never emitted).
 */
public class EmptyVIE<Intent : VIE.Intent, Signal : VIE.Signal>() : VIEViewScope<Intent, Signal> {
    /** Discards [intent]. */
    override fun post(intent: Intent) {}
    /** Always empty. */
    override val signalFlow: Flow<Signal> = emptyFlow()
}
