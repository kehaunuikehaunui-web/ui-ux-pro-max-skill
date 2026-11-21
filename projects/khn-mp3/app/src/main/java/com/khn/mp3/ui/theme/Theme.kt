package com.khn.mp3.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khn.mp3.R

/** Palette « Sombre néon » : base OLED + accents violet / magenta / cyan. */
@Immutable
object KhnColors {
    val Background = Color(0xFF07070D)
    val Surface = Color(0xFF12121C)
    val SurfaceHigh = Color(0xFF1C1C2B)
    val Outline = Color(0xFF2E2A4A)
    val Violet = Color(0xFFA855F7)
    val Magenta = Color(0xFFEC4899)
    val Cyan = Color(0xFF22D3EE)
    val Text = Color(0xFFF5F3FF)
    val TextMuted = Color(0xFFA1A1B5)
    val Error = Color(0xFFF43F5E)

    val Neon = Brush.linearGradient(listOf(Violet, Magenta))
    val NeonHorizontal = Brush.horizontalGradient(listOf(Violet, Magenta))
}

val Righteous = FontFamily(Font(R.font.righteous_regular))
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private val KhnTypography = Typography(
    displaySmall = TextStyle(fontFamily = Righteous, fontSize = 34.sp, letterSpacing = 1.sp),
    headlineMedium = TextStyle(fontFamily = Righteous, fontSize = 28.sp, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Poppins, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

private val KhnColorScheme = darkColorScheme(
    primary = KhnColors.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1466),
    onPrimaryContainer = KhnColors.Text,
    secondary = KhnColors.Magenta,
    onSecondary = Color.White,
    tertiary = KhnColors.Cyan,
    background = KhnColors.Background,
    onBackground = KhnColors.Text,
    surface = KhnColors.Surface,
    onSurface = KhnColors.Text,
    surfaceVariant = KhnColors.SurfaceHigh,
    onSurfaceVariant = KhnColors.TextMuted,
    surfaceContainer = KhnColors.Surface,
    surfaceContainerHigh = KhnColors.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF24243A),
    surfaceContainerLow = Color(0xFF0E0E17),
    outline = KhnColors.Outline,
    outlineVariant = Color(0xFF221F38),
    error = KhnColors.Error,
)

@Immutable
class KhnShapes {
    val artwork = RoundedCornerShape(10.dp)
    val card = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}

object Khn {
    val shapes = KhnShapes()
}

@Composable
fun KhnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KhnColorScheme,
        typography = KhnTypography,
        content = content,
    )
}
