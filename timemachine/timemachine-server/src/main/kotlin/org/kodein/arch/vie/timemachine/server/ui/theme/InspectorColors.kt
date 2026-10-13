package org.kodein.arch.vie.timemachine.server.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The roles Material3's [ColorScheme] has no slot for. Values are transcribed from the design's
 * token table (`VIE TimeMachine.dc.html`, §07) and from the theme block of `Inspector.dc.html`.
 */
@Immutable
data class InspectorColors(
    val model: Color,
    val modelContainer: Color,
    val intent: Color,
    val intentContainer: Color,
    val signal: Color,
    val signalContainer: Color,

    val intervened: Color,
    val intervenedContainer: Color,
    /** The tint of an intervened row. */
    val intervenedRow: Color,

    /** The Gap marker: loud, solid rules over a hatch. */
    val gap: Color,
    val gapContainer: Color,
    /** The darker stripe of the Gap hatch, drawn over [gapContainer]. */
    val gapStripe: Color,
    val possiblyIncomplete: Color,
    val evicted: Color,
    val historyUnavailable: Color,
    val historyUnavailableContainer: Color,

    val success: Color,
    val successContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val timeout: Color,
    val timeoutContainer: Color,
    /** The background of an "accepted, not observed" result; its icon is [success]. */
    val notObserved: Color,
    /** The icon of a "not sent" result, on `surfaceContainerHigh`. */
    val notSent: Color,

    val selectedRow: Color,
    val rowLine: Color,
    /** The outline of a hovered grip or a focused field, stronger than `outlineVariant`. */
    val strongOutline: Color,

    val jsonKey: Color,
    val jsonString: Color,
    val jsonNumber: Color,
    val jsonLiteral: Color,
)

private fun hex(argb: Long) = Color(0xFF000000 or argb)

internal val DarkInspectorColors = InspectorColors(
    model = hex(0x86B7FF), modelContainer = hex(0x1C2A40),
    intent = hex(0xF2B661), intentContainer = hex(0x3A2A12),
    signal = hex(0xC9A6FF), signalContainer = hex(0x2E2342),
    intervened = hex(0xFF84BB), intervenedContainer = hex(0x4A1A33), intervenedRow = hex(0x221419),
    gap = hex(0xFF6F66), gapContainer = hex(0x3A1715), gapStripe = hex(0x2A1210),
    possiblyIncomplete = hex(0xE9A39A), evicted = hex(0x98A1AD),
    historyUnavailable = hex(0x9DB6CF), historyUnavailableContainer = hex(0x1B2530),
    success = hex(0x72D59E), successContainer = hex(0x163426),
    error = hex(0xFF6F66), errorContainer = hex(0x3A1715),
    timeout = hex(0xD9C08A), timeoutContainer = hex(0x2F2A1C),
    notObserved = hex(0x22262C), notSent = hex(0x7F8793),
    selectedRow = hex(0x17332F), rowLine = hex(0x191C21), strongOutline = hex(0x3A4049),
    jsonKey = hex(0xD5DAE1), jsonString = hex(0x8FD6C6), jsonNumber = hex(0xE9B98F), jsonLiteral = hex(0xB9A8F0),
)

internal val LightInspectorColors = InspectorColors(
    model = hex(0x1C5DAE), modelContainer = hex(0xDDE8F9),
    intent = hex(0x8A5200), intentContainer = hex(0xFAE8CC),
    signal = hex(0x6A3BB5), signalContainer = hex(0xECE1FB),
    intervened = hex(0xAE1260), intervenedContainer = hex(0xFADCEA), intervenedRow = hex(0xFDF2F7),
    gap = hex(0xB3261E), gapContainer = hex(0xF9DEDC), gapStripe = hex(0xF2CBC7),
    possiblyIncomplete = hex(0xA8483F), evicted = hex(0x5E6672),
    historyUnavailable = hex(0x3F5E7C), historyUnavailableContainer = hex(0xE1E9F1),
    success = hex(0x1A7043), successContainer = hex(0xD4EFDF),
    error = hex(0xB3261E), errorContainer = hex(0xF9DEDC),
    timeout = hex(0x6B5823), timeoutContainer = hex(0xF2EBD7),
    notObserved = hex(0xECEEF2), notSent = hex(0x646C78),
    selectedRow = hex(0xDCF1EC), rowLine = hex(0xEEF0F3), strongOutline = hex(0xBEC4CC),
    jsonKey = hex(0x1A1D22), jsonString = hex(0x0B6E5F), jsonNumber = hex(0x9A4B12), jsonLiteral = hex(0x6B3FB5),
)

// `onError` and `onErrorContainer` are not in the token table: the dark ones are the Gap stripe and
// the error itself, the light ones the usual white and the error itself.
internal val DarkInspectorScheme: ColorScheme = darkColorScheme(
    primary = hex(0x5ED3BF), onPrimary = hex(0x00382F),
    primaryContainer = hex(0x16443D), onPrimaryContainer = hex(0xA8F2E3),
    error = hex(0xFF6F66), onError = hex(0x2A1210),
    errorContainer = hex(0x3A1715), onErrorContainer = hex(0xFF6F66),
    background = hex(0x0E1013), onBackground = hex(0xE2E5EA),
    surface = hex(0x0E1013), onSurface = hex(0xE2E5EA), onSurfaceVariant = hex(0xA6ADB8),
    surfaceDim = hex(0x0B0D10),
    surfaceContainerLowest = hex(0x0E1013),
    surfaceContainerLow = hex(0x14171B), surfaceContainer = hex(0x1A1E23),
    surfaceContainerHigh = hex(0x22262C), surfaceContainerHighest = hex(0x2B3037),
    outline = hex(0x7F8793), outlineVariant = hex(0x262A30),
)

internal val LightInspectorScheme: ColorScheme = lightColorScheme(
    primary = hex(0x00695B), onPrimary = hex(0xFFFFFF),
    primaryContainer = hex(0xC6EEE5), onPrimaryContainer = hex(0x00201B),
    error = hex(0xB3261E), onError = hex(0xFFFFFF),
    errorContainer = hex(0xF9DEDC), onErrorContainer = hex(0xB3261E),
    background = hex(0xE9ECF0), onBackground = hex(0x1A1D22),
    surface = hex(0xE9ECF0), onSurface = hex(0x1A1D22), onSurfaceVariant = hex(0x454C57),
    surfaceDim = hex(0xDFE3E8),
    surfaceContainerLowest = hex(0xE9ECF0),
    surfaceContainerLow = hex(0xF7F8FA), surfaceContainer = hex(0xFFFFFF),
    surfaceContainerHigh = hex(0xECEEF2), surfaceContainerHighest = hex(0xE1E4E9),
    outline = hex(0x646C78), outlineVariant = hex(0xDCE0E5),
)

internal val LocalInspectorColors = staticCompositionLocalOf { DarkInspectorColors }
