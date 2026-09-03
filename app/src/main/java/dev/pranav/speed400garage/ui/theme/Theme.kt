package dev.pranav.speed400garage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Plan §7.11 — a workshop-manual palette rather than a consumer-app one: warm off-white
// paper, a dark engine green, and enough contrast for a tablet propped on a bench.
private val EngineGreen = Color(0xFF2E5041)
private val EngineGreenLight = Color(0xFF6FA98D)
private val Rust = Color(0xFF9B4A2F)
private val Paper = Color(0xFFF4F1EA)
private val Ink = Color(0xFF1A1C1A)

private val LightScheme = lightColorScheme(
    primary = EngineGreen,
    onPrimary = Paper,
    secondary = Rust,
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFBF9F5),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6E2D8),
    onSurfaceVariant = Color(0xFF44483F),
)

private val DarkScheme = darkColorScheme(
    primary = EngineGreenLight,
    onPrimary = Color(0xFF10261C),
    secondary = Color(0xFFE49578),
    background = Ink,
    onBackground = Paper,
    surface = Color(0xFF222522),
    onSurface = Paper,
    surfaceVariant = Color(0xFF3A3E38),
    onSurfaceVariant = Color(0xFFC6C8BE),
)

private val GarageTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

@Composable
fun Speed400GarageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = GarageTypography,
        content = content,
    )
}
