package org.openscanner.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Exact OKEN Scanner / CamScanner Official Color Palette
val OkenGreen = Color(0xFF018656)          // Primary Brand Emerald Green
val OkenGreenLight = Color(0xFF00B15F)     // Active / Hover Green
val OkenGreenDark = Color(0xFF006F4B)      // Pressed / Dark Green
val OkenGreenContainer = Color(0xFFDCF3EE) // Subtle Green Background Tint

val OkenDarkBar = Color(0xFF1E2939)        // Dark Toolbar / Action Bar
val OkenSlatePrimary = Color(0xFF34485E)   // Slate Primary (CamScanner Header)
val OkenSlateDark = Color(0xFF2C3E50)      // Slate Dark
val OkenNavyDark = Color(0xFF17202B)       // Dark Header Tint

val OkenBgLight = Color(0xFFF4F5F6)        // App Background Light
val OkenCardBg = Color(0xFFFFFFFF)         // Document Card Background
val OkenBorderLight = Color(0xFFE5E7EB)    // Card & Divider Border

val OkenTextPrimary = Color(0xFF17202B)    // Main Title Text
val OkenTextSecondary = Color(0xFF7F878E)  // Date / Subtitle Text
val OkenTextMuted = Color(0xFF9CA3AF)      // Placeholder / Disabled Text

val OkenBlueAccent = Color(0xFF0D7DFC)     // PDF / Cloud Accent
val OkenGoldVip = Color(0xFFEDCC8B)        // Premium / VIP Gold Accent
val OkenRed = Color(0xFFEE3F3F)            // Delete / Alert Red

private val LightColorScheme = lightColorScheme(
    primary = OkenGreen,
    onPrimary = Color.White,
    primaryContainer = OkenGreenContainer,
    onPrimaryContainer = OkenGreenDark,
    secondary = OkenSlatePrimary,
    onSecondary = Color.White,
    background = OkenBgLight,
    surface = OkenCardBg,
    onBackground = OkenTextPrimary,
    onSurface = OkenTextPrimary,
    surfaceVariant = Color(0xFFECEFF1),
    onSurfaceVariant = OkenTextSecondary,
    outline = OkenBorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = OkenGreenLight,
    onPrimary = Color.White,
    primaryContainer = OkenGreenDark,
    onPrimaryContainer = Color.White,
    secondary = OkenSlatePrimary,
    onSecondary = Color.White,
    background = Color(0xFF121820),
    surface = Color(0xFF1A222D),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF242F3D),
    onSurfaceVariant = Color(0xFF90A4AE),
    outline = Color(0xFF374151)
)

@Composable
fun OpenScannerTheme(
    darkTheme: Boolean = false, // OKEN Scanner default is clean Light Theme with Dark Camera/Crop
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
