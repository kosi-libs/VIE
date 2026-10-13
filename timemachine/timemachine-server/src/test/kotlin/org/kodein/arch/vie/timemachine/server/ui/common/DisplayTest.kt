package org.kodein.arch.vie.timemachine.server.ui.common

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonNull
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.server.store.Intervention
import org.kodein.arch.vie.timemachine.server.store.ViewModelRecord
import org.kodein.arch.vie.timemachine.server.store.t0


class DisplayTest {

    @Test
    fun aSessionIdIsShortenedToItsFirstAndLastFourDigits() {
        assertEquals("3f2a…c91e", shortSessionId(Uuid.parse("3f2a1111-2222-3333-4444-5555aaaac91e")))
    }

    @Test
    fun aViewModelIdIsItsFirstFourDigits() {
        assertEquals("3f2a", shortViewModelId(Uuid.parse("3f2a1111-2222-3333-4444-5555aaaac91e")))
    }

    @Test
    fun aDottedSerialNameIsSplitAfterItsLastDot() {
        assertEquals(SerialNameParts("org.app.TaskListVIE.Intent.", "DeleteTask"), splitSerialName("org.app.TaskListVIE.Intent.DeleteTask"))
    }

    @Test
    fun aNameWithNoDotIsAllEmphasis() {
        assertEquals(SerialNameParts("", "circle"), splitSerialName("circle"))
    }

    @Test
    fun aTimeIsPrintedWithMilliseconds() {
        assertEquals("08:00:00.042", formatTime(t0 + 42.milliseconds, ZoneOffset.UTC))
    }

    @Test
    fun interventionsAreNumberedInTheOrderTheyWereAsked() {
        val ids = List(3) { Uuid.random() }
        val vm = ViewModelRecord(
            id = Uuid.random(), info = null, firstSeenAt = t0,
            interventions = ids.associateWith { Intervention(it, VIEObjectKind.Model, "M", JsonNull, t0, sent = true) },
        )

        assertEquals(listOf(1, 2, 3), ids.map { vm.interventionNumber(it) })
        assertEquals(null, vm.interventionNumber(Uuid.random()))
    }
}
