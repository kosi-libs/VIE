package org.kodein.arch.vie.timemachine.client.type

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.kodein.arch.vie.timemachine.client.fixtures.objectType
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult


private class NotSerializable

/** Not `@Serializable`: kotlinx-serialization still hands out an open polymorphic serializer for it. */
private sealed interface PlainIntent {
    data object A : PlainIntent
}

/** Its serializer throws a `CancellationException`, as app code reached from a custom serializer might. */
@Serializable(with = CancellingSerializer::class)
private class Cancelling

private object CancellingSerializer : KSerializer<Cancelling> {
    override val descriptor = PrimitiveSerialDescriptor("Cancelling", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Cancelling) = error("not needed")
    override fun deserialize(decoder: Decoder): Cancelling = throw CancellationException("not the caller's cancellation")
}

/** Its serializer throws an `Error`, as a custom serializer left as `TODO()` for a type the app only encodes does. */
@Serializable(with = UnimplementedSerializer::class)
private class Unimplemented

private object UnimplementedSerializer : KSerializer<Unimplemented> {
    override val descriptor = PrimitiveSerialDescriptor("Unimplemented", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Unimplemented) = error("not needed")
    override fun deserialize(decoder: Decoder): Unimplemented = TODO()
}

/** Reading its serializer's descriptor throws, as a custom serializer's own code might. */
@Serializable(with = ThrowingDescriptorSerializer::class)
private class ThrowingDescriptor

private object ThrowingDescriptorSerializer : KSerializer<ThrowingDescriptor> {
    override val descriptor: SerialDescriptor get() = error("descriptor boom")
    override fun serialize(encoder: Encoder, value: ThrowingDescriptor) = error("not needed")
    override fun deserialize(decoder: Decoder): ThrowingDescriptor = error("not needed")
}

/** Reading its serializer's descriptor throws an `Error`, as one left as `TODO()` does. */
@Serializable(with = UnimplementedDescriptorSerializer::class)
private class UnimplementedDescriptor

private object UnimplementedDescriptorSerializer : KSerializer<UnimplementedDescriptor> {
    override val descriptor: SerialDescriptor get() = TODO()
    override fun serialize(encoder: Encoder, value: UnimplementedDescriptor) = error("not needed")
    override fun deserialize(decoder: Decoder): UnimplementedDescriptor = error("not needed")
}

/** Its descriptor says it is sealed, and throws an `Error` once asked for its subtypes. */
@Serializable(with = HalfSealedSerializer::class)
private class HalfSealed

@OptIn(SealedSerializationApi::class)
private object HalfSealedDescriptor : SerialDescriptor {
    override val serialName: String = "HalfSealed"
    override val kind: SerialKind = PolymorphicKind.SEALED
    override val elementsCount: Int = 2
    override fun getElementName(index: Int): String = TODO()
    override fun getElementIndex(name: String): Int = TODO()
    override fun getElementAnnotations(index: Int): List<Annotation> = TODO()
    override fun getElementDescriptor(index: Int): SerialDescriptor = TODO()
    override fun isElementOptional(index: Int): Boolean = TODO()
}

private object HalfSealedSerializer : KSerializer<HalfSealed> {
    override val descriptor: SerialDescriptor = HalfSealedDescriptor
    override fun serialize(encoder: Encoder, value: HalfSealed) = error("not needed")
    override fun deserialize(decoder: Decoder): HalfSealed = error("not needed")
}

/** `Box<*>` has no serializer to look up: kotlinx-serialization throws on a star projection. */
@Serializable
private class Box<T>(val value: T)

@Serializable
private data class SimpleModel(val count: Int)

@Serializable
private sealed interface TestIntent {
    @Serializable
    data class Add(val title: String) : TestIntent

    @Serializable
    data class Remove(val id: Int) : TestIntent
}

@Serializable
private sealed interface FilterIntent {
    @Serializable
    data class SetFilter(val type: String) : FilterIntent
}

@Serializable
private sealed interface NestedRoot {
    @Serializable
    sealed interface Branch : NestedRoot {
        @Serializable
        data class Leaf(val x: Int) : Branch
    }

    @Serializable
    data class Other(val y: Int) : NestedRoot
}

@Serializable
@JsonClassDiscriminator("kind")
private sealed interface ShapeIntent {
    @Serializable
    @SerialName("circle")
    data class Circle(val radius: Int) : ShapeIntent

    @Serializable
    @SerialName("square")
    data class Square(val side: Int) : ShapeIntent
}

class VIEObjectTypeTest {

    @Test
    fun nonSealedSerializableTypeReportsItsFqnAndNoSealedSubTypes() {
        val type = objectType<SimpleModel>()

        assertTrue(type.isSerializable)
        assertEquals(requireNotNull(type.serializer).descriptor.serialName, type.typeName)
        assertNull(type.sealedSubTypes)
    }

