package com.danteandroid.transbee

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Typeface
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import java.util.Locale

private val LogoFont =
    FontMgr.default.matchFamilyStyle("Didot", FontStyle.NORMAL)?.let {
        FontFamily(Typeface(it))
    } ?: FontFamily.Default

private fun resolveSystemFontFamily(vararg candidates: String): FontFamily {
    val mgr = FontMgr.default
    for (name in candidates) {
        mgr.matchFamilyStyle(name, FontStyle.NORMAL)?.let { return FontFamily(Typeface(it)) }
    }
    return FontFamily.Default
}

private val UiFontEn: FontFamily by lazy {
    val custom = System.getenv("TRANSBEE_FONT_EN")?.trim().orEmpty()
    if (custom.isNotEmpty()) {
        resolveSystemFontFamily(custom)
    } else {
        resolveSystemFontFamily(
            // Retro / editorial serif vibe
            "EB Garamond",
            "Cormorant Garamond",
            "Garamond",
            "Baskerville",
            "Didot",
            "Georgia",
            // Fallbacks
            "Times New Roman",
        )
    }
}

private val UiFontZh: FontFamily by lazy {
    val custom = System.getenv("TRANSBEE_FONT_ZH")?.trim().orEmpty()
    if (custom.isNotEmpty()) {
        resolveSystemFontFamily(custom)
    } else {
        resolveSystemFontFamily(
            // Retro Chinese: Song/FangSong/Kai family preference
            "Songti SC",
            "STSong",
            "SimSun",
            "FangSong",
            "STFangsong",
            "KaiTi",
            "STKaiti",
            // Readable modern fallbacks if above unavailable
            "Noto Serif SC",
            "Noto Sans SC",
            "PingFang SC",
        )
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF715C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE171),
    onPrimaryContainer = Color(0xFF221B00),
    secondary = Color(0xFF006782),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBDE9FF),
    onSecondaryContainer = Color(0xFF001F29),
    tertiary = Color(0xFF006A6A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFE7E2D9),
    onSurfaceVariant = Color(0xFF49463E),
    surfaceDim = Color(0xFFDDE3EA),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE8EEF4),
    surfaceContainerHighest = Color(0xFFDCE3EC),
    outline = Color(0xFF7A776D),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFCC00), // Yellow accent
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF332900),
    onPrimaryContainer = Color(0xFFFFE171),
    secondary = Color(0xFF4D6BFF), // Blue for buttons/gradients
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF001F2B),
    onSecondaryContainer = Color(0xFFBDE9FF),
    tertiary = Color(0xFF8B5CF6), // Purple for gradients
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF080C1E), // Deep navy background
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0D132D), // Panel surface
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF060918),
    surfaceContainerLow = Color(0xFF0A1024),
    surfaceContainer = Color(0xFF111A33),
    surfaceContainerHigh = Color(0xFF161F3D),
    surfaceContainerHighest = Color(0xFF1B2648),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFFF5555),
    onError = Color(0xFF000000),
)

private fun whisperTypography(fontFamily: FontFamily) = Typography(
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
)

private val WhisperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Stable
data class AppSpacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val xLarge: Dp = 20.dp,
    val xxLarge: Dp = 24.dp,
)

private val LocalSpacing = staticCompositionLocalOf { AppSpacing() }

object AppTheme {
    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current
}

@Composable
fun TransbeeTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val fontFamily = if (Locale.getDefault().language.lowercase() == "zh") UiFontZh else UiFontEn
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = whisperTypography(fontFamily),
        shapes = WhisperShapes,
    ) {
        CompositionLocalProvider(
            LocalSpacing provides AppSpacing(),
            content = content,
        )
    }
}
