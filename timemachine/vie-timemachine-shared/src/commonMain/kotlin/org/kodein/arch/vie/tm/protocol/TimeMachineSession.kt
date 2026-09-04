package org.kodein.arch.vie.tm.protocol

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable


/**
 * Identifies a client session.
 *
 * Several sessions may share the same [name] (the same application running different instances).
 * There is only ever one live connection per [id], but a client may disconnect and reconnect with
 * the same [id] (e.g. after a network issue).
 */
@Serializable
public data class TimeMachineSession(
    val id: Uuid,
    val name: String,
)
