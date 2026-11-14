package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import org.kodein.arch.vie.timemachine.server.ui.common.JsonPath
import org.kodein.arch.vie.timemachine.server.ui.common.JsonViewControls
import org.kodein.arch.vie.timemachine.server.ui.common.ScrollbarBox
import org.kodein.arch.vie.timemachine.server.ui.common.TreeRawSwitch
import org.kodein.arch.vie.timemachine.server.ui.common.jsonTree
import org.kodein.arch.vie.timemachine.server.ui.common.rawJson
import org.kodein.arch.vie.timemachine.server.ui.common.rememberJsonLines
import org.kodein.arch.vie.timemachine.server.ui.common.treeLines


/** How a [JsonViewer]'s lines sit on its pane. */
internal enum class JsonViewerVariant {
    /** Straight on the pane, as the pinned Model's. */
    Plain,

    /** In a rounded, outlined code frame, which each line draws its part of, as the Detail tab's. */
    Framed,
}

/**
 * A JSON document as a collapsible tree or as numbered text, at the end of a list that scrolls under its
 * scrollbar: the Detail tab's and the pinned Model's. The list only composes the lines in view, however
 * long the document. [controls] say which of the two shows and which nodes of the tree are open; the
 * viewer asks to change them through [onRawChange] and [onToggle].
 *
 * The viewer draws the Tree | Raw switch, and hands it to [header] and to [leadingItems]: the caller
 * places it once, in one of them, where its design wants it.
 *
 * @param json The document; `null` when there is none, and the list then holds [leadingItems] only.
 * @param rawText [json] as text, which the raw view shows: its pane computes it, off the main thread,
 *   while [controls] ask for it, and it is `null` until then.
 * @param variant Whether the lines sit straight on the pane or in a code frame.
 * @param changed The nodes of the tree tinted as changed, at the strength of [highlight].
 * @param highlight How strongly [changed] is tinted, from 1 to 0, read only when a line is drawn: an
 *   animation of it redraws the tinted lines and composes nothing. `null` when nothing is tinted.
 * @param contentPadding Around the list's content: [leadingItems] and the document's lines.
 * @param header What stays above the list, given the switch to place.
 * @param leadingItems What the list holds before the document, and scrolls with it, given the switch to place.
 */
@Composable
internal fun JsonViewer(
    json: JsonElement?,
    rawText: String?,
    controls: JsonViewControls,
    onRawChange: (Boolean) -> Unit,
    onToggle: (JsonPath) -> Unit,
    modifier: Modifier = Modifier,
    variant: JsonViewerVariant = JsonViewerVariant.Plain,
    changed: Set<JsonPath> = emptySet(),
    highlight: State<Float>? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: @Composable (rawSwitch: @Composable () -> Unit) -> Unit = {},
    leadingItems: LazyListScope.(rawSwitch: @Composable () -> Unit) -> Unit = {},
) {
    val raw = controls.raw
    // The lines of the one that shows, the tree or the text: the other has none.
    val treeLines = remember(json, controls.expanded, raw) { if (json == null || raw) emptyList() else treeLines(json, controls.expanded) }
    val textLines = rememberJsonLines(if (raw) rawText.orEmpty() else "")
    val rawSwitch: @Composable () -> Unit = { TreeRawSwitch(raw, onChange = onRawChange) }
    val framed = variant == JsonViewerVariant.Framed
    // The tree's lines are given it from the list's block, which remembers nothing: one lambda for the
    // viewer's life, calling the latest onToggle, keeps them from composing again at each run.
    val currentOnToggle by rememberUpdatedState(onToggle)
    val toggle = remember { { path: JsonPath -> currentOnToggle(path) } }
    val list = rememberLazyListState()
    Column(modifier) {
        header(rawSwitch)
        ScrollbarBox(rememberScrollbarAdapter(list), Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = contentPadding) {
                leadingItems(rawSwitch)
                if (json != null) {
                    if (raw) rawJson(textLines, framed = framed)
                    else jsonTree(treeLines, changed = changed, highlight = highlight, framed = framed, onToggle = toggle)
                }
            }
        }
    }
}
