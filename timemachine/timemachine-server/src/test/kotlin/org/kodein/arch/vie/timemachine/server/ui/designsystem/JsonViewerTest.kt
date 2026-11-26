@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.kodein.arch.vie.timemachine.server.fixtures.contentTest
import org.kodein.arch.vie.timemachine.server.fixtures.shown
import org.kodein.arch.vie.timemachine.server.ui.common.JsonPath
import org.kodein.arch.vie.timemachine.server.ui.common.JsonViewControls
import org.kodein.arch.vie.timemachine.server.ui.common.pretty


/** The JSON viewer on a small document: what it shows of it, and what its switch and its nodes ask. */
class JsonViewerTest {

    private val document = Json.parseToJsonElement("""{"tasks":[{"title":"Buy milk"}],"count":1}""")

    @Test
    fun `it shows the tree with its open nodes, and a click on a closed one asks to open it`() {
        val toggled = mutableListOf<JsonPath>()
        contentTest(width = 420, height = 300, content = {
            JsonViewer(document, rawText = null, JsonViewControls(), onRawChange = {}, onToggle = { toggled += it }, modifier = Modifier.fillMaxSize())
        }) {
            assertTrue(shown("tasks"))
            assertFalse(shown("title"), "tasks is closed")
            onNodeWithText("tasks").performClick()
            assertEquals(listOf(listOf("tasks")), toggled)
        }
    }

    @Test
    fun `as raw, it shows the text a line a row`() {
        contentTest(width = 420, height = 300, content = {
            JsonViewer(document, rawText = document.pretty(), JsonViewControls(raw = true), onRawChange = {}, onToggle = {}, modifier = Modifier.fillMaxSize())
        }) {
            assertTrue(onAllNodesWithText("\"count\": 1", substring = true).fetchSemanticsNodes().isNotEmpty())
            assertFalse(shown("tasks"), "the tree is not shown")
        }
    }

    @Test
    fun `the switch it hands to its header asks for the text`() {
        var raw: Boolean? = null
        contentTest(width = 420, height = 300, content = {
            JsonViewer(document, rawText = null, JsonViewControls(), onRawChange = { raw = it }, onToggle = {}, modifier = Modifier.fillMaxSize(), header = { rawSwitch -> rawSwitch() })
        }) {
            onNodeWithText("Raw").performClick()
            assertEquals(true, raw)
        }
    }

    @Test
    fun `the switch it hands to its leading items asks for the text too`() {
        var raw: Boolean? = null
        val leading: LazyListScope.(@Composable () -> Unit) -> Unit = { rawSwitch -> item { rawSwitch() } }
        contentTest(width = 420, height = 300, content = {
            JsonViewer(document, rawText = null, JsonViewControls(), onRawChange = { raw = it }, onToggle = {}, modifier = Modifier.fillMaxSize(), leadingItems = leading)
        }) {
            onNodeWithText("Raw").performClick()
            assertEquals(true, raw)
        }
    }

    @Test
    fun `without a document, the list holds the caller's items only`() {
        contentTest(width = 420, height = 300, content = {
            JsonViewer(null, rawText = null, JsonViewControls(), onRawChange = {}, onToggle = {}, modifier = Modifier.fillMaxSize()) { item { Text("No model yet.") } }
        }) {
            assertTrue(shown("No model yet."))
            assertFalse(shown("Raw"), "the switch is where the caller put it: nowhere")
        }
    }
}
