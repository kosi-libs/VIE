package org.kodein.arch.vie.tm.protocol

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable


/**
 * Full description of an VIE ViewModel running on a client, as sent when it starts or when the
 * server explicitly requests it.
 *
 * @property id Unique identifier of this ViewModel instance (among multiple instances of [typeName]).
 * @property typeName Fully qualified type name of the ViewModel.
 */
@Serializable
public data class VIEViewModelInfo(
    val id: Uuid,
    val typeName: String,
    val model: VIETypeInfo,
    val intent: VIETypeInfo,
    val signal: VIETypeInfo,
)
