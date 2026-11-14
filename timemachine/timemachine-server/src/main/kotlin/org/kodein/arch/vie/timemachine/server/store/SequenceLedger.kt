package org.kodein.arch.vie.timemachine.server.store

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence


/**
 * The timeline sequences a session received or skipped, across all of its connections.
 *
 * Sequence is the only order: a message is placed by its sequence, whichever connection carried it
 * and whenever it arrives. A late frame from an evicted connection is kept, and heals the missing
 * range it falls in.
 *
 * @property floor The lowest sequence received or skipped, `null` before the first.
 * @property highest The highest sequence received or skipped, `null` before the first.
 * @property missing The sequences from [floor] to [highest], both included, not received, as sorted,
 *   disjoint ranges with a received sequence between any two; [floor] or [highest] is in it only when
 *   skipped, and a skipped sequence stays there for good unless a late frame brings it. Never pruned,
 *   but it stays small: a lasting range only opens for a frame skipped as undecodable or because the
 *   core threw on it, which the inspector acknowledges all the same and the probe forgets; a range a
 *   reconnect opens is healed by the probe's resend or by the evicted connection's late frame.
 */
data class SequenceLedger(
    val floor: TimeMachineSequence? = null,
    val highest: TimeMachineSequence? = null,
    val missing: List<ClosedRange<TimeMachineSequence>> = emptyList(),
) {

    /** This ledger once [sequence] is received, or `null` when it was received already. */
    fun receive(sequence: TimeMachineSequence): SequenceLedger? {
        if (floor == null || highest == null) return SequenceLedger(floor = sequence, highest = sequence)
        return when {
            sequence < floor -> copy(floor = sequence, missing = prepend(sequence.inc()..floor.dec(), missing))
            sequence > highest -> copy(highest = sequence, missing = append(missing, highest.inc()..sequence.dec()))
            else -> {
                val index = missing.indexOfFirst { sequence in it }
                if (index < 0) return null
                val range = missing[index]
                val healed = listOfNotEmpty(range.start..sequence.dec(), sequence.inc()..range.endInclusive)
                copy(missing = missing.subList(0, index) + healed + missing.subList(index + 1, missing.size))
            }
        }
    }

    /**
     * This ledger once [sequence] is skipped: a frame the inspector will never hold, whose sequence
     * therefore enters [missing] for good, even below [floor] or above [highest], where it moves them.
     * Unchanged when [sequence] was received or skipped already.
     */
    fun skip(sequence: TimeMachineSequence): SequenceLedger {
        if (floor == null || highest == null) return SequenceLedger(sequence, sequence, listOf(sequence..sequence))
        return when {
            sequence < floor -> copy(floor = sequence, missing = prepend(sequence..floor.dec(), missing))
            sequence > highest -> copy(highest = sequence, missing = append(missing, highest.inc()..sequence))
            else -> this // received already, or missing already
        }
    }

    // A range that touches its neighbour merges with it: one gap, one marker.
    private fun prepend(range: ClosedRange<TimeMachineSequence>, ranges: List<ClosedRange<TimeMachineSequence>>): List<ClosedRange<TimeMachineSequence>> {
        if (range.isEmpty()) return ranges
        val first = ranges.firstOrNull()
        return if (first != null && first.start == range.endInclusive.inc()) listOf(range.start..first.endInclusive) + ranges.drop(1)
        else listOf(range) + ranges
    }

    private fun append(ranges: List<ClosedRange<TimeMachineSequence>>, range: ClosedRange<TimeMachineSequence>): List<ClosedRange<TimeMachineSequence>> {
        if (range.isEmpty()) return ranges
        val last = ranges.lastOrNull()
        return if (last != null && last.endInclusive.inc() == range.start) ranges.dropLast(1) + (last.start..range.endInclusive)
        else ranges + listOf(range)
    }

    private fun listOfNotEmpty(vararg ranges: ClosedRange<TimeMachineSequence>): List<ClosedRange<TimeMachineSequence>> = ranges.filterNot { it.isEmpty() }
}
