package org.kodein.arch.vie

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A [VIEViewScope] with nothing behind it, for previews: [post] discards the intent, and
 * [signalFlow] is an always-empty [kotlinx.coroutines.flow.Flow], so a [SignalEffect] in the view never fires.
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