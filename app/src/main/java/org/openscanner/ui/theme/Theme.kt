package org.openscanner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------------
// BRAND CONSTANTS
// ---------------------------------------------------------------------------
val ScannerGreen = Color(0xFF00BA66)          // Primary Brand Emerald Green
val ScannerGreenLight = Color(0xFF00D675)     // Active / Highlight Green
val ScannerGreenDark = Color(0xFF008C4C)      // Pressed / Dark Green
val ScannerFolderYellow = Color(0xFFF0AF3A)   // Manila Folder Yellow
val ScannerBlueAccent = Color(0xFF0D7DFC)     // PDF / Cloud Accent
val ScannerGoldVip = Color(0xFFEDCC8B)        // Gold Accent
val ScannerRed = Color(0xFFEE3F3F)            // Delete / Alert Red

// Legacy / Static tokens (Defaults to Dark for backward compatibility)
val ScannerDarkBar = Color(0xFF141414)
val ScannerSlatePrimary = Color(0xFF2C3E50)
val ScannerSlateDark = Color(0xFF1C2833)
val ScannerNavyDark = Color(0xFF121212)
val ScannerBgLight = Color(0xFF000000)
val ScannerCardBg = Color(0xFF141414)
val ScannerBorderLight = Color(0xFF222222)
val ScannerTextPrimary = Color(0xFFFFFFFF)
val ScannerTextSecondary = Color(0xFF8E8E93)
val ScannerTextMuted = Color(0xFF636366)
val ScannerGreenContainer = Color(0xFF1E2822)

// ---------------------------------------------------------------------------
// DYNAMIC COLOR DESIGN SYSTEM
// ---------------------------------------------------------------------------
@Stable
class ScannerColors(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryContainer: Color,
    val folderYellow: Color,
    val isDark: Boolean
)

val DarkScannerColors = ScannerColors(
    background = Color(0xFF000000),         // AMOLED true black
    surface = Color(0xFF141414),            // Dark graphite card
    surfaceSecondary = Color(0xFF1E1E1E),   // Secondary elevated surface
    cardBorder = Color(0xFF242424),         // Subtle dark border
    textPrimary = Color(0xFFFFFFFF),        // High-contrast white text
    textSecondary = Color(0xFF8E8E93),      // Neutral gray secondary text
    textMuted = Color(0xFF636366),          // Subtle muted text
    primary = ScannerGreen,                 // Emerald green
    primaryContainer = Color(0xFF1E2822),   // Dark green container tint
    folderYellow = ScannerFolderYellow,     // Manila folder yellow
    isDark = true
)

val LightScannerColors = ScannerColors(
    background = Color(0xFFF8F9FA),        // Soft paper light background
    surface = Color(0xFFFFFFFF),           // Pure white card surface
    surfaceSecondary = Color(0xFFF1F3F5),  // Soft gray secondary surface
    cardBorder = Color(0xFFE9ECEF),        // Clean card border
    textPrimary = Color(0xFF17202B),       // Deep slate primary text
    textSecondary = Color(0xFF6C757D),     // Medium slate secondary text
    textMuted = Color(0xFFADB5BD),         // Light muted text
    primary = ScannerGreen,                // Emerald green
    primaryContainer = Color(0xFFDCF5E8),  // Mint green container tint
    folderYellow = ScannerFolderYellow,    // Manila folder yellow
    isDark = false
)

val LocalScannerColors = staticCompositionLocalOf { DarkScannerColors }

object ScannerTheme {
    val colors: ScannerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalScannerColors.current
}

// ---------------------------------------------------------------------------
// MATERIAL 3 COLOR SCHEMES
// ---------------------------------------------------------------------------
private val DarkColorScheme = darkColorScheme(
    primary = ScannerGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E2822),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF2C3E50),
    onSecondary = Color.White,
    background = Color(0xFF000000),
    surface = Color(0xFF141414),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF242424)
)

private val LightColorScheme = lightColorScheme(
    primary = ScannerGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCF5E8),
    onPrimaryContainer = Color(0xFF006F4B),
    secondary = Color(0xFF2C3E50),
    onSecondary = Color.White,
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF17202B),
    onSurface = Color(0xFF17202B),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF6C757D),
    outline = Color(0xFFE9ECEF)
)

@Composable
fun OpenScannerTheme(
    darkTheme: Boolean = ThemeManager.isDark(LocalContext.current),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val scannerColors = if (darkTheme) DarkScannerColors else LightScannerColors

    // Synchronize System Status Bar and Navigation Bar dynamically with Edge-to-Edge
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalScannerColors provides scannerColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
