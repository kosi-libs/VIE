package org.kodein.arch.vie.timemachine.protocol

import kotlinx.serialization.Serializable


/**
 * Information about one of the three VIE types (`Model`, `Intent` or `Signal`) used by a ViewModel.
 *
 * @property typeName The type's serial name: its fully qualified name unless `@SerialName` overrides
 *   it. A type that is not `@Serializable` is named by its class instead, fully qualified except on
 *   web, where only the simple name is available.
 * @property isSerializable Whether the type is `@Serializable` (a prerequisite for interventions and
 *   for a JSON representation in observations).
 * @property sealedSubTypes `null` when the type is not `sealed`; otherwise the serial names of its
 *   `@Serializable` subtypes (which may be empty).
 */
@Serializable
public data class VIETypeInfo(
    val typeName: String,
    val isSerializable: Boolean,
    val sealedSubTypes: List<String>? = null,
)
