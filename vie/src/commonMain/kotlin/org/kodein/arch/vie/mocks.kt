package org.kodein.arch.vie

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow


public class MockVIE<Model: VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    model: Model,
) : VIE<Model, Intent, Signal> {

    public val model: Model get() = modelFlow.value

    override val modelFlow: MutableStateFlow<Model> = MutableStateFlow(model)

    public val intentsChannel: ReceiveChannel<Intent> field = Channel(Channel.UNLIMITED)

    override fun post(intent: Intent) {
        intentsChannel.trySend(intent)
    }

    override val signalFlow: MutableSharedFlow<Signal> = MutableSharedFlow()
}

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

public class EmptyVIE<Intent : VIE.Intent, Signal : VIE.Signal>() : VIEViewScope<Intent, Signal> {
    override fun post(intent: Intent) {}
    override val signalFlow: Flow<Signal> = emptyFlow()
}
