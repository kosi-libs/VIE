package org.kodein.arch.vie.timemachine.client

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


class TimeMachineConfigTest {

    private val invalidPorts = listOf(0, -1, 65536)
    private val invalidBufferSizes = listOf(0, -1)

    @AfterTest
    fun stopProbe() {
        TimeMachine.stop()
    }

    @Test
    fun anInvalidConfigurationIsBuiltWithoutThrowing() {
        // As a release build does, with values read from a build property: the probe is off, and
        // nothing it is configured with may throw.
        for (enabled in listOf(true, false)) {
            for (port in invalidPorts) assertEquals(port, TimeMachineConfig(appName = "Test", port = port, enabled = enabled).port)
            for (bufferSize in invalidBufferSizes) {
                assertEquals(bufferSize, TimeMachineConfig(appName = "Test", enabled = enabled, bufferSize = bufferSize).bufferSize)
            }
        }
    }

    @Test
    fun aPortOutsideTheValidRangeIsAProblem() {
        for (port in invalidPorts) {
            val problem = assertNotNull(TimeMachineConfig(appName = "Test", port = port).problem, "port $port")
            assertTrue("port" in problem && port.toString() in problem, problem)
        }
    }

    @Test
    fun thePortBoundsAreAccepted() {
        for (port in listOf(1, 65535)) {
            assertNull(TimeMachineConfig(appName = "Test", port = port).problem, "port $port")
        }
    }

    @Test
    fun aNonPositiveBufferSizeIsAProblem() {
        for (bufferSize in invalidBufferSizes) {
            val problem = assertNotNull(TimeMachineConfig(appName = "Test", bufferSize = bufferSize).problem, "bufferSize $bufferSize")
            assertTrue("bufferSize" in problem && bufferSize.toString() in problem, problem)
        }
    }

    @Test
    fun theDefaultConfigurationHasNoProblem() {
        assertNull(TimeMachineConfig(appName = "Test").problem)
    }

    @Test
    fun startingAnInvalidConfigurationPrintsItAndStartsNoProbe() {
        for (port in invalidPorts) {
            TimeMachine.start(TimeMachineConfig(appName = "Test", port = port))
            assertFalse(TimeMachine.isStarted, "port $port")
        }
        for (bufferSize in invalidBufferSizes) {
            TimeMachine.start(TimeMachineConfig(appName = "Test", bufferSize = bufferSize))
            assertFalse(TimeMachine.isStarted, "bufferSize $bufferSize")
        }
    }

    @Test
    fun startingADisabledInvalidConfigurationDoesNothing() {
        TimeMachine.start(TimeMachineConfig(appName = "Test", port = 0, enabled = false, bufferSize = 0))
        assertFalse(TimeMachine.isStarted)
    }
}
