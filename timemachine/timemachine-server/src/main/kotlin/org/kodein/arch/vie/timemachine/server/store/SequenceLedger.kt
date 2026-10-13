package org.kodein.arch.vie.timemachine.server.store

import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence


/**
 * The timeline sequences a session received, across all of its connections.
 *
 * Sequence is the only order: a message is placed by its sequence, whichever connection carried it
 * and whenever it arrives. A late frame from an evicted connection is kept, and heals the missing
 * range it falls in.
 *
 * @property floor The lowest sequence received, `null` before the first.
 * @property highest The highest sequence received, `null` before the first.
 * @property missing The sequences between [floor] and [highest] not received yet, as sorted,
 *   disjoint ranges. Never pruned, but it stays small: a lasting range only opens for a frame skipped
 *   as undecodable, which the next acknowledgement covers and the probe forgets; a range a reconnect
 *   opens is healed by the probe's resend or by the evicted connection's late frame.
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
            sequence < floor -> copy(floor = sequence, missing = listOfNotEmpty(sequence.inc()..floor.dec()) + missing)
            sequence > highest -> copy(highest = sequence, missing = missing + listOfNotEmpty(highest.inc()..sequence.dec()))
            else -> {
                val index = missing.indexOfFirst { sequence in it }
                if (index < 0) return null
                val range = missing[index]
                val healed = listOfNotEmpty(range.start..sequence.dec(), sequence.inc()..range.endInclusive)
                copy(missing = missing.subList(0, index) + healed + missing.subList(index + 1, missing.size))
            }
        }
    }

    private fun listOfNotEmpty(vararg ranges: ClosedRange<TimeMachineSequence>): List<ClosedRange<TimeMachineSequence>> = ranges.filterNot { it.isEmpty() }
}
