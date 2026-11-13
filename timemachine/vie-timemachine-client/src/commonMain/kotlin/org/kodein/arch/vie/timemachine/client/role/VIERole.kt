package org.kodein.arch.vie.timemachine.client.role

import kotlin.reflect.KType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializerOrNull
import org.kodein.arch.vie.timemachine.client.describing
import org.kodein.arch.vie.timemachine.protocol.TimeMachineJson
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult.Reason
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo


/**
 * [TimeMachineJson], minus `ignoreUnknownKeys`, for decoding the app's own objects. Protocol frames
 * skip unknown keys so peers of different versions can talk; an app object's JSON is edited by hand
 * in the inspector, and a mistyped key must fail rather than silently leave its field at the default.
 */
private val StrictJson: Json = Json(TimeMachineJson) { ignoreUnknownKeys = false }

/**
 * Captures the serial name a sealed serializer writes for a value, without encoding the value.
 * `SealedClassSerializer` resolves the concrete subtype, writes its serial name as element 0 of
 * [sealedDescriptor], then the value itself as element 1: this encoder keeps the former and drops
 * the latter, so the subtype's own serializer never runs and cannot fail.
 */
private class SerialNameCapture(private val sealedDescriptor: SerialDescriptor) : AbstractEncoder() {
    override val serializersModule: SerializersModule get() = TimeMachineJson.serializersModule

    var serialName: String? = null
        private set
    private var atSerialName = false

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        atSerialName = descriptor == sealedDescriptor && index == 0
        return true
    }

    override fun encodeString(value: String) {
        if (atSerialName) serialName = value
    }

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {}
}

/**
 * Introspection over one VIE role (`Model`, `Intent` or `Signal`): whether [T] is `@Serializable`,
 * its type name, and — for a sealed [T] — the names of its registered subtypes. Built once per role
 * via [of] and reused for every observation and intervention of that role.
 *
 * Everything a role knows about [T] is read by [of], when it is built: from then on, only [encode],
 * [decodeForIntervention] and [typeNameOf] run the app's serializers.
 *
 * Every name is a *serial name*: the fully qualified name unless `@SerialName` overrides it, so a
 * name can be a bare `circle` with no package at all. Only a type that is not `@Serializable` is
 * named by its class instead: fully qualified, except on web (js and wasmJs), where
 * `KClass.qualifiedName` is unsupported and the simple name stands in. This only affects the
 * display-only name of a role already shown disabled in the inspector: encode/decode never run
 * without a serializer.
 *
 * An open polymorphic type — a plain interface that is not `@Serializable`, or a `@Polymorphic` or
 * abstract one — counts as not `@Serializable`: its serializer needs a `SerializersModule` to
 * resolve subtypes, and the probe has none. A sealed type is unaffected: it lists its own subtypes.
 * A type whose serializer cannot be introspected counts as not `@Serializable` too (see [of]).
 *
 * @property sealedSubTypes `null` when [T] is not a sealed hierarchy; otherwise the serial names of
 *   its registered subtypes, recursively flattened to concrete leaves (an intermediate sealed subtype
 *   never appears itself — see `SealedClassSerializer`'s own contract).
 * @property classDiscriminator The key under which [T]'s JSON carries its concrete type: [T]'s own
 *   `@JsonClassDiscriminator` when it has one, [TimeMachineJson]'s otherwise — the same lookup
 *   kotlinx-serialization does when it encodes and decodes [T].
 */
