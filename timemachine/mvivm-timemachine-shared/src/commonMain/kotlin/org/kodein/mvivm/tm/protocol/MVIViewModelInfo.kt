package org.kodein.mvivm.tm.protocol

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable


/**
 * Full description of an MVI ViewModel running on a client, as sent when it starts or when the
 * server explicitly requests it.
 *
 * @property id Unique identifier of this ViewModel instance (among multiple instances of [typeName]).
 * @property typeName Fully qualified type name of the ViewModel.
 */
@Serializable
public data class MVIViewModelInfo(
    val id: Uuid,
    val typeName: String,
    val model: MVITypeInfo,
    val intent: MVITypeInfo,
    val signal: MVITypeInfo,
)
