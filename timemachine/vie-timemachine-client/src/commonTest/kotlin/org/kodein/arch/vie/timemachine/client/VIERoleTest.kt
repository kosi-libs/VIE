package org.kodein.arch.vie.timemachine.client

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

class VIERoleTest {

    @Test
    fun nonSealedSerializableRoleReportsItsFqnAndNoSealedSubTypes() {
        val role = role<SimpleModel>()

        assertTrue(role.isSerializable)
        assertEquals(requireNotNull(role.serializer).descriptor.serialName, role.typeName)
        assertNull(role.sealedSubTypes)
    }

    @Test
    fun nonSerializableRoleReportsNotSerializable() {
        val role = role<NotSerializable>()

        assertFalse(role.isSerializable)
        assertNull(role.sealedSubTypes)
        assertNull(role.encode(NotSerializable()))
    }

    @Test
    fun plainInterfaceRoleReportsNotSerializable() {
        val role = role<PlainIntent>()

        assertFalse(role.isSerializable)
        assertNull(role.serializer)
        assertNull(role.sealedSubTypes)
        assertNull(role.encode(PlainIntent.A))
        assertTrue(role.typeName.endsWith("PlainIntent"), role.typeName)
    }

    @Test
    fun sealedRoleReportsItsSubTypes() {
        val role = role<TestIntent>()

        assertTrue(role.isSerializable)
        assertEquals(
            setOf(
                "org.kodein.arch.vie.timemachine.client.TestIntent.Add",
                "org.kodein.arch.vie.timemachine.client.TestIntent.Remove",
            ),
            role.sealedSubTypes?.toSet(),
        )
    }

    @Test
    fun nestedSealedHierarchyFlattensToLeafTypesOnly() {
        val role = role<NestedRoot>()
        val descriptor = requireNotNull(role.serializer).descriptor

        assertEquals(2, descriptor.elementsCount)
        assertEquals("type", descriptor.getElementName(0))
        assertEquals(
            setOf(
                "org.kodein.arch.vie.timemachine.client.NestedRoot.Branch.Leaf",
                "org.kodein.arch.vie.timemachine.client.NestedRoot.Other",
            ),
            role.sealedSubTypes?.toSet(),
        )
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val role = role<SimpleModel>()

        val json = requireNotNull(role.encode(SimpleModel(42)))

        assertEquals(SimpleModel(42), role.decodeForIntervention(role.typeName, json).value())
    }

    @Test
    fun sealedSubTypeWithATypePropertyRoundTrips() {
        val role = role<FilterIntent>()
        val json = requireNotNull(role.encode(FilterIntent.SetFilter("done")))
        val typeName = "org.kodein.arch.vie.timemachine.client.FilterIntent.SetFilter"

        assertEquals(typeName, (json as JsonObject).getValue("#type").jsonPrimitive.content)
        assertEquals("done", json.getValue("type").jsonPrimitive.content)
        assertEquals(typeName, role.typeNameOf(FilterIntent.SetFilter("done")))
        assertEquals(FilterIntent.SetFilter("done"), role.decodeForIntervention(typeName, json).value())
    }

    @Test
    fun sealedValueIsNamedBySerialNameWithoutEncoding() {
        val role = role<TestIntent>()

        assertEquals("org.kodein.arch.vie.timemachine.client.TestIntent.Add", role.typeNameOf(TestIntent.Add("hi")))
    }

    @Test
    fun nonSealedSerializableValueIsNamedByItsRole() {
        val role = role<SimpleModel>()

        assertEquals(role.typeName, role.typeNameOf(SimpleModel(1)))
    }

    @Test
    fun nonSerializableValueIsNamedByItsClass() {
        val role = role<NotSerializable>()

        val typeName = role.typeNameOf(NotSerializable())

        assertTrue(typeName.endsWith("NotSerializable"), typeName)
    }

