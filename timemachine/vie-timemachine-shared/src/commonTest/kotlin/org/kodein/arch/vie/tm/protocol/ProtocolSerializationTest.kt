package org.kodein.arch.vie.tm.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    fun clientMessages() {
        assertRoundTrip<TimeMachineClientMessage>(
            TimeMachineClientMessage.Hello(TimeMachineSession(sessionId, "Demo App"))
        )
        assertRoundTrip<TimeMachineClientMessage>(
            TimeMachineClientMessage.ViewModelStarted(vmInfo, now)
        )
        assertRoundTrip<TimeMachineClientMessage>(
            TimeMachineClientMessage.ViewModelInfo(vmId, vmInfo)
        )
        assertRoundTrip<TimeMachineClientMessage>(
            TimeMachineClientMessage.ViewModelInfo(vmId, null)
        )
        assertRoundTrip<TimeMachineClientMessage>(
            TimeMachineClientMessage.ViewModelClosed(vmId, now)
        )
        assertRoundTrip<TimeMachineClientMessage>(
            TimeMachineClientMessage.Observed(
                VIEObservation(
                    viewModelId = vmId,
                    at = now,
                    kind = VIEObjectKind.Model,
                    typeName = "org.example.TaskListVIE.Model",
                    asString = "Model(tasks=[])",
                    asJson = payload,
                )
            )
        )
        assertRoundTrip<TimeMachineClientMessage>(
            TimeMachineClientMessage.Observed(
                VIEObservation(
                    viewModelId = vmId,
                    at = now,
                    kind = VIEObjectKind.Intent,
                    typeName = "org.example.TaskListVIE.Intent.Add",
                    asString = "Add(title=Buy milk)",
                    asJson = payload,
                    injected = true,
                )
            )
        )
        assertRoundTrip<TimeMachineClientMessage>(
            TimeMachineClientMessage.InterventionResult(requestId, VIEInterventionResult.Success)
        )
        assertRoundTrip<TimeMachineClientMessage>(
            TimeMachineClientMessage.InterventionResult(
                requestId,
                VIEInterventionResult.Failure(VIEInterventionResult.Reason.BadJson, "unexpected token"),
            )
        )
    }

    @Test
    fun serverMessages() {
        assertRoundTrip<TimeMachineServerMessage>(
            TimeMachineServerMessage.RequestViewModelInfo(vmId)
        )
        assertRoundTrip<TimeMachineServerMessage>(
            TimeMachineServerMessage.Intervene(
                requestId = requestId,
                viewModelId = vmId,
                kind = VIEObjectKind.Intent,
                typeName = "org.example.TaskListVIE.Intent.Add",
                asJson = payload,
            )
        )
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
