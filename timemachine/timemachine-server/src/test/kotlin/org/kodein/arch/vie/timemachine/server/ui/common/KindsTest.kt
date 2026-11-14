package org.kodein.arch.vie.timemachine.server.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo
import org.kodein.arch.vie.timemachine.server.ui.theme.DarkInspectorColors
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph

class KindsTest {

    @Test
    fun `an intent goes into the ViewModel, a model and a signal come out of it`() {
        assertEquals(
            listOf(KindStyle.Direction.OutOf, KindStyle.Direction.Into, KindStyle.Direction.OutOf),
            VIEObjectKind.entries.map { it.style().direction },
        )
        with(KindStyle.Direction.Into) { assertEquals(Triple(Glyph.Login, false, "into the ViewModel"), Triple(glyph, mirrored, label)) }
        with(KindStyle.Direction.OutOf) { assertEquals(Triple(Glyph.Logout, true, "out of the ViewModel"), Triple(glyph, mirrored, label)) }
    }

    @Test
    fun `each kind is sent with its own verb and glyph`() {
        assertEquals(listOf("Replace model", "Post intent", "Emit signal"), VIEObjectKind.entries.map { it.style().verb })
        assertEquals(listOf("replaced", "posted", "emitted"), VIEObjectKind.entries.map { it.style().participle })
        assertEquals(listOf(Glyph.SwapHoriz, Glyph.Login, Glyph.Campaign), VIEObjectKind.entries.map { it.style().sendGlyph })
    }

    @Test
    fun `an editor with nothing observed names a model by its kind and the others by their type`() {
        assertEquals("no Model observed yet — starting from {}", VIEObjectKind.Model.style().unobservedNote("TaskList"))
        assertEquals("no AddTask observed yet — starting from {}", VIEObjectKind.Intent.style().unobservedNote("AddTask"))
        assertEquals("no Toast observed yet — starting from {}", VIEObjectKind.Signal.style().unobservedNote("Toast"))
    }

    @Test
    fun `a kind picks its own type info and colours`() {
        val info = VIEViewModelInfo(
            id = Uuid.parse("3f2a1111-2222-3333-4444-5555aaaac91e"),
            typeName = "org.app.TaskListVM",
            model = VIETypeInfo("org.app.TaskList", isSerializable = true),
            intent = VIETypeInfo("org.app.TaskIntent", isSerializable = true),
            signal = VIETypeInfo("org.app.TaskSignal", isSerializable = false),
        )
        assertEquals(listOf("org.app.TaskList", "org.app.TaskIntent", "org.app.TaskSignal"), VIEObjectKind.entries.map { info.type(it).typeName })
        val colors = DarkInspectorColors
        assertEquals(listOf(colors.model, colors.intent, colors.signal), VIEObjectKind.entries.map { it.style().color(colors) })
        assertEquals(listOf(colors.modelContainer, colors.intentContainer, colors.signalContainer), VIEObjectKind.entries.map { it.style().container(colors) })
    }
}
