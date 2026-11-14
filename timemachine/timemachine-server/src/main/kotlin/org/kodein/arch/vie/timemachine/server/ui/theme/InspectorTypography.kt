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

/**
 * The steps of the type scale that Material3's [Typography] has no slot for. With the roles of
 * [inspectorTypography], they are every size a text is drawn at: a view takes one of them, and at most
 * changes its colour, its weight or its line height. A size is changed here, and so is a spacing or a
 * weight: [badge] and [sectionLabel] are the small label with a spacing of their own, and [labelStrong] is
 * the medium label in semi-bold.
 *
 * In Plex, regular:
 *
 * @property note Notes, hints and the status bar.
 * @property body The panes' running text.
 * @property bodyEmphasis A text that weighs more than its neighbours: the composer's warning.
 * @property emptyTitle What a pane says when it has nothing to show.
 * @property screenTitle The title of the screen that waits for an app.
 * @property screenHeadline The title of the screen of a listener that failed.
 *
 * In Plex, medium:
 *
 * @property tag The smallest labels: "initial", "all ViewModels".
 * @property actionSmall An action in a tight place: a row's "Edit & intervene", the Tree | Raw switch.
 * @property action An action's label.
 * @property label Labels, links and the titles of the timeline's markers.
 * @property actionLarge The label of a main action: Send, Retry.
 *
 * In Plex, semi-bold:
 *
 * @property badge The text of a small chip: a kind, a status, "initial". The small label, a little less spaced.
 * @property sectionLabel The label of a section or of a column, written in capitals: "TOSTRING", "SEQ". The
 *   small label, a little more spaced than a badge's.
 * @property labelStrong A label that names an outcome: the title of an intervention's result.
 *
 * In JetBrains Mono, with no line height of their own, for most sit in a row of a fixed height:
 *
 * @property monoMeta Identifiers and counts beside a name.
 * @property monoSecondary Data that accompanies other data: a sequence, an address, a source.
 * @property codeRow The timeline's rows, and data set in a line of text.
 * @property code JSON, type names and what is typed.
 * @property monoTitle A name that titles: a ViewModel's, the address to connect to.
 * @property monoNumeral The number of a step.
 */
@Immutable
data class InspectorTextStyles(
    val note: TextStyle,
    val body: TextStyle,
    val bodyEmphasis: TextStyle,
    val emptyTitle: TextStyle,
    val screenTitle: TextStyle,
    val screenHeadline: TextStyle,
    val tag: TextStyle,
    val actionSmall: TextStyle,
    val action: TextStyle,
    val label: TextStyle,
    val actionLarge: TextStyle,
    val badge: TextStyle,
    val sectionLabel: TextStyle,
    val labelStrong: TextStyle,
    val monoMeta: TextStyle,
    val monoSecondary: TextStyle,
    val codeRow: TextStyle,
    val code: TextStyle,
    val monoTitle: TextStyle,
    val monoNumeral: TextStyle,
)

/**
 * Material3 roles as in the type scale of the design's token table (§07). The roles the scale does not
 * define stay Material's, in Plex: a text that takes one, or no style at all, is still drawn in a
 * bundled font.
 */
internal fun inspectorTypography(fonts: InspectorFonts): Typography = Typography(
    fontFamily = fonts.plex,
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

/**
 * The steps between and around the roles of [typography]: each Plex step is the role next to it at
 * another size (the badge's and the section label's, at their own spacing too, and the strong label's,
 * in its own weight), so it keeps that role's line height, and its weight but for the strong label's.
 */
internal fun inspectorTextStyles(fonts: InspectorFonts, typography: Typography = inspectorTypography(fonts)): InspectorTextStyles {
    fun mono(size: Float) = TextStyle(fontFamily = fonts.mono, fontSize = size.sp, fontWeight = FontWeight.Normal)
    return InspectorTextStyles(
        note = typography.bodySmall.copy(fontSize = 11.5.sp),
        body = typography.bodySmall.copy(fontSize = 12.sp),
        bodyEmphasis = typography.bodySmall.copy(fontSize = 12.5.sp),
        emptyTitle = typography.bodyMedium.copy(fontSize = 13.5.sp),
        screenTitle = typography.bodyMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        screenHeadline = typography.bodyMedium.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
        tag = typography.labelMedium.copy(fontSize = 10.5.sp),
        actionSmall = typography.labelMedium.copy(fontSize = 11.sp),
        action = typography.labelMedium.copy(fontSize = 11.5.sp),
        label = typography.labelMedium.copy(fontSize = 12.sp),
        actionLarge = typography.labelMedium.copy(fontSize = 13.sp),
        badge = typography.labelSmall.copy(letterSpacing = 0.02.sp),
        sectionLabel = typography.labelSmall.copy(letterSpacing = 0.06.sp),
        labelStrong = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        monoMeta = mono(10.5f),
        monoSecondary = mono(11f),
        codeRow = mono(11.5f),
        code = mono(12f),
        monoTitle = mono(12.5f),
        monoNumeral = mono(13f),
    )
}

internal val LocalInspectorFonts = staticCompositionLocalOf<InspectorFonts> { error("No InspectorTheme") }
internal val LocalInspectorTextStyles = staticCompositionLocalOf<InspectorTextStyles> { error("No InspectorTheme") }
