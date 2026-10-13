package org.kodein.arch.vie.timemachine.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement


class TimeMachineProtocolVersionTest {

    @Test
    fun encodesAsABareNumber() {
        assertEquals(
            JsonPrimitive(1),
            TimeMachineJson.encodeToJsonElement(TimeMachineProtocolVersion(1)),
        )
    }
}
