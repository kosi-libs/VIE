package org.kodein.mvi.nomi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow


public interface MVI<Model : MVI.Model, Intent : MVI.Intent, Signal : MVI.Signal> : MVIViewContext<Intent, Signal> {

    public interface Model
    public interface Intent {
        public class None private constructor() : Intent
    }
    public interface Signal {
        public sealed interface Type
        public interface Effect: Signal, Type
        public interface Event: Signal, Type
        public abstract class None private constructor() : Type
    }

    public val modelFlow: StateFlow<Model>
    override fun post(intent: Intent)
    override val signalFlow: Flow<Signal>
}