    @Test
    fun plainInterfaceValueIsNamedByItsClass() {
        val role = role<PlainIntent>()

        val typeName = role.typeNameOf(PlainIntent.A)

        assertTrue(typeName.endsWith("A"), typeName)
    }

    @Test
    fun sealedRoleWithACustomDiscriminatorNamesValuesBySerialName() {
        val role = role<ShapeIntent>()
        val json = requireNotNull(role.encode(ShapeIntent.Circle(3)))

        assertEquals("circle", (json as JsonObject).getValue("kind").jsonPrimitive.content)
        assertEquals("circle", role.typeNameOf(ShapeIntent.Circle(3)))
        assertTrue("circle" in requireNotNull(role.sealedSubTypes))
    }

    @Test
    fun decodeForInterventionRefusesAMismatchedTypeNameUnderACustomDiscriminator() {
        val role = role<ShapeIntent>()
        val json = requireNotNull(role.encode(ShapeIntent.Circle(3)))

        assertEquals(VIEInterventionResult.Reason.BadJson, role.decodeForIntervention("square", json).reason())
    }

    @Test
    fun sealedRoleWithACustomDiscriminatorRoundTrips() {
        val role = role<ShapeIntent>()
        val json = requireNotNull(role.encode(ShapeIntent.Circle(3)))

        assertEquals(ShapeIntent.Circle(3), role.decodeForIntervention("circle", json).value())
    }

    private fun <T> VIERole.Decoded<T>.value(): T = assertIs<VIERole.Decoded.Value<T>>(this).value

    private fun <T> VIERole.Decoded<T>.reason(): VIEInterventionResult.Reason? = (this as? VIERole.Decoded.Refused)?.reason

    @Test
    fun decodeForInterventionDecodesAKnownSubType() {
        val role = role<TestIntent>()
        val json = requireNotNull(role.encode(TestIntent.Add("hi")))

        val decoded = role.decodeForIntervention("org.kodein.arch.vie.timemachine.client.TestIntent.Add", json)

        assertEquals(TestIntent.Add("hi"), decoded.value())
    }

    @Test
    fun decodeForInterventionRefusesAPayloadWhoseSerializerThrowsACancellation() {
        val role = role<Cancelling>()

        assertEquals(VIEInterventionResult.Reason.BadJson, role.decodeForIntervention(role.typeName, JsonPrimitive("x")).reason())
    }

    @Test
    fun decodeForInterventionRefusesANonSerializableRole() {
        val role = role<NotSerializable>()

        assertEquals(VIEInterventionResult.Reason.NotSerializable, role.decodeForIntervention(role.typeName, JsonNull).reason())
    }

    @Test
    fun decodeForInterventionRefusesAPlainInterfaceRole() {
        val role = role<PlainIntent>()

        assertEquals(VIEInterventionResult.Reason.NotSerializable, role.decodeForIntervention(role.typeName, JsonNull).reason())
    }

    @Test
    fun decodeForInterventionRefusesAnUnknownTypeName() {
        val role = role<TestIntent>()
        val json = requireNotNull(role.encode(TestIntent.Add("hi")))

        assertEquals(VIEInterventionResult.Reason.UnknownType, role.decodeForIntervention("org.example.NotARegisteredType", json).reason())
    }

    @Test
    fun decodeForInterventionRefusesJsonOfAnotherSubTypeAsBadJson() {
        val role = role<TestIntent>()
        val json = requireNotNull(role.encode(TestIntent.Add("hi")))

        val decoded = role.decodeForIntervention("org.kodein.arch.vie.timemachine.client.TestIntent.Remove", json)

        assertEquals(VIEInterventionResult.Reason.BadJson, decoded.reason())
    }

    @Test
    fun decodeForInterventionRefusesAMistypedKeyAsBadJson() {
        val role = role<SimpleModel>()
        val json = buildJsonObject {
            put("count", 1)
            put("cuont", 2)
        }

        assertEquals(VIEInterventionResult.Reason.BadJson, role.decodeForIntervention(role.typeName, json).reason())
    }
}