    @Test
    fun nonSerializableTypeReportsNotSerializable() {
        val type = objectType<NotSerializable>()

        assertFalse(type.isSerializable)
        assertNull(type.sealedSubTypes)
        assertNull(type.encode(NotSerializable()))
    }

    @Test
    fun plainInterfaceTypeReportsNotSerializable() {
        val type = objectType<PlainIntent>()

        assertFalse(type.isSerializable)
        assertNull(type.serializer)
        assertNull(type.sealedSubTypes)
        assertNull(type.encode(PlainIntent.A))
        assertTrue(type.typeName.endsWith("PlainIntent"), type.typeName)
    }

    @Test
    fun aTypeWhoseDescriptorThrowsIsNotSerializable() {
        val type = objectType<ThrowingDescriptor>()

        assertNotIntrospected(type)
        assertTrue(type.typeName.endsWith("ThrowingDescriptor"), type.typeName)
    }

    @Test
    fun aTypeWhoseDescriptorThrowsAnErrorIsNotSerializable() {
        val type = objectType<UnimplementedDescriptor>()

        assertNotIntrospected(type)
        assertTrue(type.typeName.endsWith("UnimplementedDescriptor"), type.typeName)
    }

    @Test
    fun aTypeWhoseSealedSubTypesThrowAnErrorIsNotSerializable() {
        val type = objectType<HalfSealed>()

        assertNotIntrospected(type)
        assertTrue(type.typeName.endsWith("HalfSealed"), type.typeName)
    }

    @Test
    fun aStarProjectedTypeIsNotSerializable() {
        assertNotIntrospected(objectType<Box<*>>())
    }

    /** [type] fell back to one without a serializer, and nothing it is asked afterwards throws. */
    private fun assertNotIntrospected(type: VIEObjectType<*>) {
        assertFalse(type.isSerializable)
        assertNull(type.serializer)
        assertNull(type.sealedSubTypes)
        val info = type.toVIETypeInfo()
        assertEquals(type.typeName, info.typeName)
        assertFalse(info.isSerializable)
        assertNull(info.sealedSubTypes)
        assertEquals(VIEInterventionResult.Reason.NotSerializable, type.decodeForIntervention(type.typeName, JsonNull).reason())
    }

    @Test
    fun sealedTypeReportsItsSubTypes() {
        val type = objectType<TestIntent>()

        assertTrue(type.isSerializable)
        assertEquals(
            setOf(
                "org.kodein.arch.vie.timemachine.client.type.TestIntent.Add",
                "org.kodein.arch.vie.timemachine.client.type.TestIntent.Remove",
            ),
            type.sealedSubTypes?.toSet(),
        )
    }

    @Test
    fun nestedSealedHierarchyFlattensToLeafTypesOnly() {
        val type = objectType<NestedRoot>()
        val descriptor = requireNotNull(type.serializer).descriptor

        assertEquals(2, descriptor.elementsCount)
        assertEquals("type", descriptor.getElementName(0))
        assertEquals(
            setOf(
                "org.kodein.arch.vie.timemachine.client.type.NestedRoot.Branch.Leaf",
                "org.kodein.arch.vie.timemachine.client.type.NestedRoot.Other",
            ),
            type.sealedSubTypes?.toSet(),
        )
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val type = objectType<SimpleModel>()

        val json = requireNotNull(type.encode(SimpleModel(42)))

        assertEquals(SimpleModel(42), type.decodeForIntervention(type.typeName, json).value())
    }

    @Test
    fun sealedSubTypeWithATypePropertyRoundTrips() {
        val type = objectType<FilterIntent>()
        val json = requireNotNull(type.encode(FilterIntent.SetFilter("done")))
        val typeName = "org.kodein.arch.vie.timemachine.client.type.FilterIntent.SetFilter"

        assertEquals(typeName, (json as JsonObject).getValue("#type").jsonPrimitive.content)
        assertEquals("done", json.getValue("type").jsonPrimitive.content)
        assertEquals(typeName, type.typeNameOf(FilterIntent.SetFilter("done")))
        assertEquals(FilterIntent.SetFilter("done"), type.decodeForIntervention(typeName, json).value())
    }

    @Test
    fun sealedValueIsNamedBySerialNameWithoutEncoding() {
        val type = objectType<TestIntent>()

        assertEquals("org.kodein.arch.vie.timemachine.client.type.TestIntent.Add", type.typeNameOf(TestIntent.Add("hi")))
    }

    @Test
    fun nonSealedSerializableValueIsNamedByItsType() {
        val type = objectType<SimpleModel>()

        assertEquals(type.typeName, type.typeNameOf(SimpleModel(1)))
    }

