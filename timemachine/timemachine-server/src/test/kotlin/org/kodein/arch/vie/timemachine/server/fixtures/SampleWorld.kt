package org.kodein.arch.vie.timemachine.server.fixtures

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineProtocolVersion
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEInterventionResult
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo
import org.kodein.arch.vie.timemachine.server.fixtures.Demo.Builder
import org.kodein.arch.vie.timemachine.server.store.ConnectionId
import org.kodein.arch.vie.timemachine.server.store.Limits
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope


/**
 * What differs from the base world. The defaults are the world the design looks at; the others are
 * the states the design's frames add to it.
 *
 * @property earlierSession Whether the earlier, disconnected session is there.
 * @property demoSession Whether the Demo session is there at all: when it is not, nothing of it is.
 * @property disconnected Whether the Demo session has disconnected.
 * @property rejected Whether a probe of another protocol version was turned away.
 * @property unknownViewModel Whether a ViewModel nobody described has sent a row.
 * @property unknownClosed Whether the probe then answered that it never knew that ViewModel.
 * @property silentViewModel Whether a ViewModel has been described but has no entry yet.
 * @property notSentIntervention Whether an intervention asked while the session was gone is recorded.
 * @property extraIntervention An intervention asked of TaskListVM after the base data, and how it stands.
 */
internal data class WorldOptions(
    val earlierSession: Boolean = true,
    val demoSession: Boolean = true,
    val disconnected: Boolean = false,
    val rejected: Boolean = false,
    val unknownViewModel: Boolean = false,
    val unknownClosed: Boolean = false,
    val silentViewModel: Boolean = false,
    val notSentIntervention: Boolean = false,
    val extraIntervention: ExtraIntervention? = null,
)

/**
 * How the latest intervention of TaskListVM stands, each as the store derives it from the facts it was
 * given: the request, the probe's answer, the row it tagged and the deadline.
 */
internal enum class ExtraIntervention { Pending, Succeeded, AcceptedNotObserved, BadJson, UnknownType, TimedOut }

/**
 * The world the inspector looks at in the design: the Demo app, as the events its probe sent, so a
 * state is the store's own, built by its pure core. The ViewModel tests read it.
 */
internal object SampleWorld {

    /** The store's bound on each ViewModel's rows: low, so the base data evicts some. */
    const val BaseMaxRows: Int = 14

    val demoScope = InspectorScope.Session(Demo.session.id)
    val earlierScope = InspectorScope.Session(Demo.oldSession.id)
    val taskListScope = InspectorScope.ViewModel(Demo.session.id, Demo.taskList)
    val editTaskScope = InspectorScope.ViewModel(Demo.session.id, Demo.editTask)
    val editTaskClosedScope = InspectorScope.ViewModel(Demo.session.id, Demo.editTaskClosed)
    val settingsScope = InspectorScope.ViewModel(Demo.session.id, Demo.settings)
    val unknownScope = InspectorScope.ViewModel(Demo.session.id, Demo.unknown)
    val earlierTaskListScope = InspectorScope.ViewModel(Demo.oldSession.id, Demo.oldTaskList)

    /** The same app, speaking another protocol, when [WorldOptions.rejected]: it never becomes a session. */
    val rejectedSession = TimeMachineSession(Uuid.parse("8e2b0000-0000-4000-8000-00000000f00d"), "Demo")

    /** The state [events] lead to, in a store that keeps [maxRows] rows of each ViewModel. */
    fun state(events: List<TimeMachineEvent>, maxRows: Int = BaseMaxRows): TimeMachineState =
        TimeMachineState(limits = Limits(maxRows = maxRows)).on(events).state

    fun state(options: WorldOptions = WorldOptions()): TimeMachineState = state(events(options))

    fun listening() = listOf(TimeMachineEvent.ListenerChanged(ListenerState.Listening(TimeMachineConnection.port)))

