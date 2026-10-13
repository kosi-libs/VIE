package org.kodein.arch.vie.timemachine.server.ui.sessions

import org.kodein.arch.vie.timemachine.server.ui.common.splitSerialName
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorScope
import org.kodein.arch.vie.timemachine.server.ui.core.ViewModelStatus


/** How a rail tile says whether its session is there: a filled dot, a hollow ring, or nothing. */
internal enum class RailDot { Filled, Ring, None }

/**
 * One tile of the compact layout's rail: a session or one of its ViewModels, as two letters.
 *
 * @property scope What a click selects.
 * @property label The two letters on the tile.
 * @property description What the tile is, in words: for a screen reader, and for the tests.
 * @property dimmed Whether what the tile stands for is gone: a disconnected session, a closed ViewModel.
 */
internal data class RailItem(
    val scope: InspectorScope,
    val label: String,
    val description: String,
    val selected: Boolean,
    val dot: RailDot,
    val dimmed: Boolean,
)

/**
 * The rail as [model] has it: every session, and under one of them its ViewModels. That session is the
 * selected scope's, or, with nothing selected, the one the empty centre pane names: the first connected.
 */
internal fun railItems(model: SessionsVIE.Model): List<RailItem> {
    val expanded = model.selected?.sessionId ?: (model.sessions.firstOrNull { it.connected } ?: model.sessions.firstOrNull())?.id
    return model.sessions.flatMap { session ->
        buildList {
            val scope = InspectorScope.Session(session.id)
            add(
                RailItem(
                    scope, sessionMonogram(session.name),
                    "${session.name} ${session.shortId}, ${if (session.connected) "connected" else "disconnected"}",
                    selected = model.selected == scope,
                    dot = if (session.connected) RailDot.Filled else RailDot.Ring,
                    dimmed = !session.connected,
                ),
            )
            if (session.id == expanded) for (vm in session.viewModels) {
                val vmScope = InspectorScope.ViewModel(session.id, vm.id)
                val name = vm.typeName?.let { splitSerialName(it).last } ?: "Unknown ViewModel"
                val closed = vm.status == ViewModelStatus.Closed
                add(
                    RailItem(
                        vmScope, viewModelMonogram(vm.typeName),
                        "$name ${vm.shortId}${if (closed) ", closed" else ""}",
                        selected = model.selected == vmScope,
                        dot = RailDot.None,
                        dimmed = closed || !session.connected,
                    ),
                )
            }
        }
    }
}

/** A session's first two characters (`Demo` → `De`), or `?` when it has no name. */
internal fun sessionMonogram(name: String): String =
    name.trim().let { trimmed -> trimmed.codePoints().limit(2).toArray().let { String(it, 0, it.size) } }.ifEmpty { "?" }

/**
 * A ViewModel's initials: what is not a lowercase letter in its simple type name, without a trailing
 * `VM`, at most two (`TaskListVM` → `TL`, `SettingsVM` → `S`); `VM` when there is none, `?` when its
 * type is not known.
 */
internal fun viewModelMonogram(typeName: String?): String {
    typeName ?: return "?"
    return splitSerialName(typeName).last.removeSuffix("VM").filterNot { it.isLowerCase() }.take(2).ifEmpty { "VM" }
}
