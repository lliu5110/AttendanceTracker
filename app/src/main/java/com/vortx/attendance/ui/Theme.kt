package com.vortx.attendance.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pure black base, white detail. Greys are neutral (no blue/warm tint) so the screen
 * reads as instrument panel rather than "dark mode". Nothing here is chromatic —
 * the only colour in the app is the red used for destructive admin actions.
 */
object Ink {
    val Base = Color(0xFF000000)
    val Raised = Color(0xFF0A0A0A)   // cards sitting on the base
    val Line = Color(0xFF242424)     // hairline borders
    val LineBright = Color(0xFF3D3D3D)
    val Primary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFA0A0A0) // supporting copy
    val Muted = Color(0xFF5A5A5A)     // absent dots, disabled text
    val Danger = Color(0xFFFF4A4A)
}

/** Dot grid sizing, tuned so ~14 dots per row fit a 393dp-wide screen with margins. */
object Dots {
    val Size = 12.dp
    val Gap = 6.dp
}

private val Scheme = darkColorScheme(
    primary = Ink.Primary,
    onPrimary = Ink.Base,
    secondary = Ink.Secondary,
    background = Ink.Base,
    onBackground = Ink.Primary,
    surface = Ink.Raised,
    onSurface = Ink.Primary,
    surfaceVariant = Ink.Raised,
    onSurfaceVariant = Ink.Secondary,
    outline = Ink.Line,
    error = Ink.Danger
)

/**
 * One family throughout. Monospace is reserved for ID numbers and counts only —
 * places where digit alignment genuinely helps scanning a column.
 */
private val AppType = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Light, fontSize = 40.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp)
)

val MonoNumber = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    letterSpacing = 0.5.sp
)

/** Always dark. The brief calls for a black surface regardless of the system setting. */
@Composable
fun AttendanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppType, content = content)
}