    /**
     * The Demo app. `TaskListVM` is the one the design looks at: its start was never seen (the
     * inspector came after it), it dropped 42 entries at #215, sequences 216–219 never arrived,
     * its oldest rows were evicted, and four interventions were asked of it. `EditTaskVM` ran
     * twice, one of them closed. `SettingsVM` has a sealed Model and a Signal that is not
     * serializable.
     */
    fun events(options: WorldOptions = WorldOptions()): List<TimeMachineEvent> {
        val disconnected = options.disconnected
        val notSent = options.notSentIntervention
        val rejected = options.rejected
        val unknown = options.unknownViewModel
        val silent = options.silentViewModel
        val b = Builder(Demo.connection)
        val old = Builder(Demo.oldConnection)
        val vm = Demo.taskList
        val model = VIEObjectKind.Model
        val intent = VIEObjectKind.Intent
        val signal = VIEObjectKind.Signal

        // A session that is gone, to browse.
        old.hello(Demo.oldSession, Demo.at(-240_000))
        old.started(Demo.oldTaskListInfo, 0, Demo.at(-239_900))
        old.observed(Demo.oldTaskList, 1, Demo.at(-239_500), model, Demo.TaskListModel, Demo.taskListModel(null))
        old.observed(Demo.oldTaskList, 2, Demo.at(-239_000), model, Demo.TaskListModel, Demo.taskListModel(listOf(Demo.Milk to Demo.milk)))
        old.disconnect(Demo.at(-20_000))

        b.hello(Demo.session, Demo.at(17_900))
        b.observed(vm, 200, t(200), model, Demo.TaskListModel, Demo.taskListModel(null))
        b.info(vm, Demo.taskListInfo, t(200))
        b.observed(vm, 201, t(201), model, Demo.TaskListModel, Demo.taskListModel(listOf(Demo.Milk to Demo.milk, Demo.Brief to Demo.brief)))
        // Intervention 1: a Model, whose row (202) the bounds evict.
        val replaced = Demo.taskListModel(listOf(Demo.Milk to Demo.milk, Demo.Brief to Demo.brief))
        b.intervene(vm, Demo.request1, model, Demo.TaskListModel, replaced, t(201))
        b.result(Demo.request1, VIEInterventionResult.Success, t(201))
        b.observed(vm, 202, t(202), model, Demo.TaskListModel, replaced, interventionId = Demo.request1)
        b.observed(vm, 203, t(203), intent, Demo.UpdateTaskCompletion, Demo.updateCompletion(Demo.Milk, true))
        b.observed(vm, 204, t(204), model, Demo.TaskListModel, Demo.taskListModel(listOf(Demo.Milk to Demo.milkDone, Demo.Brief to Demo.brief)))
        b.observed(vm, 205, t(205), intent, Demo.UpdateTaskCompletion, Demo.updateCompletion(Demo.Milk, false))
        b.observed(vm, 206, t(206), model, Demo.TaskListModel, Demo.taskListModel(listOf(Demo.Milk to Demo.milk, Demo.Brief to Demo.brief)))
        b.observed(vm, 207, t(207), intent, Demo.DeleteTask, Demo.deleteTask(Demo.Milk))
        b.observed(vm, 208, t(208), model, Demo.TaskListModel, Demo.taskListModel(listOf(Demo.Milk to Demo.milk, Demo.Brief to Demo.brief), mapOf(Demo.Milk to "Deleting")))
        b.observed(vm, 209, t(209), model, Demo.TaskListModel, Demo.taskListModel(listOf(Demo.Brief to Demo.brief)))

        // SettingsVM
        b.started(Demo.settingsInfo, 210, t(210))
        b.observed(Demo.settings, 211, t(211), model, Demo.SettingsLoading, buildJsonObject { put("#type", Demo.SettingsLoading) })
        b.observed(
            Demo.settings, 212, t(212), model, Demo.SettingsLoaded,
            buildJsonObject { put("#type", Demo.SettingsLoaded); put("darkMode", false) },
        )
        // Its Signal is not serializable: `toString` only.
        b.observed(Demo.settings, 213, t(213), signal, "${Demo.SettingsSignal}.Saved", null, asString = "Saved", delivery = VIESignalDelivery.Event)
        b.observed(
            Demo.settings, 214, t(214), intent, Demo.SetDarkMode,
            buildJsonObject { put("#type", Demo.SetDarkMode); put("enabled", true) },
        )
        b.dropped(vm, 215, 42, t(215))
        // 216 to 219 never arrive.

        // EditTaskVM, twice.
        b.started(Demo.editTaskInfo(Demo.editTask), 220, t(220))
        b.observed(Demo.editTask, 221, t(221), model, Demo.EditTaskModel, Demo.editTaskModel(loading = true, task = null))
        b.observed(Demo.editTask, 222, t(222), model, Demo.EditTaskModel, Demo.editTaskModel(loading = false, task = Demo.milk))
        b.started(Demo.editTaskInfo(Demo.editTaskClosed), 223, t(223))
        b.observed(Demo.editTaskClosed, 224, t(224), model, Demo.EditTaskModel, Demo.editTaskModel(loading = true, task = null))
        b.observed(Demo.editTaskClosed, 225, t(225), model, Demo.EditTaskModel, Demo.editTaskModel(loading = false, task = Demo.milk))

        // The second EditTaskVM is typed into until it closes; TaskListVM interleaves.
        var typed = 0
        fun type(vmId: Uuid, sequence: Long) {
            val task = buildJsonObject { put("title", "Buy milk"); put("description", "2 litres, semi-skimmed".take(8 + typed / 2)); put("completed", false) }
            if (typed++ % 2 == 0) b.observed(vmId, sequence, t(sequence), intent, Demo.EditTaskEdit, Demo.edit(task))
            else b.observed(vmId, sequence, t(sequence), model, Demo.EditTaskModel, Demo.editTaskModel(loading = false, task = task))
        }
        for (sequence in 226L..250L) when (sequence) {
            229L -> {
                // Intervention 2: an Intent.
                b.intervene(vm, Demo.request2, intent, Demo.DeleteTask, Demo.deleteTask(Demo.Brief), t(228))
                b.result(Demo.request2, VIEInterventionResult.Success, t(228))
                b.observed(vm, 229, t(229), intent, Demo.DeleteTask, Demo.deleteTask(Demo.Brief), interventionId = Demo.request2)
            }
            230L -> b.observed(vm, 230, t(230), model, Demo.TaskListModel, Demo.taskListModel(emptyList()))
            238L -> {
                // Intervention 3: a Model.
                val three = Demo.taskListModel(listOf(Demo.Milk to Demo.milk, Demo.Brief to Demo.brief))
                b.intervene(vm, Demo.request3, model, Demo.TaskListModel, three, t(237))
                b.result(Demo.request3, VIEInterventionResult.Success, t(237))
                b.observed(vm, 238, t(238), model, Demo.TaskListModel, three, interventionId = Demo.request3)
            }
            else -> type(Demo.editTaskClosed, sequence)
        }
        // Intervention 4 was refused: no row.
        b.intervene(vm, Demo.request4, intent, Demo.DeleteTask, buildJsonObject { put("#type", Demo.DeleteTask); put("taskId", 12) }, t(250))
        b.result(
            Demo.request4,
            VIEInterventionResult.Failure(VIEInterventionResult.Reason.BadJson, "Unexpected JSON token at offset 61: Expected string literal for 'taskId'"),
            t(250),
        )
        b.observed(
            vm, 251, t(251), signal, Demo.TaskListError, Demo.error("Network unreachable"),
            asString = "Error(message=Network unreachable)", delivery = VIESignalDelivery.Event,
        )
        for (sequence in 252L..254L) type(Demo.editTaskClosed, sequence)
        b.closed(Demo.editTaskClosed, 255, t(255))
        for (sequence in 256L..265L) type(Demo.editTask, sequence)
        b.observed(vm, 266, t(266), intent, Demo.DeleteTask, Demo.deleteTask(Demo.Brief))
        for (sequence in 267L..269L) type(Demo.editTask, sequence)
        b.observed(vm, 270, t(270), model, Demo.TaskListModel, Demo.taskListModel(listOf(Demo.Milk to Demo.milk)))
        b.observed(
            Demo.editTask, 271, t(271), signal, Demo.EditTaskEdited, buildJsonObject { put("#type", Demo.EditTaskEdited) },
            asString = "Edited", delivery = VIESignalDelivery.Event,
        )

        options.extraIntervention?.let { extra ->
            val request = Demo.requestLatest
            val type = if (extra == ExtraIntervention.UnknownType) Demo.UpdateTaskCompletion else Demo.DeleteTask
            val json = if (extra == ExtraIntervention.UnknownType) Demo.updateCompletion(Demo.Brief, false) else Demo.deleteTask(Demo.Brief)
            val asked = t(Demo.NextSequence)
            b.intervene(vm, request, intent, type, json, asked)
            when (extra) {
                ExtraIntervention.Pending -> Unit
                ExtraIntervention.Succeeded -> {
                    b.result(request, VIEInterventionResult.Success, asked)
                    b.observed(vm, Demo.NextSequence, asked, intent, Demo.DeleteTask, json, interventionId = request)
                }
                ExtraIntervention.AcceptedNotObserved -> {
                    b.result(request, VIEInterventionResult.Success, asked)
                    b.events += TimeMachineEvent.DeadlinePassed(request, asked + 5.seconds)
                }
                ExtraIntervention.BadJson -> b.result(
                    request,
                    VIEInterventionResult.Failure(VIEInterventionResult.Reason.BadJson, "Unexpected JSON token at offset 61: Expected string literal for 'taskId'"),
                    asked,
                )
                ExtraIntervention.UnknownType -> b.result(
                    request,
                    VIEInterventionResult.Failure(VIEInterventionResult.Reason.UnknownType, "${Demo.UpdateTaskCompletion} is not a subtype of the Intent"),
                    asked,
                )
                ExtraIntervention.TimedOut -> b.events += TimeMachineEvent.DeadlinePassed(request, asked + 5.seconds)
            }
        }

        val events = mutableListOf<TimeMachineEvent>()
        events += listening()
        if (options.earlierSession) events += old.events
        if (!options.demoSession) return events
        events += b.events

        if (unknown) {
            // Rows of a ViewModel nobody described: the store asks the probe.
            val u = Builder(Demo.connection)
            u.observed(Demo.unknown, Demo.NextSequence, Demo.at(29_000), model, "app.Unknown.Model", null, asString = "Model(…)")
            // The probe answered that it never knew it: it is closed.
            if (options.unknownClosed) u.info(Demo.unknown, null, Demo.at(29_100))
            events += u.events
        }
        if (silent) {
            val s = Builder(Demo.connection)
            s.info(Demo.silent, Demo.editTaskInfo(Demo.silent), Demo.at(29_000))
            events += s.events
        }
        if (rejected) {
            // The same app, speaking another protocol, retrying: one notice, two attempts.
            val version = TimeMachineProtocolVersion(TimeMachineConnection.protocolVersion.value + 1)
            listOf(ConnectionId(3) to Demo.at(22_000), ConnectionId(4) to Demo.at(27_000)).forEach { (connection, at) ->
                events += TimeMachineEvent.HelloReceived(
                    connection,
                    TimeMachineClientMessage.Hello(rejectedSession, version),
                    at,
                )
            }
        }
        if (disconnected) events += TimeMachineEvent.Disconnected(Demo.connection, Demo.at(29_000))
        if (notSent) {
            events += TimeMachineEvent.Intervene(
                Demo.session.id, vm, model, Demo.TaskListModel,
                Demo.taskListModel(listOf(Demo.Milk to Demo.milk)), Demo.requestNotSent, Demo.at(29_500),
            )
        }
        return events
    }

    /** When the base data had the row at [sequence] arrive. */
    private fun t(sequence: Number): Instant = Demo.at(
        when (val s = sequence.toLong()) {
            in 0..206 -> 18_000 + (s - 200) * 160
            207L -> 19_011
            208L -> 19_012
            else -> 19_012 + (s - 208) * 140
        },
    )
}

private val Demo.oldTaskListInfo
    get() = VIEViewModelInfo(oldTaskList, taskListInfo.typeName, taskListInfo.model, taskListInfo.intent, taskListInfo.signal)
