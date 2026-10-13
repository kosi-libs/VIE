package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonElement
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineProtocolVersion
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo


internal val t0: Instant = Instant.fromEpochSeconds(1_800_000_000)

internal fun seq(n: Long) = TimeMachineSequence(n)

internal fun seq(n: Int) = TimeMachineSequence(n.toLong())

/** The sequences from [first] to [last], both included. */
internal fun seqs(first: Long, last: Long) = seq(first)..seq(last)

internal fun at(second: Int): Instant = t0 + second.seconds

internal fun info(id: Uuid) = VIETypeInfo("T", isSerializable = true).let { VIEViewModelInfo(id, "VM", it, it, it) }

internal fun hello(
    connection: ConnectionId,
    session: TimeMachineSession,
    now: Instant = t0,
    version: TimeMachineProtocolVersion = TimeMachineConnection.protocolVersion,
) = TimeMachineEvent.HelloReceived(connection, TimeMachineClientMessage.Hello(session, version), now)

internal fun receive(connection: ConnectionId, message: TimeMachineClientMessage, length: Int = 10, now: Instant = t0) =
    TimeMachineEvent.MessageReceived(connection, message, length, now)

internal fun started(vm: Uuid, sequence: Long) = TimeMachineClientMessage.ViewModelStarted(info(vm), t0, seq(sequence))

internal fun closed(vm: Uuid, sequence: Long, at: Instant = t0) = TimeMachineClientMessage.ViewModelClosed(vm, at, seq(sequence))

internal fun dropped(vm: Uuid, sequence: Long, count: Int = 1) = TimeMachineClientMessage.ObservationsDropped(vm, count, t0, seq(sequence))

internal fun observed(
    vm: Uuid,
    sequence: Long,
    kind: VIEObjectKind = VIEObjectKind.Model,
    typeName: String = "M",
    json: JsonElement? = null,
    at: Instant = t0,
    delivery: VIESignalDelivery? = null,
) = TimeMachineClientMessage.Observed(
    VIEObservation(vm, at, kind, typeName, asString = "#$sequence", asJson = json, delivery = delivery),
    seq(sequence),
)

internal fun vmInfo(vm: Uuid, known: Boolean = true) = TimeMachineClientMessage.ViewModelInfo(vm, if (known) info(vm) else null)

/** Applies [events] one by one, and returns the final state with every command, in order. */
internal fun TimeMachineState.after(vararg events: TimeMachineEvent): Transition =
    events.fold(Transition(this)) { (state, commands), event -> state.on(event).let { Transition(it.state, commands + it.commands) } }

internal fun TimeMachineState.session(id: Uuid): SessionRecord = sessions.getValue(id)

internal fun TimeMachineState.vm(session: Uuid, vm: Uuid): ViewModelRecord = session(session).viewModels.getValue(vm)

internal fun TimeMachineState.sequencesOf(session: Uuid, vm: Uuid): List<Long> = vm(session, vm).rows.map { it.sequence.value }

internal fun requests(commands: List<Command>, connection: ConnectionId): List<Uuid> =
    commands.filterIsInstance<Command.Send>()
        .filter { it.connection == connection }
        .mapNotNull { (it.message as? TimeMachineServerMessage.RequestViewModelInfo)?.viewModelId }

internal fun assertSends(connection: ConnectionId, messages: List<TimeMachineServerMessage>, commands: List<Command>) =
    assertEquals(messages.map { Command.Send(connection, it) }, commands)
