package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/** The inspector's theme: a Material3 scheme per mode, plus the colours and the steps of the type scale Material3 has no slot for. */
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
            content = content,
        )
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
