package org.kodein.arch.vie.timemachine.protocol

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable


/**
 * Full description of a VIE ViewModel running on a client, as sent when it starts or when the
 * server explicitly requests it.
 *
 * @property id Unique identifier of this ViewModel instance (among multiple instances of [typeName]).
 * @property typeName The ViewModel's type name: fully qualified where the platform allows it, simple
 *   on the web and for a local class, and `<anonymous>` for an anonymous one.
 */
@Serializable
public data class VIEViewModelInfo(
    val id: Uuid,
    val typeName: String,
    val model: VIETypeInfo,
    val intent: VIETypeInfo,
    val signal: VIETypeInfo,
)
