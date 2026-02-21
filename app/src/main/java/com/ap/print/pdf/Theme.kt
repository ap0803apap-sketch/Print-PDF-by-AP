package com.ap.print.pdf

import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

// -----------------------------
// Theme Mode Enum
// -----------------------------
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

// -----------------------------
// Brand Colors
// -----------------------------
val ExpressivePink = Color(0xFFFF6B9D)
val DeepPlum = Color(0xFF7C3AED)
val SurfaceLavender = Color(0xFFF3E5F5)

// -----------------------------
// AMOLED + Dark UI Colors
// -----------------------------
val AmoledBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF0B0B0B)
val DarkCard = Color(0xFF141414)
val DarkOutline = Color(0xFF2A2A2A)
val ExpressivePinkLight = Color(0xFFE94B7D)

// -----------------------------
// LIGHT THEME
// -----------------------------
private val LightColorScheme = lightColorScheme(
    primary = ExpressivePinkLight,
    onPrimary = Color.White,
    primaryContainer = SurfaceLavender,
    onPrimaryContainer = DeepPlum,

    secondary = ExpressivePink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE5EC),
    onSecondaryContainer = ExpressivePink,

    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,

    error = Color(0xFFB3261E),
    onError = Color.White,

    background = Color(0xFFFEFEFE),
    onBackground = Color(0xFF1C1B1F),

    surface = Color(0xFFFEFEFE),
    onSurface = Color(0xFF1C1B1F),

    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454E),

    outline = Color(0xFF79747E),
    scrim = Color.Black
)

// -----------------------------
// DARK THEME (AMOLED PREMIUM)
// -----------------------------
private val DarkColorScheme = darkColorScheme(

    primary = ExpressivePink,
    onPrimary = Color.Black,

    primaryContainer = DeepPlum,
    onPrimaryContainer = Color.White,

    secondary = Color(0xFFBFA7FF),
    onSecondary = Color.Black,

    secondaryContainer = Color(0xFF2A1F3D),
    onSecondaryContainer = Color.White,

    tertiary = Color(0xFFFF9EC4),
    onTertiary = Color.Black,

    error = Color(0xFFFF6B6B),
    onError = Color.Black,

    background = AmoledBlack,
    onBackground = Color(0xFFEDEDED),

    surface = AmoledBlack,
    onSurface = Color(0xFFEDEDED),

    surfaceVariant = DarkCard,
    onSurfaceVariant = Color(0xFFBDBDBD),

    outline = DarkOutline,
    outlineVariant = Color(0xFF1F1F1F),

    scrim = Color.Black
)

// -----------------------------
// MAIN THEME
// -----------------------------
@Composable
fun PrintPdfTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current

    val systemDark =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    val isDarkMode = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDarkMode) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        isDarkMode -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
