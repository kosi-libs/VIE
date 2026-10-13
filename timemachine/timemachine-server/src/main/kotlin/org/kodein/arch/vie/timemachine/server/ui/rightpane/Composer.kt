package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery
import org.kodein.arch.vie.timemachine.server.store.SessionRecord
import org.kodein.arch.vie.timemachine.server.store.TypeKey
import org.kodein.arch.vie.timemachine.server.store.ViewModelRecord
import org.kodein.arch.vie.timemachine.server.ui.common.pretty
import org.kodein.arch.vie.timemachine.server.ui.common.role


/** Why nothing can be sent to a ViewModel, in the priority order a reason is given. */
enum class ComposerBlock { UnknownViewModel, SessionDisconnected, ViewModelClosed }

/** Where the composer's prefill comes from. */
sealed interface PrefillSource {

    /** The latest JSON observed for the type, at [sequence]. */
    data class Observed(val sequence: TimeMachineSequence) : PrefillSource

    /** Nothing was observed for the type, and the role is sealed: just its `#type`. */
    data object SealedTemplate : PrefillSource

    /** Nothing was observed for the type, and the role is not sealed: an empty object. */
    data object EmptyObject : PrefillSource
}

data class ComposerPrefill(val json: String, val source: PrefillSource)

/**
 * What the developer is told about sending a Signal of the chosen type. It never disables Send.
 */
enum class SignalNote(val text: String) {
    /** The type is known to be an Effect. */
    Effect("Delivered to one collector: with no view collecting, it blocks this ViewModel's signals, then its intents"),

    /** The type was never observed as a Signal, or is untyped. */
    Unknown("If this is an Effect, it is delivered to one collector: with no view collecting, it blocks this ViewModel's signals, then its intents"),
}

/**
 * What the composer offers for one ViewModel, one kind and one choice of type.
 *
 * @property block Why nothing can be sent, if so.
 * @property enabledKinds The kinds whose role is `@Serializable`: the others are shown disabled.
 * @property types The types the kind offers: the subtypes of a sealed role, else the role's own type.
 * @property type The chosen type, `null` when the role offers none.
 * @property prefill What the editor starts with, `null` when there is no [type] to key it on or no ViewModel.
 * @property signalNote For a Signal only: `null` for a known Event.
 */
data class ComposerTarget(
    val block: ComposerBlock?,
    val kind: VIEObjectKind,
    val enabledKinds: Set<VIEObjectKind>,
    val types: List<String>,
    val type: String?,
    val prefill: ComposerPrefill?,
    val signalNote: SignalNote?,
) {

    /** Whether Send is enabled for the editor's [parse]: it parses, the kind is enabled and nothing blocks. */
    fun canSend(parse: JsonParse): Boolean = parse is JsonParse.Valid && block == null && kind in enabledKinds && type != null
}

/** The first reason nothing can be sent to [vm] of [session]. */
fun composerBlock(session: SessionRecord?, vm: ViewModelRecord?): ComposerBlock? = when {
    session == null || vm == null || vm.isUnknown -> ComposerBlock.UnknownViewModel
    !session.isConnected -> ComposerBlock.SessionDisconnected
    vm.closed -> ComposerBlock.ViewModelClosed
    else -> null
}

/**
 * The composer for [vm] of [session] and [kind].
 *
 * The type list and the fallback prefill key on the role's type info alone, whatever the kind: a
 * Model can be sealed, and an Intent or a Signal can be one plain class. That is the probe's own rule,
 * so the composer never offers a type the probe answers `UnknownType` to.
 *
 * @param chosenType The developer's explicit choice, ignored when the kind does not offer it.
 * @param selected The selected timeline entry, if any: its type is chosen when it is of this [kind].
 */
fun composerTarget(
    session: SessionRecord?,
    vm: ViewModelRecord?,
    kind: VIEObjectKind,
    chosenType: String? = null,
    selected: Pair<VIEObjectKind, String>? = null,
): ComposerTarget {
    val block = composerBlock(session, vm)
    val info = vm?.info
    if (vm == null || info == null) return ComposerTarget(block, kind, emptySet(), emptyList(), null, null, null)

    val role = info.role(kind)
    val types = role.sealedSubTypes ?: listOf(role.typeName)
    val type = chosenType?.takeIf { it in types }
        ?: selected?.takeIf { (selectedKind, selectedType) -> selectedKind == kind && selectedType in types }?.second
        ?: types.firstOrNull()
    val latest = type?.let { vm.latestJson[TypeKey(kind, it)] }
    val prefill = type?.let {
        when {
            latest != null -> ComposerPrefill(latest.value.pretty(), PrefillSource.Observed(latest.sequence))
            role.sealedSubTypes != null -> ComposerPrefill("{\"#type\": ${JsonPrimitive(it)}}", PrefillSource.SealedTemplate)
            else -> ComposerPrefill("{}", PrefillSource.EmptyObject)
        }
    }
    val signalNote = if (kind != VIEObjectKind.Signal || type == null) null else when (vm.signalDelivery[type]) {
        VIESignalDelivery.Effect -> SignalNote.Effect
        VIESignalDelivery.Event -> null
        null -> SignalNote.Unknown
    }
    return ComposerTarget(
        block = block,
        kind = kind,
        enabledKinds = VIEObjectKind.entries.filterTo(mutableSetOf()) { info.role(it).isSerializable },
        types = types,
        type = type,
        prefill = prefill,
        signalNote = signalNote,
    )
}

/** The editor's text, parsed. */
sealed interface JsonParse {
    data class Valid(val element: JsonElement) : JsonParse

    /** @property line Where it went wrong, from 1. */
    data class Invalid(val message: String, val line: Int, val column: Int) : JsonParse
}

private val OffsetRegex = Regex("""offset (\d+)""")

/** Parses the editor's [text], locating a syntax error by line and column. */
fun parseJson(text: String): JsonParse = try {
    JsonParse.Valid(Json.parseToJsonElement(text))
} catch (e: SerializationException) {
    val message = e.message.orEmpty()
    // An error at the end of the text (an unclosed object) names no offset.
    val offset = OffsetRegex.find(message)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, text.length) ?: text.length
    val before = text.substring(0, offset)
    JsonParse.Invalid(message.lineSequence().first(), line = before.count { it == '\n' } + 1, column = offset - before.lastIndexOf('\n'))
}
