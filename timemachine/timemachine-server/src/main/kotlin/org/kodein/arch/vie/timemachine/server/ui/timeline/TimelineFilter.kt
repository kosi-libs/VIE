package org.kodein.arch.vie.timemachine.server.ui.timeline

import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIEObservation
import org.kodein.arch.vie.timemachine.server.store.Row
import org.kodein.arch.vie.timemachine.server.store.TimelineEntry


/**
 * What the timeline shows: the observations of the [kinds] on, which were [intervenedOnly] when set,
 * whose type name, `toString` or JSON contains [query], ignoring case. Only observations are
 * filtered: bookends and markers always pass.
 */
data class TimelineFilter(
    val kinds: Set<VIEObjectKind> = VIEObjectKind.entries.toSet(),
    val intervenedOnly: Boolean = false,
    val query: String = "",
) {

    /** Whether the filter narrows anything on its own: a query, intervened-only, or a kind off. */
    val isNarrowing: Boolean get() = query.isNotEmpty() || intervenedOnly || kinds.size != VIEObjectKind.entries.size

    /**
     * Whether [observation] passes. [json] is its JSON text, asked for only when the query has to
     * look in it: the caches of that text belong to the pane, which keeps this function pure.
     */
    fun matches(observation: VIEObservation, json: () -> String?): Boolean =
        observation.kind in kinds &&
            (!intervenedOnly || observation.interventionId != null) &&
            matchesQuery(observation, json)

    private fun matchesQuery(observation: VIEObservation, json: () -> String?): Boolean =
        query.isEmpty() ||
            observation.typeName.contains(query, ignoreCase = true) ||
            observation.asString.contains(query, ignoreCase = true) ||
            json()?.contains(query, ignoreCase = true) == true

    /**
     * This filter with only the parts that hide [observation] cleared: a filter the developer set is
     * never dropped silently, but the ones that hide the selected row are what "show" undoes.
     */
    fun showing(observation: VIEObservation, json: () -> String?): TimelineFilter = copy(
        kinds = kinds + observation.kind,
        intervenedOnly = intervenedOnly && observation.interventionId != null,
        query = if (matchesQuery(observation, json)) query else "",
    )
}

/**
 * @property hidden How many observations the filter hid; markers and bookends are never counted.
 * @property active Whether a filter is on: it hid something, or it has a query, intervened-only or a kind off.
 */
data class Filtered<out T>(val items: List<T>, val hidden: Int, val active: Boolean)

/**
 * The [items] that pass this filter. [entryOf] gives the timeline entry an item stands for, `null`
 * when it always passes (a divider); [jsonOf] gives a row's JSON text.
 */
fun <T> TimelineFilter.apply(items: List<T>, entryOf: (T) -> TimelineEntry?, jsonOf: (Row) -> String?): Filtered<T> {
    var hidden = 0
    val kept = items.filter { item ->
        val row = (entryOf(item) as? TimelineEntry.Message)?.row
        val observation = (row?.message as? TimeMachineClientMessage.Observed)?.observation
        (observation == null || matches(observation) { jsonOf(row) }).also { if (!it) hidden++ }
    }
    return Filtered(kept, hidden, active = hidden > 0 || isNarrowing)
}

/** How far into a text a search hit can be before the text is trimmed in front of it. */
private const val SnippetLeadStart = 40

/** How much of the text is kept in front of a trimmed hit. */
private const val SnippetLead = 28

/**
 * [text] as a search result shows it: a hit past index 40 is brought into view by trimming what
 * precedes it to `…` plus 28 characters.
 */
fun searchSnippet(text: String, query: String): String {
    if (query.isEmpty()) return text
    val hit = text.indexOf(query, ignoreCase = true)
    return if (hit > SnippetLeadStart) "…" + text.substring(hit - SnippetLead) else text
}
