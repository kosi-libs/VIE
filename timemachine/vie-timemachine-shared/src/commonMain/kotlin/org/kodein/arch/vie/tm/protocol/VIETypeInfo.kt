package org.kodein.arch.vie.tm.protocol

import kotlinx.serialization.Serializable


/**
 * Information about one of the three VIE types (`Model`, `Intent` or `Signal`) used by a ViewModel.
 *
 * @property typeName The fully qualified type name.
 * @property isSerializable Whether the type is `@Serializable` (a prerequisite for interventions and
 *   for a JSON representation in observations).
 * @property sealedSubTypes `null` when the type is not `sealed`; otherwise the fully qualified names
 *   of its `@Serializable` subtypes (which may be empty).
 */
@Serializable
public data class VIETypeInfo(
    val typeName: String,
    val isSerializable: Boolean,
    val sealedSubTypes: List<String>? = null,
)
