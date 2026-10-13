package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.kodein.arch.vie.timemachine.server.resources.IBMPlexSans_Medium
import org.kodein.arch.vie.timemachine.server.resources.IBMPlexSans_Regular
import org.kodein.arch.vie.timemachine.server.resources.IBMPlexSans_SemiBold
import org.kodein.arch.vie.timemachine.server.resources.JetBrainsMono_Medium
import org.kodein.arch.vie.timemachine.server.resources.JetBrainsMono_Regular
import org.kodein.arch.vie.timemachine.server.resources.JetBrainsMono_SemiBold
import org.kodein.arch.vie.timemachine.server.resources.MaterialSymbolsRounded
import org.kodein.arch.vie.timemachine.server.resources.Res

/** The three bundled families: Plex for the chrome, JetBrains Mono for data, Symbols for icons. */
@Immutable
data class InspectorFonts(val plex: FontFamily, val mono: FontFamily, val symbols: FontFamily)

@Composable
internal fun rememberInspectorFonts(): InspectorFonts {
    val plex = FontFamily(
        Font(Res.font.IBMPlexSans_Regular, FontWeight.Normal),
        Font(Res.font.IBMPlexSans_Medium, FontWeight.Medium),
        Font(Res.font.IBMPlexSans_SemiBold, FontWeight.SemiBold),
    )
    val mono = FontFamily(
        Font(Res.font.JetBrainsMono_Regular, FontWeight.Normal),
        Font(Res.font.JetBrainsMono_Medium, FontWeight.Medium),
        Font(Res.font.JetBrainsMono_SemiBold, FontWeight.SemiBold),
    )
    val symbols = FontFamily(Font(Res.font.MaterialSymbolsRounded, FontWeight.Normal))
    return remember(plex, mono, symbols) { InspectorFonts(plex, mono, symbols) }
}

/** The two mono roles Material3's [Typography] has no slot for (§07: `codeRow`, `codeBlock`). */
@Immutable
data class InspectorTextStyles(val codeRow: TextStyle, val codeBlock: TextStyle)

/** Material3 roles as in the type scale of the design's token table (§07). */
internal fun inspectorTypography(fonts: InspectorFonts): Typography = Typography(
    titleMedium = TextStyle(fontFamily = fonts.mono, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = fonts.plex, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontFamily = fonts.plex, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = fonts.plex, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontFamily = fonts.plex, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(
        fontFamily = fonts.plex, fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.04.em,
    ),
)

internal fun inspectorTextStyles(fonts: InspectorFonts) = InspectorTextStyles(
    codeRow = TextStyle(fontFamily = fonts.mono, fontSize = 11.5.sp, lineHeight = 28.sp),
    codeBlock = TextStyle(fontFamily = fonts.mono, fontSize = 12.sp, lineHeight = 19.sp),
)

internal val LocalInspectorFonts = staticCompositionLocalOf<InspectorFonts> { error("No InspectorTheme") }
internal val LocalInspectorTextStyles = staticCompositionLocalOf<InspectorTextStyles> { error("No InspectorTheme") }
