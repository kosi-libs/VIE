package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** The inspector's theme: a Material3 scheme per mode, plus the roles Material3 has no slot for. */
@Composable
fun InspectorTheme(dark: Boolean, content: @Composable () -> Unit) {
    val fonts = rememberInspectorFonts()
    CompositionLocalProvider(
        LocalInspectorColors provides if (dark) DarkInspectorColors else LightInspectorColors,
        LocalInspectorFonts provides fonts,
        LocalInspectorTextStyles provides inspectorTextStyles(fonts),
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkInspectorScheme else LightInspectorScheme,
            typography = inspectorTypography(fonts),
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
