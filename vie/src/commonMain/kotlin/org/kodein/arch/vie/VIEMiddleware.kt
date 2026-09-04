package org.kodein.arch.vie

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach


public interface VIEMiddleware<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal> {

    public fun interceptModels(flow: Flow<Model>): Flow<Model> = flow

    public fun interceptIntents(flow: Flow<Intent>): Flow<Intent> = flow

    public fun interceptSignals(flow: Flow<Signal>): Flow<Signal> = flow

}

public class VIELoggingMiddleware<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    private val name: String,
) : VIEMiddleware<Model, Intent, Signal> {

    init {
        println("$name START")
    }

    override fun interceptModels(flow: Flow<Model>): Flow<Model> =
        flow
            .onEach { println("$name MODEL: $it") }
            .onCompletion { println("$name END") }

    override fun interceptIntents(flow: Flow<Intent>): Flow<Intent> = flow.onEach { println("$name INTENT: $it") }

    override fun interceptSignals(flow: Flow<Signal>): Flow<Signal>  = flow.onEach { println("$name SIGNAL: $it") }
}
