package org.kodein.arch.vie.timemachine.client

import io.ktor.client.engine.HttpClientEngineFactory


/** The ktor engine the probe connects to the inspector with: one per platform, all WebSocket-capable. */
internal expect val timeMachineEngine: HttpClientEngineFactory<*>
