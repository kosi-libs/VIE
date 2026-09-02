package org.kodein.mvi.nomi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach


public interface MVIMiddleware<Model : MVI.Model, Intent : MVI.Intent, Signal : MVI.Signal> {

    public fun interceptModels(flow: Flow<Model>): Flow<Model> = flow

    public fun interceptIntents(flow: Flow<Intent>): Flow<Intent> = flow

    public fun interceptSignals(flow: Flow<Signal>): Flow<Signal> = flow

}

public class LoggingMiddleware<Model : MVI.Model, Intent : MVI.Intent, Signal : MVI.Signal>(
    public val tag: String,
) : MVIMiddleware<Model, Intent, Signal> {

    override fun interceptModels(flow: Flow<Model>): Flow<Model> =
        flow.onEach { println("$tag: MODEL: $it") }

    override fun interceptIntents(flow: Flow<Intent>): Flow<Intent> =
        flow.onEach { println("$tag: INTENT: $it") }

    override fun interceptSignals(flow: Flow<Signal>): Flow<Signal> =
        flow.onEach { println("$tag: SIGNAL: $it") }

}