package org.kodein.arch.vie.timemachine.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js


internal actual val timeMachineEngine: HttpClientEngineFactory<*> get() = Js
