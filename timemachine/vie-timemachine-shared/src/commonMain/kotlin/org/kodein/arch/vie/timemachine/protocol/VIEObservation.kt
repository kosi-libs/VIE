package org.kodein.arch.vie.timemachine.protocol

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


/**
 * A single VIE object (`Model`, `Intent` or `Signal`) observed on a client.
 *
 * @property viewModelId Unique identifier of the ViewModel it happened into.
 * @property at When it was observed on the client. Display-only: it cannot order a timeline (see
 *   `TimeMachineTimelineMessage.sequence`, carried by the containing `Observed` message).
 * @property kind Whether the object is a `Model`, an `Intent` or a `Signal`.
 * @property typeName The object's concrete type, named as [VIETypeInfo.typeName] and
 *   [VIETypeInfo.sealedSubTypes] name types: its serial name when it has one. This is the name
 *   [TimeMachineServerMessage.Intervene.typeName] expects back.
 * @property asString The `toString()` representation of the object.
 * @property asJson The JSON representation of the object, present only if its type is `@Serializable`.
 * @property interventionId The [TimeMachineServerMessage.Intervene.requestId] of the intervention
 *   that placed the object, or `null` when the application produced it itself.
 * @property delivery For a [VIEObjectKind.Signal], the delivery kind its type picked; `null` for a
 *   Model or an Intent, or for a Signal that picked neither (the ViewModel cannot emit it).
 */
@Serializable
public data class VIEObservation(
    val viewModelId: Uuid,
    val at: Instant,
    val kind: VIEObjectKind,
    val typeName: String,
    val asString: String,
    val asJson: JsonElement? = null,
    val interventionId: Uuid? = null,
    val delivery: VIESignalDelivery? = null,
)
