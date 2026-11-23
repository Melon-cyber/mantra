package app.mantra.cards.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.mantra.cards.R

val Ink = Color(0xFF131014)
val Panel = Color(0xFF1C181E)
val Elevated = Color(0xFF28222A)
val Accent = Color(0xFFFFA8C5)
val Income = Color(0xFF6EE7A8)
val Expense = Color(0xFFFF8A80)
val Leaf = Color(0xFFFFD6E4)
val SoftWhite = Color(0xFFF6F1F4)
val Muted = Color(0xFFAEA3AB)
val Line = Color(0xFF372F38)
val Warm = Color(0xFFFFB86B)

@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

@Composable fun MantraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Ink, secondary = Leaf, onSecondary = Ink, background = Ink, onBackground = SoftWhite,
            surface = Panel, onSurface = SoftWhite, surfaceVariant = Elevated, onSurfaceVariant = Muted, outline = Line,
            surfaceContainer = Panel, surfaceContainerHigh = Elevated, surfaceContainerHighest = Elevated, surfaceContainerLow = Panel,
            secondaryContainer = Color(0xFF4D2A3A), onSecondaryContainer = Leaf, primaryContainer = Color(0xFF4D2A3A), onPrimaryContainer = Leaf, surfaceTint = Accent,
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 37.sp, letterSpacing = (-0.8).sp),
            headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 29.sp),
            titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
            titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
            bodyLarge = TextStyle(fontFamily = Manrope, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = Manrope, fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontFamily = Manrope, fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontFamily = Manrope, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontFamily = Manrope, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontFamily = Manrope, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
