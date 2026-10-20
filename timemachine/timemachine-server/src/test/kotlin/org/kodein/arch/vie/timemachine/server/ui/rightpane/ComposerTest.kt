package org.kodein.arch.vie.timemachine.server.ui.rightpane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.kodein.arch.vie.timemachine.protocol.TimeMachineClientMessage
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSession
import org.kodein.arch.vie.timemachine.protocol.VIEObjectKind
import org.kodein.arch.vie.timemachine.protocol.VIESignalDelivery
import org.kodein.arch.vie.timemachine.protocol.VIETypeInfo
import org.kodein.arch.vie.timemachine.protocol.VIEViewModelInfo
import org.kodein.arch.vie.timemachine.server.store.ConnectionId
import org.kodein.arch.vie.timemachine.server.store.TimeMachineEvent
import org.kodein.arch.vie.timemachine.server.store.TimeMachineState
import org.kodein.arch.vie.timemachine.server.store.after
import org.kodein.arch.vie.timemachine.server.store.closed
import org.kodein.arch.vie.timemachine.server.store.hello
import org.kodein.arch.vie.timemachine.server.store.observed
import org.kodein.arch.vie.timemachine.server.store.receive
import org.kodein.arch.vie.timemachine.server.store.seq
import org.kodein.arch.vie.timemachine.server.store.session
import org.kodein.arch.vie.timemachine.server.store.t0
import org.kodein.arch.vie.timemachine.server.store.vm


class ComposerTest {

    private val c1 = ConnectionId(1)
    private val session = TimeMachineSession(Uuid.random(), "App")
    private val vm = Uuid.random()

    private fun plain(name: String) = VIETypeInfo(name, isSerializable = true)
    private fun sealed(vararg subs: String) = VIETypeInfo("app.Sealed", isSerializable = true, sealedSubTypes = subs.toList())

    private fun state(
        model: VIETypeInfo = plain("app.Model"),
        intent: VIETypeInfo = plain("app.Intent"),
        signal: VIETypeInfo = plain("app.Signal"),
        vararg more: TimeMachineEvent,
    ): TimeMachineState = TimeMachineState().after(
        hello(c1, session),
        receive(c1, TimeMachineClientMessage.ViewModelStarted(VIEViewModelInfo(vm, "app.VM", model, intent, signal), t0, seq(0))),
        *more,
    ).state

    private fun TimeMachineState.target(kind: VIEObjectKind, chosen: String? = null, selected: Pair<VIEObjectKind, String>? = null) =
        composerTarget(session(session.id), vm(session.id, vm), kind, chosen, selected)

    // Blocks

    @Test
    fun anUnknownViewModelBlocksBeforeAnythingElse() {
        val s = TimeMachineState().after(hello(c1, session), receive(c1, observed(vm, 0))).state
        val disconnected = s.after(TimeMachineEvent.Disconnected(c1, t0)).state

        assertEquals(ComposerBlock.UnknownViewModel, composerBlock(disconnected.session(session.id), disconnected.vm(session.id, vm)))
        assertEquals(ComposerBlock.UnknownViewModel, composerBlock(null, null))
        assertEquals(ComposerBlock.UnknownViewModel, composerBlock(s.session(session.id), null))
    }

    @Test
    fun aDisconnectedSessionBlocksBeforeAClosedViewModel() {
        val s = state(more = arrayOf(receive(c1, closed(vm, 1)))).after(TimeMachineEvent.Disconnected(c1, t0)).state

        assertEquals(ComposerBlock.SessionDisconnected, composerBlock(s.session(session.id), s.vm(session.id, vm)))
    }

    @Test
    fun aClosedViewModelBlocks() {
        val s = state(more = arrayOf(receive(c1, closed(vm, 1))))

        assertEquals(ComposerBlock.ViewModelClosed, s.target(VIEObjectKind.Intent).block)
    }

    @Test
    fun nothingBlocksALiveViewModelOfAConnectedSession() {
        assertNull(state().target(VIEObjectKind.Intent).block)
    }

    // Kinds

    @Test
    fun onlyTheSerializableRolesAreEnabled() {
        val s = state(intent = VIETypeInfo("app.Intent", isSerializable = false))

        assertEquals(setOf(VIEObjectKind.Model, VIEObjectKind.Signal), s.target(VIEObjectKind.Model).enabledKinds)
    }

    // Type list and fallback prefill, for the four shapes

    @Test
    fun aSealedIntentOffersItsSubtypesAndPrefillsTheirTag() {
        val target = state(intent = sealed("app.Add", "app.Delete")).target(VIEObjectKind.Intent)

        assertEquals(listOf("app.Add", "app.Delete"), target.types)
        assertEquals("app.Add", target.type)
        assertEquals(ComposerPrefill("""{"#type": "app.Add"}""", PrefillSource.SealedTemplate), target.prefill)
    }

    @Test
    fun aPlainModelOffersItsOwnTypeAndPrefillsAnEmptyObject() {
        val target = state(model = plain("app.Model")).target(VIEObjectKind.Model)

        assertEquals(listOf("app.Model"), target.types)
        assertEquals(ComposerPrefill("{}", PrefillSource.EmptyObject), target.prefill)
    }

    @Test
    fun aSealedModelOffersItsSubtypesAndPrefillsTheirTag() {
        val target = state(model = sealed("app.Loading", "app.Loaded")).target(VIEObjectKind.Model, chosen = "app.Loaded")

        assertEquals(listOf("app.Loading", "app.Loaded"), target.types)
        assertEquals("app.Loaded", target.type)
        assertEquals(ComposerPrefill("""{"#type": "app.Loaded"}""", PrefillSource.SealedTemplate), target.prefill)
    }

