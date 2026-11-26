package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorAlpha


/**
 * [content], and over it a sheet coming in from the left edge while [open]: a scrim that [onDismiss]
 * answers a click on, and [sheet] at [sheetWidth], which [title] names as a pane. Escape dismisses it too,
 * wherever the focus is within the sheet and unless something there takes it first.
 *
 * The open state is the caller's: this keeps none, so what opens it and what closes it write the same
 * flag. [sheet] is composed only while the sheet is open or on its way out. It takes the keyboard while
 * it is open; whoever had it asks for it back when [open] turns false.
 *
 * While it is open it is modal: the focus cannot leave the sheet, nor enter [content], which is hidden from
 * accessibility, so that neither Tab nor a screen reader gets behind the scrim. The scrim itself is a button named
 * "Close [title]". All of it follows [open], not the sheet being composed: it still is while it slides out,
 * and what is behind it is the caller's again by then.
 */
@Composable
internal fun ModalSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    title: String,
    sheetWidth: Dp,
    sheet: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            // After what has the focus within the sheet: a key it takes (Escape in a menu) is not a dismissal.
            .onKeyEvent { event ->
                val escape = open && event.type == KeyEventType.KeyDown && event.key == Key.Escape
                if (escape) onDismiss()
                escape
            },
    ) {
        Box(
            Modifier.fillMaxSize().then(
                if (open) Modifier.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup().semantics { hideFromAccessibility() } else Modifier,
            ),
        ) { content() }
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = InspectorAlpha.scrim))
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                    .semantics {
                        contentDescription = "Close $title"
                        onClick("Close $title") { onDismiss(); true }
                    },
            )
        }
        AnimatedVisibility(open, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
            Surface(
                Modifier.width(sheetWidth).fillMaxHeight()
                    .focusRequester(focus)
                    .then(if (open) Modifier.focusProperties { onExit = { cancelFocusChange() } } else Modifier)
                    .focusable()
                    .semantics { paneTitle = title },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
            ) { sheet() }
        }
    }
}
