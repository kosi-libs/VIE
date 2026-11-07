package org.kodein.arch.vie.timemachine.server.fixtures

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo
import org.kodein.arch.vie.timemachine.server.store.ConnectionId
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent


/**
 * The design's sample data: a to-do app, `Demo`, with the sequences the design cites. Its
 * numbering is contiguous from 200, but for the 216–219 that never arrive, because the store opens a
 * "possibly incomplete" marker at any sequence it did not receive.
 */
internal object Demo {

    /** The fixed instant the sample data is relative to: a clock frozen on it keeps every sparkline the same. */
    val now: Instant = Instant.parse("2026-10-01T14:02:30Z")

    /** [offsetMillis] after 14:02:00. */
    fun at(offsetMillis: Long): Instant = Instant.parse("2026-10-01T14:02:00Z") + offsetMillis.milliseconds

    // Sessions

    val session = TimeMachineSession(Uuid.parse("3f2a0000-0000-4000-8000-00000000c91e"), "Demo")
    val oldSession = TimeMachineSession(Uuid.parse("7b10e4d2-0000-4000-8000-0000000051a7"), "Demo")
    val connection = ConnectionId(1)
    val oldConnection = ConnectionId(2)

    // ViewModels

    val taskList = Uuid.parse("a1f40000-0000-4000-8000-000000000001")
    val editTask = Uuid.parse("b2e50000-0000-4000-8000-000000000002")
    val editTaskClosed = Uuid.parse("b2e60000-0000-4000-8000-000000000003")
    val settings = Uuid.parse("c3d70000-0000-4000-8000-000000000004")
    val oldTaskList = Uuid.parse("a1f40000-0000-4000-8000-000000000005")

    /** A ViewModel the inspector never got the description of. */
    val unknown = Uuid.parse("d4c80000-0000-4000-8000-000000000006")

    /** A ViewModel described, but with no entry yet. */
    val silent = Uuid.parse("e5b90000-0000-4000-8000-000000000007")

    // Interventions asked before the scenario starts, the first one's row evicted

    val request1 = Uuid.parse("00000000-0000-4000-8000-0000000000a1")
    val request2 = Uuid.parse("00000000-0000-4000-8000-0000000000a2")
    val request3 = Uuid.parse("00000000-0000-4000-8000-0000000000a3")
    val request4 = Uuid.parse("00000000-0000-4000-8000-0000000000a4")

    /** The one the `compNotSent` scenario starts from. */
    val requestNotSent = Uuid.parse("00000000-0000-4000-8000-0000000000a5")

    /** The intervention [ExtraIntervention] adds to the base world: the latest of TaskListVM. */
    val requestLatest = Uuid.parse("00000000-0000-4000-8000-0000000000a6")

    /** The first sequence after the base timeline: where the fake probe's own rows go on. */
    const val NextSequence: Long = 272

    // Types

    private const val Vie = "org.kodein.arch.vie.demo.vie"
    const val TaskListModel = "$Vie.TaskListVIE.Model"
    const val TaskListIntent = "$Vie.TaskListVIE.Intent"
    const val DeleteTask = "$Vie.TaskListVIE.Intent.DeleteTask"
    const val UpdateTaskCompletion = "$Vie.TaskListVIE.Intent.UpdateTaskCompletion"
    const val TaskListSignal = "$Vie.TaskListVIE.Signal"
    const val TaskListError = "$Vie.TaskListVIE.Signal.Error"
    const val EditTaskModel = "$Vie.EditTaskVIE.Model"
    const val EditTaskIntent = "$Vie.EditTaskVIE.Intent"
    const val EditTaskEdit = "$Vie.EditTaskVIE.Intent.Edit"
    const val EditTaskSignal = "$Vie.EditTaskVIE.Signal"
    const val EditTaskEdited = "$Vie.EditTaskVIE.Signal.Edited"
    const val SettingsModel = "$Vie.SettingsVIE.Model"
    const val SettingsLoading = "$Vie.SettingsVIE.Model.Loading"
    const val SettingsLoaded = "$Vie.SettingsVIE.Model.Loaded"
    const val SettingsIntent = "$Vie.SettingsVIE.Intent"
    const val SetDarkMode = "$Vie.SettingsVIE.Intent.SetDarkMode"
    const val SettingsSignal = "$Vie.SettingsVIE.Signal"

    private fun serializable(name: String, vararg subTypes: String) =
        VIETypeInfo(name, isSerializable = true, sealedSubTypes = subTypes.takeIf { it.isNotEmpty() }?.toList())

    val taskListInfo = VIEViewModelInfo(
        taskList, "$Vie.vm.TaskListVM",
        model = serializable(TaskListModel),
        intent = serializable(TaskListIntent, DeleteTask, UpdateTaskCompletion),
        signal = serializable(TaskListSignal, TaskListError),
    )

    fun editTaskInfo(id: Uuid) = VIEViewModelInfo(
        id, "$Vie.vm.EditTaskVM",
        model = serializable(EditTaskModel),
        intent = serializable(EditTaskIntent, EditTaskEdit),
        signal = serializable(EditTaskSignal, EditTaskEdited, "$Vie.EditTaskVIE.Signal.LoadFail", "$Vie.EditTaskVIE.Signal.Error"),
    )