internal class VIERole<T> private constructor(
    val typeName: String,
    val serializer: KSerializer<T>?,
    val sealedSubTypes: List<String>?,
    private val classDiscriminator: String,
) {

    /** Whether [T] is `@Serializable` and not open polymorphic, i.e. whether [serializer] is non-null. */
    val isSerializable: Boolean get() = serializer != null

    /** Snapshots this role's introspected info as the wire-transmissible [VIETypeInfo]. */
    fun toVIETypeInfo(): VIETypeInfo =
        VIETypeInfo(typeName = typeName, isSerializable = isSerializable, sealedSubTypes = sealedSubTypes)

    /** Encodes [value] to JSON, or `null` if [T] is not `@Serializable` (see [isSerializable]). */
    fun encode(value: T): JsonElement? {
        val s = serializer ?: return null
        return TimeMachineJson.encodeToJsonElement(s, value)
    }

    /**
     * Decodes [json] as [typeName], or reports why it cannot as the [VIEInterventionResult.Reason]
     * the inspector expects — not as an exception, since a `SerializationException` *is* an
     * `IllegalArgumentException`, and the reasons could not be told apart by type.
     *
     * - [T] is not `@Serializable`: [VIEInterventionResult.Reason.NotSerializable].
     * - [typeName] is none of [sealedSubTypes] (nor this role's own [typeName] when not sealed):
     *   [VIEInterventionResult.Reason.UnknownType].
     * - [json]'s own class discriminator names another type: [VIEInterventionResult.Reason.BadJson].
     *   kotlinx-serialization dispatches decoding purely on that embedded field, and would otherwise
     *   silently decode [json] as whatever type it actually encodes, ignoring [typeName].
     * - [json] does not decode: [VIEInterventionResult.Reason.BadJson]. Decoding is strict: a key
     *   [json] has but the type doesn't declare fails, rather than being dropped. So does anything the
     *   app's code throws while decoding, an `Error` or a `CancellationException` included.
     */
    fun decodeForIntervention(typeName: String, json: JsonElement): Decoded<T> {
        val s = serializer ?: return Decoded.Refused(Reason.NotSerializable, "${this.typeName} is not @Serializable")
        if (!isKnown(typeName)) return Decoded.Refused(Reason.UnknownType, unknownTypeMessage(typeName))
        val actualType = concreteTypeNameIn(json)
        if (actualType != null && actualType != typeName) return Decoded.Refused(Reason.BadJson, mismatchMessage(typeName, actualType))
        return try {
            Decoded.Value(StrictJson.decodeFromJsonElement(s, json))
        } catch (e: Throwable) {
            // A SerializationException, or whatever app code throws: a custom serializer rejecting the
            // payload, or one left as `TODO()`, a class's `init`… Errors and cancellation included, like
            // `describing` on the encoding side: this runs on the probe's own coroutine, which must not
            // end because of the app's code.
            Decoded.Refused(Reason.BadJson, e.message ?: e.toString())
        }
    }

    /** The outcome of [decodeForIntervention]. */
    sealed interface Decoded<out T> {
        class Value<T>(val value: T) : Decoded<T>
        class Refused(val reason: Reason, val message: String) : Decoded<Nothing> {
            val failure: VIEInterventionResult.Failure get() = VIEInterventionResult.Failure(reason, message)
        }
    }

    private fun isKnown(typeName: String): Boolean = sealedSubTypes?.contains(typeName) ?: (typeName == this.typeName)

    private fun unknownTypeMessage(typeName: String) = "Unknown type $typeName for role ${this.typeName}"

    private fun mismatchMessage(typeName: String, actualType: String?) =
        "typeName $typeName doesn't match json's own type $actualType for role ${this.typeName}"

    /** The concrete type of [value], as [decodeForIntervention] expects it back: for a sealed role, the serial
     *  name of [value]'s registered subtype — one of [sealedSubTypes], even when [value] itself
     *  fails to encode; this role's own [typeName] for any other serializable role. */
    fun typeNameOf(value: T & Any): String {
        if (sealedSubTypes != null) {
            return serialNameOf(value) ?: value::class.displayName ?: typeName
        }
        if (isSerializable) return typeName
        return value::class.displayName ?: typeName
    }

    /** The serial name [serializer] writes for [value] as its class discriminator, resolved without
     *  encoding [value] (see [SerialNameCapture]), or `null` if [serializer] does not write one the
     *  way `SealedClassSerializer` does. */
    private fun serialNameOf(value: T): String? {
        val s = serializer ?: return null
        val capture = SerialNameCapture(s.descriptor)
        return try {
            s.serialize(capture, value)
            capture.serialName
        } catch (e: Exception) {
            null
        }
    }

    /** The concrete type name carried by [json]'s class discriminator (see [classDiscriminator]),
     *  or `null` when [json] carries none (a non-sealed role's JSON). */
    private fun concreteTypeNameIn(json: JsonElement?): String? =
        ((json as? JsonObject)?.get(classDiscriminator) as? JsonPrimitive)?.contentOrNull

    companion object {
        /**
         * Builds a [VIERole] for [type], which must be [T]'s own type, introspecting its serializer,
         * type name, and (if sealed) its subtypes.
         *
         * An open polymorphic serializer counts as none: kotlinx-serialization hands one out for any
         * interface, `@Serializable` or not, and it can only resolve subtypes registered in a
         * `SerializersModule` — which [TimeMachineJson] has none of, so it could never encode a value.
         *
         * Never throws. Introspecting [type] runs the app's own code — a custom serializer's
         * `descriptor`, and for a sealed type the descriptor of every subtype — on the thread that
         * builds the ViewModel, inside its constructor. Whatever that throws, an `Error` included, is
         * printed, and [type] then counts as not `@Serializable`: its objects are still observed, only
         * without JSON. So does a type kotlinx-serialization refuses to look up, such as one with a
         * star projection.
         */
        fun <T> of(type: KType): VIERole<T> =
            describing({ "introspect $type, reported without JSON" }) { introspect<T>(type) } ?: notSerializable(type)

        /** The role of [type], or `null` if it has no serializer the probe can use. Runs app code, and
         *  throws whatever it throws. */
        @Suppress("UNCHECKED_CAST")
        private fun <T> introspect(type: KType): VIERole<T>? {
            val serializer = (serializerOrNull(type) as KSerializer<T>?) ?: return null
            val descriptor = serializer.descriptor
            if (descriptor.kind == PolymorphicKind.OPEN) return null
            return VIERole(
                typeName = descriptor.serialName,
                serializer = serializer,
                sealedSubTypes = descriptor
                    .takeIf { it.kind == PolymorphicKind.SEALED }
                    ?.getElementDescriptor(1)
                    ?.elementNames
                    ?.toList(),
                classDiscriminator = descriptor.annotations
                    .filterIsInstance<JsonClassDiscriminator>()
                    .firstOrNull()
                    ?.discriminator
                    ?: TimeMachineJson.configuration.classDiscriminator,
            )
        }

        private fun <T> notSerializable(type: KType): VIERole<T> = VIERole(
            typeName = type.toString(),
            serializer = null,
            sealedSubTypes = null,
            classDiscriminator = TimeMachineJson.configuration.classDiscriminator,
        )
    }
}
