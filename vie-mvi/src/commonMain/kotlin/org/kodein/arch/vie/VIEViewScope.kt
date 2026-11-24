package org.kodein.arch.vie

import kotlinx.coroutines.flow.Flow


/**
 * What a view sees of its ViewModel: a way to post an [Intent] up, and a stream of [Signal]s to
 * react to. Nothing else — no `modelFlow`, no `update`, no `viewModelScope`, no `process`.
 *
 * A view written as an extension on this scope (rather than taking a [VIEViewModel] directly) can
 * be rendered against a real [VIEViewModel] through `VIEView`, against [EmptyVIE] for previews, or
 * against `MockVIE` in tests — with zero Compose ViewModel machinery in the last two cases.
 *
 * @param Intent The events the view can send up.
 * @param Signal The one-offs the view can react to.
 */
public interface VIEViewScope<Intent : VIE.Intent, Signal : VIE.Signal> {
    /** Sends [intent] up to the ViewModel for processing. */
    public fun post(intent: Intent)
    /** The stream of one-off [Signal]s the view should react to but not render. */
    public val signalFlow: Flow<Signal>
}
