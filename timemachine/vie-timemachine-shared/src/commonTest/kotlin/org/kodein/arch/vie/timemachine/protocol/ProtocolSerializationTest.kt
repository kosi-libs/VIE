package org.kodein.arch.vie.timemachine.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement


class ProtocolSerializationTest {

    private val vmId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val sessionId = Uuid.parse("00000000-0000-0000-0000-0000000000ff")
    private val requestId = Uuid.parse("00000000-0000-0000-0000-00000000000a")
    private val now = Instant.fromEpochMilliseconds(1_725_000_000_000)

    private val payload = JsonObject(
        mapOf(
            "title" to JsonPrimitive("Buy milk"),
            "done" to JsonPrimitive(false),
            "tags" to JsonArray(listOf(JsonPrimitive("home"), JsonPrimitive("urgent"))),
        )
    )

    private val vmInfo = VIEViewModelInfo(
        id = vmId,
        typeName = "org.example.TaskListVM",
        model = VIETypeInfo("org.example.TaskListVIE.Model", isSerializable = true),
        intent = VIETypeInfo(
            "org.example.TaskListVIE.Intent",
            isSerializable = true,
            sealedSubTypes = listOf("org.example.TaskListVIE.Intent.Add", "org.example.TaskListVIE.Intent.Remove"),
        ),
        signal = VIETypeInfo(
            "org.example.TaskListVIE.Signal",
            isSerializable = false,
            sealedSubTypes = emptyList(),
        ),
    )

    private inline fun <reified T> assertRoundTrip(value: T) {
        val element = TimeMachineJson.encodeToJsonElement(value)
        assertEquals(value, TimeMachineJson.decodeFromJsonElement<T>(element))
    }

