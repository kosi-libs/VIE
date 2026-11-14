package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The inspector's theme: a Material3 scheme per mode, plus the colours and the steps of the type scale Material3 has no slot for,
 * and the content colour Material's components fall back on (see the comment on [LocalContentColor] below).
 */
@Composable
fun InspectorTheme(dark: Boolean, content: @Composable () -> Unit) {
    val fonts = rememberInspectorFonts()
    val typography = remember(fonts) { inspectorTypography(fonts) }
    val text = remember(fonts, typography) { inspectorTextStyles(fonts, typography) }
    CompositionLocalProvider(
        LocalInspectorColors provides if (dark) DarkInspectorColors else LightInspectorColors,
        LocalInspectorFonts provides fonts,
        LocalInspectorTextStyles provides text,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkInspectorScheme else LightInspectorScheme,
            typography = typography,
            shapes = InspectorShapes,
        ) {
            // The scrollbars of the panes: the outline of a rule at rest, the one of a hovered grip under the pointer.
            val scrollbars = ScrollbarStyle(
                minimalHeight = 24.dp,
                thickness = InspectorDimens.scrollbarThumb,
                shape = InspectorPillShape,
                hoverDurationMillis = 150,
                unhoverColor = MaterialTheme.colorScheme.outlineVariant,
                hoverColor = InspectorTheme.colors.strongOutline,
            )
            CompositionLocalProvider(
                LocalScrollbarStyle provides scrollbars,
                // The content colour, which Material draws its ripple in: what shows the pointer over an action (at 8 %),
                // pressing it or the focus on it (at 10 %). The Material components that draw a container (a Surface, a
                // tab, a chip...) provide it, and none is above the panes, so the ripple falls back on Material's default
                // black. Black darkens the panes' light surface by 20 levels of 255 under the pointer, but their dark one
                // by 2 to 3 only: the dark theme provides its onSurface, which lightens that surface by 16 levels under
                // the pointer and 20 pressed. The light theme keeps Material's black: onSurface would make the ripple 2 to
                // 3 levels weaker there (18 levels instead of 20 under the pointer, 23 instead of 26 pressed).
                LocalContentColor provides if (dark) MaterialTheme.colorScheme.onSurface else Color.Black,
                content = content,
            )
        }
    }
}

object InspectorTheme {
    val colors: InspectorColors
        @Composable @ReadOnlyComposable get() = LocalInspectorColors.current

    val fonts: InspectorFonts
        @Composable @ReadOnlyComposable get() = LocalInspectorFonts.current

    val text: InspectorTextStyles
        @Composable @ReadOnlyComposable get() = LocalInspectorTextStyles.current
}
