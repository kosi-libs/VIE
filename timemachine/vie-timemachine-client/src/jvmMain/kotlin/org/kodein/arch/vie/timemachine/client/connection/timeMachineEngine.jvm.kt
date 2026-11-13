package org.kodein.arch.vie.timemachine.client.connection

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO


internal actual val timeMachineEngine: HttpClientEngineFactory<*> get() = CIO
