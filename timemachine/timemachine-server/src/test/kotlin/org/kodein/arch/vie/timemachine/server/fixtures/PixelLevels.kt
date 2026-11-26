package org.kodein.arch.vie.timemachine.server.fixtures

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.test.assertTrue


/** How many levels of 255 each of [shown]'s red, green and blue is above [rest]'s: negative where it is darker. */
internal fun levelsAbove(rest: Color, shown: Color): List<Int> =
    listOf(shown.red - rest.red, shown.green - rest.green, shown.blue - rest.blue).map { (it * 255).roundToInt() }

/**
 * What an action shows under the pointer moves its surface visibly: by at least this many levels of 255 on each
 * channel, against 2 to 3 for Material's black on the dark theme's surface. Measured on the panes' surface, the dark
 * theme's ripple lightens it by 16 levels under the pointer and 20 pressed, the light theme's darkens it by 20 and 26.
 */
internal const val VisibleLevels: Int = 10

/**
 * The least, in levels of 255 on each channel, that a filled button's hover or press darkens its fill by. Black takes
 * 7.5 levels off the dark primary's weakest channel, its red (94 of 255), at 8 % under the pointer and 9.4 at 10 %
 * pressed; this stays under both, and tells a fill that darkens from one that lightens or does not move.
 */
internal const val DarkenedLevels: Int = 5

/** [shown] is lighter than [rest] by [least] to [most] levels of 255 on each of its channels. */
internal fun assertLightened(rest: Color, shown: Color, least: Int = VisibleLevels, most: Int = 255) {
    val levels = levelsAbove(rest, shown)
    assertTrue(levels.all { it in least..most }, movedMessage(levels, "$least to $most lighter"))
}

/** [shown] is darker than [rest] by [least] to [most] levels of 255 on each of its channels. */
internal fun assertDarkened(rest: Color, shown: Color, least: Int = VisibleLevels, most: Int = 255) {
    val levels = levelsAbove(rest, shown)
    assertTrue(levels.all { -it in least..most }, movedMessage(levels, "$least to $most darker"))
}

private fun movedMessage(levels: List<Int>, expected: String) =
    "red, green and blue move by $levels levels of 255 (negative: darker), not $expected"
