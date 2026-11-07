package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/** How much of what is under a modal sheet shows through its scrim. */
private const val ScrimAlpha = 0.32f

/**
 * [content], and over it a sheet coming in from the left edge while [open]: a scrim that [onDismiss]
 * answers a click on, and [sheet] at [sheetWidth]. Escape dismisses it too.
 *
 * The open state is the caller's: this keeps none, so what opens it and what closes it write the same
 * flag. [sheet] is composed only while the sheet is open or on its way out. It takes the keyboard while
 * it is open; whoever had it asks for it back when [open] turns false.
 */
@Composable
internal fun ModalSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    sheetWidth: Dp,
    sheet: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha))
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            )
        }
        AnimatedVisibility(open, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
            Surface(
                Modifier.width(sheetWidth).fillMaxHeight()
                    .focusRequester(focus).focusable()
                    .onKeyEvent { event ->
                        val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                        if (escape) onDismiss()
                        escape
                    }
                    .semantics { paneTitle = "Sessions" },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
            ) { sheet() }
        }
    }
}
