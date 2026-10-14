package org.kodein.arch.vie.timemachine.protocol

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class TimeMachineConnectionTest {

    @Test
    fun acceptsItsOwnProtocolVersion() {
        assertTrue(TimeMachineConnection.isCompatible(TimeMachineConnection.protocolVersion))
    }

    @Test
    fun rejectsAnEarlierProtocolVersion() {
        assertFalse(
            TimeMachineConnection.isCompatible(
                TimeMachineProtocolVersion(TimeMachineConnection.protocolVersion.value - 1)
            )
        )
    }

    @Test
    fun rejectsALaterProtocolVersion() {
        assertFalse(
            TimeMachineConnection.isCompatible(
                TimeMachineProtocolVersion(TimeMachineConnection.protocolVersion.value + 1)
            )
        )
    }
}
