package org.kodein.arch.vie.tm.protocol

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


/**
 * A single VIE object (`Model`, `Intent` or `Signal`) observed on a client.
 *
 * @property viewModelId Unique identifier of the ViewModel it happened into.
 * @property at When it was observed on the client (used to order a timeline across reconnects).
 * @property kind Whether the object is a `Model`, an `Intent` or a `Signal`.
 * @property typeName Fully qualified type of the object.
 * @property asString The `toString()` representation of the object.
 * @property asJson The JSON representation of the object, present only if its type is `@Serializable`.
 * @property injected `true` when the object was injected by the server (see intervention), `false`
 *   when it originated from the application itself.
 */
@Serializable
public data class VIEObservation(
    val viewModelId: Uuid,
    val at: Instant,
    val kind: VIEObjectKind,
    val typeName: String,
    val asString: String,
    val asJson: JsonElement? = null,
    val injected: Boolean = false,
)