    @Test
    fun nonSerializableValueIsNamedByItsClass() {
        val type = objectType<NotSerializable>()

        val typeName = type.typeNameOf(NotSerializable())

        assertTrue(typeName.endsWith("NotSerializable"), typeName)
    }

    @Test
    fun plainInterfaceValueIsNamedByItsClass() {
        val type = objectType<PlainIntent>()

        val typeName = type.typeNameOf(PlainIntent.A)

        assertTrue(typeName.endsWith("A"), typeName)
    }

    @Test
    fun sealedTypeWithACustomDiscriminatorNamesValuesBySerialName() {
        val type = objectType<ShapeIntent>()
        val json = requireNotNull(type.encode(ShapeIntent.Circle(3)))

        assertEquals("circle", (json as JsonObject).getValue("kind").jsonPrimitive.content)
        assertEquals("circle", type.typeNameOf(ShapeIntent.Circle(3)))
        assertTrue("circle" in requireNotNull(type.sealedSubTypes))
    }

    @Test
    fun decodeForInterventionRefusesAMismatchedTypeNameUnderACustomDiscriminator() {
        val type = objectType<ShapeIntent>()
        val json = requireNotNull(type.encode(ShapeIntent.Circle(3)))

        assertEquals(VIEInterventionResult.Reason.BadJson, type.decodeForIntervention("square", json).reason())
    }

    @Test
    fun sealedTypeWithACustomDiscriminatorRoundTrips() {
        val type = objectType<ShapeIntent>()
        val json = requireNotNull(type.encode(ShapeIntent.Circle(3)))

        assertEquals(ShapeIntent.Circle(3), type.decodeForIntervention("circle", json).value())
    }

    private fun <T> VIEObjectType.Decoded<T>.value(): T = assertIs<VIEObjectType.Decoded.Value<T>>(this).value

    private fun <T> VIEObjectType.Decoded<T>.reason(): VIEInterventionResult.Reason? = (this as? VIEObjectType.Decoded.Refused)?.reason

    @Test
    fun decodeForInterventionDecodesAKnownSubType() {
        val type = objectType<TestIntent>()
        val json = requireNotNull(type.encode(TestIntent.Add("hi")))

        val decoded = type.decodeForIntervention("org.kodein.arch.vie.timemachine.client.type.TestIntent.Add", json)

        assertEquals(TestIntent.Add("hi"), decoded.value())
    }

    @Test
    fun decodeForInterventionRefusesAPayloadWhoseSerializerThrowsACancellation() {
        val type = objectType<Cancelling>()

        assertEquals(VIEInterventionResult.Reason.BadJson, type.decodeForIntervention(type.typeName, JsonPrimitive("x")).reason())
    }

    @Test
    fun decodeForInterventionRefusesAPayloadWhoseSerializerThrowsAnError() {
        val type = objectType<Unimplemented>()

        assertEquals(VIEInterventionResult.Reason.BadJson, type.decodeForIntervention(type.typeName, JsonPrimitive("x")).reason())
    }

    @Test
    fun decodeForInterventionRefusesANonSerializableType() {
        val type = objectType<NotSerializable>()

        assertEquals(VIEInterventionResult.Reason.NotSerializable, type.decodeForIntervention(type.typeName, JsonNull).reason())
    }

    @Test
    fun decodeForInterventionRefusesAPlainInterfaceType() {
        val type = objectType<PlainIntent>()

        assertEquals(VIEInterventionResult.Reason.NotSerializable, type.decodeForIntervention(type.typeName, JsonNull).reason())
    }

    @Test
    fun decodeForInterventionRefusesAnUnknownTypeName() {
        val type = objectType<TestIntent>()
        val json = requireNotNull(type.encode(TestIntent.Add("hi")))

        assertEquals(VIEInterventionResult.Reason.UnknownType, type.decodeForIntervention("org.example.NotARegisteredType", json).reason())
    }

    @Test
    fun decodeForInterventionRefusesJsonOfAnotherSubTypeAsBadJson() {
        val type = objectType<TestIntent>()
        val json = requireNotNull(type.encode(TestIntent.Add("hi")))

        val decoded = type.decodeForIntervention("org.kodein.arch.vie.timemachine.client.type.TestIntent.Remove", json)

        assertEquals(VIEInterventionResult.Reason.BadJson, decoded.reason())
    }

    @Test
    fun decodeForInterventionRefusesAMistypedKeyAsBadJson() {
        val type = objectType<SimpleModel>()
        val json = buildJsonObject {
            put("count", 1)
            put("cuont", 2)
        }

        assertEquals(VIEInterventionResult.Reason.BadJson, type.decodeForIntervention(type.typeName, json).reason())
    }
}
