package org.kodein.arch.vie.timemachine.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement


class TimeMachineSequenceTest {

    @Test
    fun encodesAsABareNumber() {
        assertEquals(
            JsonPrimitive(7L),
            TimeMachineJson.encodeToJsonElement(TimeMachineSequence(7)),
        )
    }

    @Test
    fun decodesFromABareNumber() {
        assertEquals(TimeMachineSequence(7), TimeMachineJson.decodeFromString(TimeMachineSequence.serializer(), "7"))
    }

    @Test
    fun isOrderedByItsNumber() {
        assertEquals(true, TimeMachineSequence(2) < TimeMachineSequence(10))
    }

    @Test
    fun incIsTheNextPosition() {
        var sequence = TimeMachineSequence(7)
        sequence++
        assertEquals(TimeMachineSequence(8), sequence)
    }

    @Test
    fun decIsThePreviousPosition() {
        var sequence = TimeMachineSequence(7)
        sequence--
        assertEquals(TimeMachineSequence(6), sequence)
    }

    @Test
    fun incAndDecAreInverse() {
        assertEquals(TimeMachineSequence(7), TimeMachineSequence(7).let { it.inc().dec() })
    }

    @Test
    fun firstIsTheSessionsFirstPosition() {
        assertEquals(TimeMachineSequence(0), TimeMachineSequence.First)
        assertEquals(TimeMachineSequence(1), TimeMachineSequence.First.inc())
    }
}