    /** A sealed Model, and a Signal that is not `@Serializable`: rows show `toString` only. */
    val settingsInfo = VIEViewModelInfo(
        settings, "$Vie.vm.SettingsVM",
        model = serializable(SettingsModel, SettingsLoading, SettingsLoaded),
        intent = serializable(SettingsIntent, SetDarkMode),
        signal = VIETypeInfo(SettingsSignal, isSerializable = false),
    )

    // Sample objects

    const val Milk = "8c1e5d2a-3b1f-4c6e-9a0d-1f2e3d4c5b6a"
    const val Brief = "d42a7f10-5c3e-4b8a-8e1d-0a9f6c2b7e34"

    private fun task(title: String, description: String, completed: Boolean) = buildJsonObject {
        put("title", title)
        put("description", description)
        put("completed", completed)
    }

    private fun entry(id: String, value: JsonElement) = buildJsonObject {
        put("first", id)
        put("second", value)
    }

    fun taskListModel(tasks: List<Pair<String, JsonObject>>?, work: Map<String, String> = emptyMap()): JsonObject = buildJsonObject {
        put("tasks", tasks?.let { list -> JsonArray(list.map { (id, task) -> entry(id, task) }) } ?: JsonNull)
        put("work", JsonArray(work.map { (id, step) -> entry(id, JsonPrimitive(step)) }))
    }

    val milk = task("Buy milk", "2 litres", completed = false)
    val milkDone = task("Buy milk", "2 litres", completed = true)
    val brief = task("Write brief", "", completed = true)

    fun deleteTask(id: String): JsonObject = buildJsonObject {
        put("#type", DeleteTask)
        put("taskId", id)
    }

    fun updateCompletion(id: String, completed: Boolean): JsonObject = buildJsonObject {
        put("#type", UpdateTaskCompletion)
        put("taskId", id)
        put("completed", completed)
    }

    fun error(message: String): JsonObject = buildJsonObject {
        put("#type", TaskListError)
        put("message", message)
    }

    fun editTaskModel(loading: Boolean, task: JsonObject?): JsonObject = buildJsonObject {
        put("loading", loading)
        put("task", task ?: JsonNull)
    }

    fun edit(task: JsonObject): JsonObject = buildJsonObject {
        put("#type", EditTaskEdit)
        put("task", task)
    }

    // Messages

    class Builder(private val connection: ConnectionId) {

        val events = mutableListOf<TimeMachineEvent>()

        fun hello(session: TimeMachineSession, at: Instant, version: Int = TimeMachineConnection.protocolVersion) {
            events += TimeMachineEvent.HelloReceived(connection, TimeMachineClientMessage.Hello(session, version), at)
        }

        fun disconnect(at: Instant) {
            events += TimeMachineEvent.Disconnected(connection, at)
        }

        fun message(message: TimeMachineClientMessage, at: Instant, length: Int = 200) {
            events += TimeMachineEvent.MessageReceived(connection, message, length, at)
        }

        fun started(info: VIEViewModelInfo, sequence: Long, at: Instant) =
            message(TimeMachineClientMessage.ViewModelStarted(info, at, TimeMachineSequence(sequence)), at)

        fun closed(viewModelId: Uuid, sequence: Long, at: Instant) =
            message(TimeMachineClientMessage.ViewModelClosed(viewModelId, at, TimeMachineSequence(sequence)), at)

        fun dropped(viewModelId: Uuid, sequence: Long, count: Int, at: Instant) =
            message(TimeMachineClientMessage.ObservationsDropped(viewModelId, count, at, TimeMachineSequence(sequence)), at)

        fun info(viewModelId: Uuid, info: VIEViewModelInfo?, at: Instant) =
            message(TimeMachineClientMessage.ViewModelInfo(viewModelId, info), at)

        /** An observation: its `toString` is the JSON, read the way Kotlin prints a data class. */
        fun observed(
            viewModelId: Uuid,
            sequence: Long,
            at: Instant,
            kind: VIEObjectKind,
            typeName: String,
            json: JsonElement?,
            asString: String = json?.let { typeName.substringAfterLast('.') + it.toString() } ?: typeName.substringAfterLast('.'),
            delivery: VIESignalDelivery? = null,
            interventionId: Uuid? = null,
        ) = message(
            TimeMachineClientMessage.Observed(
                VIEObservation(viewModelId, at, kind, typeName, asString, json, interventionId, delivery),
                TimeMachineSequence(sequence),
            ),
            at,
            length = asString.length + (json?.toString()?.length ?: 0) + 120,
        )

        fun result(requestId: Uuid, result: VIEInterventionResult, at: Instant) =
            message(TimeMachineClientMessage.InterventionResult(requestId, result), at, length = 80)

        fun intervene(
            viewModelId: Uuid,
            requestId: Uuid,
            kind: VIEObjectKind,
            typeName: String,
            json: JsonElement,
            at: Instant,
            sessionId: Uuid = session.id,
        ) {
            events += TimeMachineEvent.Intervene(sessionId, viewModelId, kind, typeName, json, requestId, at)
        }
    }
}
