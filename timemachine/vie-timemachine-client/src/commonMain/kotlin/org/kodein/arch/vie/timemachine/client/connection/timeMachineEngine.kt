package org.kodein.arch.vie.timemachine.client.connection

import io.ktor.client.engine.HttpClientEngineFactory


/**
 * The ktor engine the probe connects to the inspector with: one per platform, all WebSocket-capable.
 *
 * Named explicitly rather than left to ktor's discovery, which picks the registered engine with the
 * highest priority — the host app's own, whenever it ships one. That engine may not support
 * WebSockets at all (`ktor-client-apache5`, `ktor-client-android`), and the probe would then never
 * connect.
 */
internal expect val timeMachineEngine: HttpClientEngineFactory<*>
