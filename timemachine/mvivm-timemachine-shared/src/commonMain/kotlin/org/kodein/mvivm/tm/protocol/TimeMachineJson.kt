package org.kodein.mvivm.tm.protocol

import kotlinx.serialization.json.Json


/**
 * The [Json] configuration that both the client and the server must use to encode and decode
 * TimeMachine protocol frames, so that their settings cannot drift apart.
 */
public val TimeMachineJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
