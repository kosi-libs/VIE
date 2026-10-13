package org.kodein.arch.vie.timemachine.server.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


private fun ledgerOf(vararg sequences: Long): SequenceLedger =
    sequences.fold(SequenceLedger()) { ledger, sequence -> assertNotNull(ledger.receive(seq(sequence))) }

class SequenceLedgerTest {

    @Test
    fun theFirstSequenceIsTheFloorAndTheHighest() {
        assertEquals(SequenceLedger(floor = seq(5), highest = seq(5)), ledgerOf(5))
    }

    @Test
    fun consecutiveSequencesLeaveNothingMissing() {
        assertEquals(SequenceLedger(floor = seq(0), highest = seq(2)), ledgerOf(0, 1, 2))
    }

    @Test
    fun aSequenceAboveTheHighestExtendsItAndTheSkippedNumbersBecomeMissing() {
        assertEquals(SequenceLedger(floor = seq(0), highest = seq(4), missing = listOf(seqs(1, 3))), ledgerOf(0, 4))
    }

    @Test
    fun aSequenceBelowTheFloorLowersItAndTheNumbersInBetweenBecomeMissing() {
        assertEquals(SequenceLedger(floor = seq(2), highest = seq(9), missing = listOf(seqs(3, 5), seqs(8, 8))), ledgerOf(6, 7, 9, 2))
    }

    @Test
    fun aSequenceInsideAMissingRangeHealsIt() {
        val ledger = ledgerOf(0, 10)
        assertEquals(listOf(seqs(1, 4), seqs(6, 9)), ledger.receive(seq(5))?.missing)
        assertEquals(listOf(seqs(2, 9)), ledger.receive(seq(1))?.missing)
        assertEquals(listOf(seqs(1, 8)), ledger.receive(seq(9))?.missing)
        assertEquals(emptyList(), ledgerOf(0, 2, 1).missing)
    }

    @Test
    fun aSequenceAlreadyReceivedIsADuplicate() {
        val ledger = ledgerOf(2, 3, 6)
        assertNull(ledger.receive(seq(2))) // the floor
        assertNull(ledger.receive(seq(3)))
        assertNull(ledger.receive(seq(6))) // the highest
        assertEquals(listOf(seqs(4, 5)), ledger.missing)
    }
}
