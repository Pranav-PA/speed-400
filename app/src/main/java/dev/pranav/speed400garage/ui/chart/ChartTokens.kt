package dev.pranav.speed400garage.ui.chart

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Chart colours, validated rather than chosen by eye.
 *
 * Every value here was run through a palette validator against this app's actual
 * chart surfaces (#FBF9F5 light, #222522 dark) and passes the lightness band, chroma
 * floor, colour-vision separation, normal-vision floor and contrast checks.
 *
 * Two findings from that pass are worth recording, because both changed the design:
 *
 *  1. The app's UI green (#2E5041) FAILS as a data colour — too dark, and below the
 *     chroma floor, so a mark painted in it reads as grey. The UI keeps it; charts
 *     use [data] instead.
 *  2. Four severity colours on a warm ramp cannot be told apart. Amber, orange and
 *     red sat at ΔE 10 or below for normal vision, well under the floor of 15. So
 *     severity uses THREE colours and the row text carries the fourth distinction —
 *     "Overdue by 12 days" versus "300 km away" is unambiguous without colour.
 */
object ChartTokens {

    /** The single data hue. Passes in both modes, so it does not change between them. */
    val data = Color(0xFF199E70)

    /** Context marks — raw points behind a trend line, gridlines, axes. */
    @Composable
    @ReadOnlyComposable
    fun muted(): Color = if (isSystemInDarkTheme()) Color(0xFF6E736B) else Color(0xFF8A8F86)

    @Composable
    @ReadOnlyComposable
    fun grid(): Color = if (isSystemInDarkTheme()) Color(0xFF35392F) else Color(0xFFE6E2D8)

    /** Status. Green and amber hold in both modes; the red is lifted for dark. */
    val good = Color(0xFF199E70)
    val warning = Color(0xFFB8860B)

    @Composable
    @ReadOnlyComposable
    fun critical(): Color = if (isSystemInDarkTheme()) Color(0xFFC93B2F) else Color(0xFFB3261E)
}