    @Test
    fun helloRoundTrips() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.Hello(
            TimeMachineSession(sessionId, "Demo App"),
            protocolVersion = 1,
        )
    )

    @Test
    fun viewModelStartedRoundTrips() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.ViewModelStarted(vmInfo, now, sequence = TimeMachineSequence(1))
    )

    @Test
    fun viewModelInfoRoundTrips() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.ViewModelInfo(vmId, vmInfo)
    )

    @Test
    fun viewModelInfoRoundTripsWhenUnknown() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.ViewModelInfo(vmId, null)
    )

    @Test
    fun viewModelClosedRoundTrips() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.ViewModelClosed(vmId, now, sequence = TimeMachineSequence(2))
    )

    @Test
    fun observedRoundTrips() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.Observed(
            VIEObservation(
                viewModelId = vmId,
                at = now,
                kind = VIEObjectKind.Model,
                typeName = "org.example.TaskListVIE.Model",
                asString = "Model(tasks=[])",
                asJson = payload,
            ),
            sequence = TimeMachineSequence(3),
        )
    )

    @Test
    fun observedRoundTripsAsAnEffectSignal() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.Observed(
            VIEObservation(
                viewModelId = vmId,
                at = now,
                kind = VIEObjectKind.Signal,
                typeName = "org.example.TaskListVIE.Signal.GoBack",
                asString = "GoBack",
                delivery = VIESignalDelivery.Effect,
            ),
            sequence = TimeMachineSequence(5),
        )
    )

    @Test
    fun observedRoundTripsAsAnEventSignal() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.Observed(
            VIEObservation(
                viewModelId = vmId,
                at = now,
                kind = VIEObjectKind.Signal,
                typeName = "org.example.TaskListVIE.Signal.Saved",
                asString = "Saved",
                delivery = VIESignalDelivery.Event,
            ),
            sequence = TimeMachineSequence(6),
        )
    )

    @Test
    fun observedRoundTripsWhenIntervened() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.Observed(
            VIEObservation(
                viewModelId = vmId,
                at = now,
                kind = VIEObjectKind.Intent,
                typeName = "org.example.TaskListVIE.Intent.Add",
                asString = "Add(title=Buy milk)",
                asJson = payload,
                interventionId = requestId,
            ),
            sequence = TimeMachineSequence(4),
        )
    )

    @Test
    fun interventionResultRoundTripsOnSuccess() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.InterventionResult(requestId, VIEInterventionResult.Success)
    )

    @Test
    fun interventionResultRoundTripsOnFailure() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.InterventionResult(
            requestId,
            VIEInterventionResult.Failure(VIEInterventionResult.Reason.BadJson, "unexpected token"),
        )
    )

    @Test
    fun interventionResultRoundTripsOnFailureWithoutAMessage() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.InterventionResult(requestId, VIEInterventionResult.Failure(VIEInterventionResult.Reason.UnknownType)),
    )

    @Test
    fun nullDefaultsAreWrittenOnTheWire() {
        assertEquals(
            Json.parseToJsonElement("""{"#type":"failure","reason":"BadJson","message":null}"""),
            Json.parseToJsonElement(
                TimeMachineJson.encodeToString<VIEInterventionResult>(VIEInterventionResult.Failure(VIEInterventionResult.Reason.BadJson))
            ),
        )
        assertEquals(
            Json.parseToJsonElement("""{"typeName":"T","isSerializable":true,"sealedSubTypes":null}"""),
            Json.parseToJsonElement(TimeMachineJson.encodeToString(VIETypeInfo("T", isSerializable = true))),
        )
    }

    @Test
    fun observationsDroppedRoundTrips() = assertRoundTrip<TimeMachineClientMessage>(
        TimeMachineClientMessage.ObservationsDropped(vmId, count = 3, at = now, sequence = TimeMachineSequence(5))
    )

    @Test
    fun welcomeRoundTrips() = assertRoundTrip<TimeMachineServerMessage>(
        TimeMachineServerMessage.Welcome
    )

    @Test
    fun rejectedRoundTrips() = assertRoundTrip<TimeMachineServerMessage>(
        TimeMachineServerMessage.Rejected(protocolVersion = 1)
    )

    @Test
    fun aRejectedWithKeysThisVersionDoesNotKnowStillDecodes() = assertEquals(
        TimeMachineServerMessage.Rejected(protocolVersion = 2),
        TimeMachineJson.decodeFromString<TimeMachineServerMessage>(
            """{"#type":"rejected","reason":"SomethingNew","protocolVersion":2}"""
        ),
    )

    private val helloLiteral =
        """{"#type":"hello","protocolVersion":1,"session":{"id":"00000000-0000-0000-0000-0000000000ff","name":"Demo App"}}"""

    private val rejectedLiteral = """{"#type":"rejected","protocolVersion":1}"""

    private val hello = TimeMachineClientMessage.Hello(TimeMachineSession(sessionId, "Demo App"), 1)

    private val rejected = TimeMachineServerMessage.Rejected(1)

    @Test
    fun helloEncodesAsItsFrozenEnvelope() = assertEquals(
        Json.parseToJsonElement(helloLiteral),
        Json.parseToJsonElement(TimeMachineJson.encodeToString<TimeMachineClientMessage>(hello)),
    )

    @Test
    fun helloDecodesFromItsFrozenEnvelope() =
        assertEquals(hello, TimeMachineJson.decodeFromString<TimeMachineClientMessage>(helloLiteral))

    @Test
    fun aHelloWithExtraTopLevelAndNestedKeysStillDecodes() = assertEquals(
        hello,
        TimeMachineJson.decodeFromString<TimeMachineClientMessage>(
            """{"#type":"hello","protocolVersion":1,"later":true,"session":{"id":"00000000-0000-0000-0000-0000000000ff","name":"Demo App","tags":[1]}}"""
        ),
    )

    @Test
    fun rejectedEncodesAsItsFrozenEnvelope() = assertEquals(
        Json.parseToJsonElement(rejectedLiteral),
        Json.parseToJsonElement(TimeMachineJson.encodeToString<TimeMachineServerMessage>(rejected)),
    )

    @Test
    fun rejectedDecodesFromItsFrozenEnvelope() =
        assertEquals(rejected, TimeMachineJson.decodeFromString<TimeMachineServerMessage>(rejectedLiteral))

    @Test
    fun receivedRoundTrips() = assertRoundTrip<TimeMachineServerMessage>(
        TimeMachineServerMessage.Received(sequence = TimeMachineSequence(6))
    )

    @Test
    fun requestViewModelInfoRoundTrips() = assertRoundTrip<TimeMachineServerMessage>(
        TimeMachineServerMessage.RequestViewModelInfo(vmId)
    )

    @Test
    fun interveneRoundTrips() = assertRoundTrip<TimeMachineServerMessage>(
        TimeMachineServerMessage.Intervene(
            requestId = requestId,
            viewModelId = vmId,
            kind = VIEObjectKind.Intent,
            typeName = "org.example.TaskListVIE.Intent.Add",
            asJson = payload,
        )
    )

    @Test
    fun aTimelineMessageExposesItsSequenceThroughTheInterface() {
        val message: TimeMachineTimelineMessage = TimeMachineClientMessage.ViewModelClosed(vmId, now, sequence = TimeMachineSequence(2))

        assertEquals(TimeMachineSequence(2), message.sequence)
    }

    @Test
    fun typeInfoDistinguishesSealedFromNonSealed() {
        val notSealed = VIETypeInfo("org.example.Model", isSerializable = true)
        val sealedNoSubtypes = VIETypeInfo("org.example.Signal", isSerializable = true, sealedSubTypes = emptyList())

        assertEquals(null, notSealed.sealedSubTypes)
        assertEquals(emptyList(), sealedNoSubtypes.sealedSubTypes)
        assertRoundTrip(notSealed)
        assertRoundTrip(sealedNoSubtypes)
    }
}
