package org.kodein.arch.vie.timemachine.server.store

import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.protocol.TimeMachineServerMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineTimelineMessage
import kotlin.time.Instant
import kotlin.uuid.Uuid


/**
 * Everything the inspector knows: immutable, and changed only by [on], a pure function.
 *
 * @property sessions The sessions, in the order they first connected.
 * @property connections The connections whose `Hello` was accepted, to their session. A connection
 *   another one evicted stays mapped until its own disconnect, so the frames already received on it
 *   are still placed. Messages from a connection not in it are ignored.
 * @property rejections The rejected probes, by session identifier.
 */
data class TimeMachineState(
    val limits: Limits = Limits(),
    val listener: ListenerState = ListenerState.Starting,
    val sessions: Map<Uuid, SessionRecord> = emptyMap(),
    val connections: Map<ConnectionId, Uuid> = emptyMap(),
    val rejections: Map<Uuid, Rejection> = emptyMap(),
) {

    /** The state after [event], and the commands it calls for. */
    fun on(event: TimeMachineEvent): Transition = when (event) {
        is TimeMachineEvent.HelloReceived -> onHello(event)
        is TimeMachineEvent.MessageReceived -> onMessage(event)
        is TimeMachineEvent.Disconnected -> onDisconnected(event)
        is TimeMachineEvent.ClearSession -> onClearSession(event.sessionId)
        TimeMachineEvent.ClearDisconnected -> Transition(removeSessions(sessions.values.filterNot { it.isConnected }.map { it.id }.toSet()))
        is TimeMachineEvent.DismissRejection -> Transition(
            rejections[event.sessionId]?.let { copy(rejections = rejections + (it.sessionId to it.copy(dismissed = true))) } ?: this
        )
        is TimeMachineEvent.Intervene -> onIntervene(event)
        is TimeMachineEvent.DeadlinePassed -> Transition(
            sessions.values.firstOrNull { event.requestId in it.interventionIndex }
                ?.let { withSession(it.updateIntervention(event.requestId) { copy(deadlinePassedAt = deadlinePassedAt ?: event.now) }) }
                ?: this
        )
        is TimeMachineEvent.ListenerChanged -> Transition(copy(listener = event.listener))
    }

    /**
     * The state after every event of [events], in order, and the commands they call for. Ends with
     * one [TimeMachineServerMessage.Received] per connection that delivered timeline messages,
     * acknowledging the highest one: a duplicate is acknowledged too, since the inspector holds it.
     */
    fun on(events: List<TimeMachineEvent>): Transition {
        var state = this
        val commands = mutableListOf<Command>()
        val applied = LinkedHashMap<ConnectionId, TimeMachineSequence>()
        for (event in events) {
            if (event is TimeMachineEvent.MessageReceived && event.message is TimeMachineTimelineMessage && event.connection in state.connections) {
                applied[event.connection] = applied[event.connection]?.let { maxOf(it, event.message.sequence) } ?: event.message.sequence
            }
            val transition = state.on(event)
            state = transition.state
            commands += transition.commands
        }
        applied.forEach { (connection, sequence) -> commands += Command.Send(connection, TimeMachineServerMessage.Received(sequence)) }
        return Transition(state, commands)
    }

    /**
     * The timeline of ViewModel [viewModelId] in session [sessionId]: [TimelineEntry.HistoryUnavailable],
     * then [TimelineEntry.Evicted], at the top; then the rows by sequence, with a
     * [TimelineEntry.PossiblyIncomplete] in position at each missing range that overlaps the
     * ViewModel's alive span and lies above its oldest retained row. Empty for an unknown session or
     * ViewModel.
     */
    fun timeline(sessionId: Uuid, viewModelId: Uuid): List<TimelineEntry> {
        val session = sessions[sessionId] ?: return emptyList()
        val vm = session.viewModels[viewModelId] ?: return emptyList()
        val span = vm.aliveSpan(session.sequences)
        val oldest = vm.rows.firstOrNull()?.sequence
        val markers = if (span == null || oldest == null) emptyList()
        else session.sequences.missing.filter { it.start > oldest && it.start <= span.endInclusive && it.endInclusive >= span.start }
        return buildList {
            if (vm.historyUnavailable(session.sequences)) add(TimelineEntry.HistoryUnavailable)
            if (vm.evictedCount > 0) add(TimelineEntry.Evicted(vm.evictedCount))
            var next = 0
            for (row in vm.rows) {
                while (next < markers.size && markers[next].start < row.sequence) add(TimelineEntry.PossiblyIncomplete(markers[next++]))
                add(TimelineEntry.Message(row))
            }
            while (next < markers.size) add(TimelineEntry.PossiblyIncomplete(markers[next++]))
        }
    }

    private fun onHello(event: TimeMachineEvent.HelloReceived): Transition {
        if (event.connection in connections) return Transition(this)
        val connection = event.connection
        val hello = event.hello
        val sessionId = hello.session.id
        if (hello.protocolVersion != TimeMachineConnection.protocolVersion) {
            val previous = rejections[sessionId]
            val rejection = Rejection(
                sessionId = sessionId,
                name = hello.session.name,
                protocolVersion = hello.protocolVersion,
                attempts = (previous?.attempts ?: 0) + 1,
                lastAttemptAt = event.now,
                dismissed = previous?.dismissed ?: false,
            )
            return Transition(
                copy(rejections = rejections + (sessionId to rejection)),
                listOf(
                    Command.Send(connection, TimeMachineServerMessage.Rejected(TimeMachineConnection.protocolVersion)),
                    Command.Close(connection),
                ),
            )
        }
        val commands = mutableListOf<Command>(Command.Send(connection, TimeMachineServerMessage.Welcome))
        val known = sessions[sessionId]
        val session = if (known == null) {
            SessionRecord(
                session = hello.session,
                protocolVersion = hello.protocolVersion,
                connection = connection,
                connections = listOf(ConnectionSpan(ConnectionSpan.Edge(event.now, null))),
            )
        } else {
            // A probe never holds two connections at once: the prior one is dead.
            known.connection?.let { commands += Command.Close(it) }
            // Re-asks any answer lost with the previous connection.
            known.viewModels.values.filter { !it.closed && it.awaitingInfo }.forEach { commands += Command.Send(connection, TimeMachineServerMessage.RequestViewModelInfo(it.id)) }
            // An evicted connection's span keeps no disconnect: this one's start is its only divider.
            known.copy(
                session = hello.session,
                protocolVersion = hello.protocolVersion,
                connection = connection,
                connections = known.connections + ConnectionSpan(ConnectionSpan.Edge(event.now, known.sequences.highest)),
            )
        }
        return Transition(copy(sessions = sessions + (sessionId to session), connections = connections + (connection to sessionId)), commands)
    }

    private fun onMessage(event: TimeMachineEvent.MessageReceived): Transition {
        val session = connections[event.connection]?.let { sessions[it] } ?: return Transition(this)
        return when (val message = event.message) {
            is TimeMachineTimelineMessage -> onTimeline(session, message, event.length, event.now)
            is TimeMachineClientMessage.ViewModelInfo -> Transition(withSession(onInfo(session, message, event.now)))
            is TimeMachineClientMessage.InterventionResult ->
                Transition(withSession(session.updateIntervention(message.requestId) { copy(result = result ?: message.result) }))
            is TimeMachineClientMessage.Hello -> Transition(this)
        }
    }

    private fun onIntervene(event: TimeMachineEvent.Intervene): Transition {
        val session = sessions[event.sessionId] ?: return Transition(this)
        val vm = session.viewModels[event.viewModelId] ?: return Transition(this)
        val connection = session.connection
        val intervention = Intervention(
            requestId = event.requestId,
            kind = event.kind,
            typeName = event.typeName,
            asJson = event.asJson,
            requestedAt = event.now,
            sent = connection != null,
        )
        val next = session.copy(
            viewModels = session.viewModels + (vm.id to vm.copy(interventions = vm.interventions + (event.requestId to intervention))),
            interventionIndex = session.interventionIndex + (event.requestId to vm.id),
        )
        val commands = if (connection == null) emptyList() else listOf(
            Command.Send(connection, TimeMachineServerMessage.Intervene(event.requestId, vm.id, event.kind, event.typeName, event.asJson)),
            Command.StartDeadline(event.requestId),
        )
        return Transition(withSession(next), commands)
    }

    private fun onTimeline(session: SessionRecord, message: TimeMachineTimelineMessage, length: Int, now: Instant): Transition {
        val sequences = session.sequences.receive(message.sequence) ?: return Transition(this)
        val id = message.viewModelId
        if (id in session.cleared) return Transition(withSession(session.copy(sequences = sequences)))

        val row = Row(message, length)
        val known = (session.viewModels[id] ?: ViewModelRecord(id = id, info = null, firstSeenAt = now)).copy(seenOnTimeline = true)
        var vm = when (message) {
            is TimeMachineClientMessage.ViewModelStarted -> known.copy(info = message.info, awaitingInfo = false).let {
                if (it.startSequence == null) it.copy(startSequence = message.sequence, startedAt = message.at).place(row, limits) else it
            }
            is TimeMachineClientMessage.ViewModelClosed ->
                if (known.closeSequence == null) known.copy(closeSequence = message.sequence, closedAt = message.at, closed = true).place(row, limits)
                else known
            is TimeMachineClientMessage.Observed, is TimeMachineClientMessage.ObservationsDropped -> known.place(row, limits)
        }

        // A drop report may hide a close: whether the ViewModel is alive is asked again.
        val commands = mutableListOf<Command>()
        var next = session.copy(sequences = sequences)
        if (!vm.closed && (message is TimeMachineClientMessage.ObservationsDropped || (vm.isUnknown && !vm.awaitingInfo))) {
            vm = vm.copy(awaitingInfo = true)
            // While disconnected nothing is sent: the request made on revival covers it.
            session.connection?.let { commands += Command.Send(it, TimeMachineServerMessage.RequestViewModelInfo(id)) }
        }
        next = next.copy(viewModels = next.viewModels + (id to vm))
        // Recorded whether the row was placed or counted as evicted. An unknown request identifier,
        // e.g. from an earlier run of the inspector, links nothing: the row is still marked intervened.
        val tag = (message as? TimeMachineClientMessage.Observed)?.observation?.interventionId
        if (tag != null) next = next.updateIntervention(tag) { copy(taggedSequence = taggedSequence ?: message.sequence) }
        return Transition(withSession(next), commands)
    }

    private fun onInfo(session: SessionRecord, message: TimeMachineClientMessage.ViewModelInfo, now: Instant): SessionRecord {
        val id = message.viewModelId
        if (id in session.cleared) return session
        val known = session.viewModels[id]
        val vm = when {
            message.info != null -> (known ?: ViewModelRecord(id = id, info = null, firstSeenAt = now)).copy(info = message.info, awaitingInfo = false)
            known == null || known.closed -> return session
            // The probe no longer knows it: it closed, and its close was lost or is still in flight.
            else -> known.copy(closed = true, inferredEnd = session.sequences.highest, awaitingInfo = false)
        }
        return session.copy(viewModels = session.viewModels + (id to vm))
    }

    private fun onDisconnected(event: TimeMachineEvent.Disconnected): Transition {
        val sessionId = connections[event.connection] ?: return Transition(this)
        val session = sessions.getValue(sessionId)
        val next = if (session.connection != event.connection) session else session.copy(
            connection = null,
            connections = session.connections.dropLast(1) +
                session.connections.last().copy(disconnected = ConnectionSpan.Edge(event.now, session.sequences.highest)),
        )
        return Transition(copy(sessions = sessions + (sessionId to next), connections = connections - event.connection))
    }

    private fun onClearSession(sessionId: Uuid): Transition {
        val session = sessions[sessionId] ?: return Transition(this)
        if (!session.isConnected) return Transition(removeSessions(setOf(sessionId)))
        val closed = session.viewModels.values.filter { it.closed }.map { it.id }.toSet()
        return Transition(
            withSession(
                session.copy(
                    viewModels = session.viewModels - closed,
                    cleared = session.cleared + closed,
                    interventionIndex = session.interventionIndex.filterValues { it !in closed },
                )
            )
        )
    }

    private fun removeSessions(ids: Set<Uuid>): TimeMachineState =
        copy(sessions = sessions - ids, connections = connections.filterValues { it !in ids })

    private fun withSession(session: SessionRecord): TimeMachineState = copy(sessions = sessions + (session.id to session))
}

/** This session with intervention [requestId] transformed; unchanged when its index does not hold it. */
private fun SessionRecord.updateIntervention(requestId: Uuid, transform: Intervention.() -> Intervention): SessionRecord {
    val vm = interventionIndex[requestId]?.let { viewModels[it] } ?: return this
    val intervention = vm.interventions[requestId] ?: return this
    return copy(viewModels = viewModels + (vm.id to vm.copy(interventions = vm.interventions + (requestId to intervention.transform()))))
}
