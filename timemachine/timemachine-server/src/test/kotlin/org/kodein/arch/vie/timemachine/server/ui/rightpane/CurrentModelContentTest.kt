@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.rightpane

import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kodein.arch.vie.VIE.NoSignal
import org.kodein.arch.vie.test.MockVIE
import org.kodein.arch.vie.timemachine.protocol.TimeMachineSequence
import org.kodein.arch.vie.timemachine.server.fixtures.Rendered
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown


/** The pinned current model's content on a Model that has no JSON: why it has none. */
class CurrentModelContentTest {

    private fun current(truncated: Boolean) = MockVIE<CurrentModelVIE.Model, CurrentModelVIE.Intent, NoSignal>(
        CurrentModelVIE.Model(
            projection = CurrentModelProjection(
                typeName = "app.Model", sequence = TimeMachineSequence(1), asString = "Model(…)", json = null, truncated = truncated,
            ),
        ),
    )

    @Test
    fun `a Model sent without its JSON because it was too large says so`() {
        val vie = current(truncated = true)
        contentTest(width = 420, height = 600, content = { vie.Rendered { CurrentModelContent(it) } }) {
            assertTrue(shown(TooLargeNote))
        }
    }

    @Test
    fun `a Model without JSON that was not too large says it is toString only`() {
        val vie = current(truncated = false)
        contentTest(width = 420, height = 600, content = { vie.Rendered { CurrentModelContent(it) } }) {
            assertTrue(shown("toString only — this model has no JSON, so it can't be shown as a tree or replaced."))
            assertFalse(shown(TooLargeNote))
        }
    }
}
