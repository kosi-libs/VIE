package org.kodein.arch.vie.timemachine.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class TimeMachineConfigTest {

    @Test
    fun aPortOutsideTheValidRangeIsRejected() {
        for (port in listOf(0, -1, 65536)) {
            assertFailsWith<IllegalArgumentException>("port $port") { TimeMachineConfig(appName = "Test", port = port) }
        }
    }

    @Test
    fun thePortBoundsAreAccepted() {
        for (port in listOf(1, 65535)) {
            assertEquals(port, TimeMachineConfig(appName = "Test", port = port).port)
        }
    }

    @Test
    fun aNonPositiveBufferSizeIsRejected() {
        for (bufferSize in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException>("bufferSize $bufferSize") {
                TimeMachineConfig(appName = "Test", bufferSize = bufferSize)
            }
        }
    }
}
