@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertTrue
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest


/** The raw view of a long JSON text: only the lines in view are read, and they are coloured. */
class RawJsonTest {

    @Test
    fun `only the lines in view of a long text are read, and they are coloured`() {
        var reads = 0
        val lines = object : AbstractList<String>() {
            override val size = 100_000
            override fun get(index: Int): String = "  \"key$index\": $index,".also { reads++ }
        }
        contentTest(width = 420, height = 300, content = { LazyColumn(Modifier.fillMaxSize()) { rawJson(lines) } }) {
            assertTrue(reads in 1..40, "$reads lines of 100 000 were read, for a view of 16")
            val first = onNodeWithText("\"key0\": 0,", substring = true).fetchSemanticsNode()
            assertTrue(first.config[SemanticsProperties.Text].single().spanStyles.isNotEmpty(), "a line in view is coloured")
        }
    }
}
