package org.kodein.arch.vie.timemachine.protocol

import kotlinx.serialization.json.Json


/**
 * The [Json] configuration that both the client and the server must use to encode and decode
 * TimeMachine protocol frames, so that their settings cannot drift apart.
 *
 * Its class discriminator is `#type` rather than the default `type`: the probe also encodes the
 * app's own sealed types, and one of them having a `type` property is common enough that the
 * default would make it impossible to encode. No Kotlin property can be named `#type` without
 * backticks.
 *
 * The discriminator key is part of the frozen handshake envelope (see
 * [TimeMachineClientMessage.Hello]), and so is [Json.configuration]'s `ignoreUnknownKeys`: it is what
 * lets a later version's `Hello` decode as this one's.
 */
public val TimeMachineJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "#type"
}
