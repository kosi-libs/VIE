package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import org.kodein.arch.vie.timemachine.server.ui.common.rememberCopy
import org.kodein.arch.vie.timemachine.server.ui.theme.Glyph
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/** How long a copy stays a check once it has copied. */
private const val CopiedMillis = 1500L

/**
 * A copy that says, for a moment, that it copied: [copied] from each [copy] until [CopiedMillis] after the
 * last one. [rememberCopyFeedback] gives one; a copy button draws its [glyph].
 */
@Stable
internal class CopyFeedback(private val clipboard: (String) -> Unit) {

    /** Whether the last copy was a moment ago. */
    var copied: Boolean by mutableStateOf(false)
        private set

    /** How many copies there were: each one starts the check's moment again. */
    var copies: Int by mutableIntStateOf(0)
        private set

    /** What a copy button draws: a check while [copied], the copy glyph otherwise. */
    val glyph: Glyph get() = if (copied) Glyph.Check else Glyph.ContentCopy

    /** Puts [text] on the clipboard, and starts the check's moment. */
    fun copy(text: String) {
        clipboard(text)
        copied = true
        copies++
    }

    /** Waits out the moment of the last copy, if there was one, then ends the check. */
    suspend fun holdCheck() {
        if (copies == 0) return
        delay(CopiedMillis)
        copied = false
    }
}

/** A [CopyFeedback] on the window's clipboard, whose check ends on its own. */
@Composable
internal fun rememberCopyFeedback(): CopyFeedback {
    val copy = rememberCopy()
    val feedback = remember(copy) { CopyFeedback(copy) }
    // Keyed by the count, so that a copy while the check shows starts its moment again.
    LaunchedEffect(feedback, feedback.copies) { feedback.holdCheck() }
    return feedback
}

/**
 * The copy glyph: a click puts [text] on the clipboard, and the glyph is a check for a moment.
 * [text] is read at the click only, for it can be a whole document to print. [description] is what it
 * says it copies.
 *
 * @param interactionSource What it reports its hover and press to, and shows them from; see `action`.
 */
@Composable
internal fun CopyAction(
    text: () -> String,
    description: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val feedback = rememberCopyFeedback()
    IconAction(
        feedback.glyph, description, onClick = { feedback.copy(text()) }, modifier = modifier,
        tint = if (feedback.copied) InspectorTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
        interactionSource = interactionSource,
    )
}
