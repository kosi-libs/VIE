package org.kodein.arch.vie.timemachine.server.ui.timeline


/**
 * How far a list scrolls for an item at [offset], [size] long, to be whole in a viewport that goes from
 * [viewportStart] to [viewportEnd]: negative towards the start, 0 when it is whole already. An item
 * longer than the viewport stops with its start on the viewport's, never past it.
 */
internal fun revealDistance(offset: Int, size: Int, viewportStart: Int, viewportEnd: Int): Int = when {
    offset < viewportStart -> offset - viewportStart
    offset + size > viewportEnd -> minOf(offset + size - viewportEnd, offset - viewportStart)
    else -> 0
}

/**
 * The index the timeline's lazy list has for row [row] of the projection's items: the no-match item,
 * when there is one, is emitted before the rows.
 */
internal fun lazyRowIndex(row: Int, noMatch: Boolean): Int = if (noMatch) row + 1 else row
