package org.kodein.mvivm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach


public interface MVIMiddleware<Model : MVI.Model, Intent : MVI.Intent, Signal : MVI.Signal> {

    public fun interceptModels(flow: Flow<Model>): Flow<Model> = flow

    public fun interceptIntents(flow: Flow<Intent>): Flow<Intent> = flow

    public fun interceptSignals(flow: Flow<Signal>): Flow<Signal> = flow

}
