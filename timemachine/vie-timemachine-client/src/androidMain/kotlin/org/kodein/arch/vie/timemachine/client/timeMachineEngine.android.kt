package org.kodein.arch.vie.timemachine.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO


// jvm and android share no parent source set: this duplicates the jvm actual.
internal actual val timeMachineEngine: HttpClientEngineFactory<*> get() = CIO
