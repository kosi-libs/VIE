package org.kodein.arch.vie.timemachine.server.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.kodein.arch.vie.timemachine.server.store.seq


class SequenceInTest {

    @Test
    fun aWholeNumberIsRead() {
        assertEquals(seq(3), sequenceIn("""{"#type":"observed","sequence":3}"""))
    }

    @Test
    fun anUnknownSubtypeIsReadAllTheSame() {
        assertEquals(seq(3), sequenceIn("""{"#type":"from-a-later-version","sequence":3}"""))
    }

    @Test
    fun aStringIsNotASequence() {
        assertNull(sequenceIn("""{"sequence":"3"}"""))
    }

    @Test
    fun anAbsentSequenceIsNotASequence() {
        assertNull(sequenceIn("""{"#type":"observed"}"""))
        assertNull(sequenceIn("not a message"))
    }

    @Test
    fun aSequenceThatIsNotAtTheTopOfAnObjectIsNotASequence() {
        assertNull(sequenceIn("""[{"sequence":3}]"""))
        assertNull(sequenceIn("""{"sequence":[3]}"""))
    }

    @Test
    fun aNumberThatIsNotWholeIsNotASequence() {
        assertNull(sequenceIn("""{"sequence":1.5}"""))
        assertNull(sequenceIn("""{"sequence":null}"""))
    }
}