    @Test
    fun aPlainIntentOffersItsOwnTypeAndPrefillsAnEmptyObject() {
        val target = state(intent = plain("app.Refresh")).target(VIEObjectKind.Intent)

        assertEquals(listOf("app.Refresh"), target.types)
        assertEquals("app.Refresh", target.type)
        assertEquals(ComposerPrefill("{}", PrefillSource.EmptyObject), target.prefill)
    }

    @Test
    fun aSealedRoleWithNoSubtypeOffersNothingToSend() {
        val target = state(intent = sealed()).target(VIEObjectKind.Intent)

        assertEquals(emptyList(), target.types)
        assertNull(target.type)
        assertNull(target.prefill)
        assertFalse(target.canSend(parseJson("{}")))
    }

    // Chosen type

    @Test
    fun theChosenTypeIsTheExplicitChoiceThenTheSelectedEntryThenTheFirst() {
        val s = state(intent = sealed("app.A", "app.B", "app.C"))

        assertEquals("app.A", s.target(VIEObjectKind.Intent).type)
        assertEquals("app.B", s.target(VIEObjectKind.Intent, selected = VIEObjectKind.Intent to "app.B").type)
        assertEquals("app.C", s.target(VIEObjectKind.Intent, chosen = "app.C", selected = VIEObjectKind.Intent to "app.B").type)
    }

    @Test
    fun aSelectedEntryOfAnotherKindOrTypeIsIgnored() {
        val s = state(intent = sealed("app.A", "app.B"))

        assertEquals("app.A", s.target(VIEObjectKind.Intent, selected = VIEObjectKind.Model to "app.B").type)
        assertEquals("app.A", s.target(VIEObjectKind.Intent, selected = VIEObjectKind.Intent to "app.Z").type)
        assertEquals("app.A", s.target(VIEObjectKind.Intent, chosen = "app.Z").type)
    }

    // Observed prefill

    @Test
    fun theLatestObservedJsonOfTheTypePrefillsWithItsSequence() {
        val s = state(
            intent = sealed("app.A", "app.B"),
            more = arrayOf(
                receive(c1, observed(vm, 1, VIEObjectKind.Intent, "app.B", JsonObject(mapOf("n" to JsonPrimitive(1))))),
                receive(c1, observed(vm, 2, VIEObjectKind.Intent, "app.B", JsonObject(mapOf("n" to JsonPrimitive(2))))),
            ),
        )

        val target = s.target(VIEObjectKind.Intent, chosen = "app.B")

        assertEquals(PrefillSource.Observed(seq(2)), target.prefill?.source)
        assertEquals(JsonObject(mapOf("n" to JsonPrimitive(2))), (parseJson(target.prefill!!.json) as JsonParse.Valid).element)
        // Another type of the same kind has nothing observed.
        assertEquals(PrefillSource.SealedTemplate, s.target(VIEObjectKind.Intent, chosen = "app.A").prefill?.source)
    }

    // Signal note

    @Test
    fun aKnownEffectIsNotedAndAKnownEventIsNot() {
        val s = state(
            signal = sealed("app.Toast", "app.Navigate", "app.Never"),
            more = arrayOf(
                receive(c1, observed(vm, 1, VIEObjectKind.Signal, "app.Navigate", delivery = VIESignalDelivery.Effect)),
                receive(c1, observed(vm, 2, VIEObjectKind.Signal, "app.Toast", delivery = VIESignalDelivery.Event)),
            ),
        )

        assertEquals(SignalNote.Effect, s.target(VIEObjectKind.Signal, chosen = "app.Navigate").signalNote)
        assertNull(s.target(VIEObjectKind.Signal, chosen = "app.Toast").signalNote)
    }

    @Test
    fun aSignalTypeNeverObservedGetsTheUncertainNote() {
        val target = state(signal = sealed("app.Never")).target(VIEObjectKind.Signal)

        assertEquals(SignalNote.Unknown, target.signalNote)
        assertTrue(SignalNote.Unknown.text.startsWith("If this is an Effect"))
    }

    @Test
    fun theSignalNoteNeverDisablesSend() {
        val target = state(signal = sealed("app.Never")).target(VIEObjectKind.Signal)

        assertTrue(target.canSend(parseJson("""{"#type":"app.Never"}""")))
    }

    @Test
    fun onlyASignalHasANote() {
        assertNull(state().target(VIEObjectKind.Intent).signalNote)
    }

    // Parsing and Send

    @Test
    fun aSyntaxErrorIsLocatedByLineAndColumn() {
        val invalid = assertIs<JsonParse.Invalid>(parseJson("{\n  \"a\": ,\n}"))

        assertEquals(2, invalid.line)
        assertEquals(8, invalid.column)
    }

    @Test
    fun anUnclosedObjectIsReportedAtTheEndOfTheText() {
        val invalid = assertIs<JsonParse.Invalid>(parseJson("{\"a\": 1"))

        assertEquals(1, invalid.line)
        assertEquals(8, invalid.column)
    }

    @Test
    fun sendNeedsValidJsonAnEnabledKindAndNothingBlocking() {
        val good = state()
        assertTrue(good.target(VIEObjectKind.Intent).canSend(parseJson("{}")))
        assertFalse(good.target(VIEObjectKind.Intent).canSend(parseJson("{")))

        val notSerializable = state(intent = VIETypeInfo("app.Intent", isSerializable = false))
        assertFalse(notSerializable.target(VIEObjectKind.Intent).canSend(parseJson("{}")))

        val closed = state(more = arrayOf(receive(c1, closed(vm, 1))))
        assertFalse(closed.target(VIEObjectKind.Intent).canSend(parseJson("{}")))
    }
}
