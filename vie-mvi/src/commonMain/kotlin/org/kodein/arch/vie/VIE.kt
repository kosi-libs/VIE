package org.kodein.arch.vie

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow


/**
 * The contract between a view and its ViewModel: a [Model] the view renders, an [Intent] the view
 * sends up, and a [Signal] the view reacts to but does not render.
 *
 * A screen declares one interface extending this one, naming its own `Model`, `Intent` and
 * `Signal`. A [VIEViewModel] implements that interface; a [VIEView] renders it. The view only ever
 * depends on the interface — never on the `VIEViewModel` subclass — through [VIEViewScope].
 *
 * ```kotlin
 * interface CounterVIE : VIE<CounterVIE.Model, CounterVIE.Intent, CounterVIE.Signal> {
 *     data class Model(val count: Int) : VIE.Model
 *     sealed interface Intent : VIE.Intent {
 *         data object Increment : Intent
 *     }
 *     sealed interface Signal : VIE.Signal {
 *         data object Saved : Signal, VIE.Signal.Event
 *     }
 * }
 * ```
 *
 * @param Model The state the view renders.
 * @param Intent The events the view sends up.
 * @param Signal The one-offs the view reacts to but does not render.
 */
public interface VIE<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> : VIEViewScope<Intent, Signal> {

    /**
     * Marker for a screen's state type. Always a `data class` — the view relies on its structural
     * equality to skip recomposition when an update produces an equal model. A model is always
     * complete: there is no "nothing yet" state, since a [VIEViewModel] is always constructed with
     * a first model.
     */
    public interface Model

    /**
     * Marker for a screen's UI-event type. An intent is most often something the user did, but not
     * always — a lifecycle callback, a timer firing in the view, an incoming deep link all qualify.
     * Sent from the view with [VIEViewScope.post].
     */
    public interface Intent {
        /**
         * The [Intent] type for a screen that takes no input at all. Its private constructor makes
         * it impossible to instantiate, so [VIEViewScope.post] becomes uncallable at that type.
         */
        public class None private constructor() : Intent
    }

    /**
     * Shorthand for [Intent.None], the [Intent] type of a screen that takes no input at all.
     *
     * Import it directly (`import org.kodein.arch.vie.VIE.NoIntent`) and use the bare name, so a
     * contract reads `VIE<Model, NoIntent, Signal>` rather than `VIE<Model, VIE.Intent.None, Signal>`.
     */
    public typealias NoIntent = Intent.None

    /**
     * Marker for a screen's one-off type: something the view should act on but not render. Every
     * concrete signal must also pick a delivery kind by implementing [Event] or [Effect].
     */
    public interface Signal {
        /** Marker for the delivery kind a concrete [Signal] picks: [Event] or [Effect]. */
        public sealed interface Type
        /** Delivered to exactly one collector of [VIEViewScope.signalFlow]; buffered until one collects, without holding back later signals. Use for one-shot navigation and other actions that must not happen twice. */
        public interface Effect: Signal, Type
        /** Broadcast to every active collector of [VIEViewScope.signalFlow]; dropped if none is currently collecting, since there is no replay buffer. Use for things every listener cares about. */
        public interface Event: Signal, Type
        /**
         * The [Signal] type for a screen with nothing to signal. Abstract and privately
         * constructed, so it can never be instantiated or emitted.
         */
        public abstract class None private constructor() : Signal, Type
    }

    /**
     * Shorthand for [Signal.None], the [Signal] type of a screen with nothing to signal.
     *
     * Import it directly (`import org.kodein.arch.vie.VIE.NoSignal`) and use the bare name, so a
     * contract reads `VIE<Model, Intent, NoSignal>` rather than `VIE<Model, Intent, VIE.Signal.None>`.
     */
    public typealias NoSignal = Signal.None

    /** The current state of the screen. Never has to represent "nothing yet" — see [Model]. */
    public val modelFlow: StateFlow<Model>

    /** Sends [intent] up to the ViewModel for processing. */
    override fun post(intent: Intent)

    /** The stream of one-off [Signal]s the view should react to but not render. */
    override val signalFlow: Flow<Signal>
}

