package org.openscanner.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// OpenScanner Professional Color Palette
val ScannerGreen = Color(0xFF00BA66)          // Primary Brand Emerald Green
val ScannerGreenLight = Color(0xFF00D675)     // Active / Highlight Green
val ScannerGreenDark = Color(0xFF008C4C)      // Pressed / Dark Green
val ScannerGreenContainer = Color(0xFF1E2822) // Subtle Dark Green Background Tint
val ScannerFolderYellow = Color(0xFFF0AF3A)   // Standard Folder Manila Yellow

val ScannerDarkBar = Color(0xFF141414)        // Dark Toolbar / Action Bar
val ScannerSlatePrimary = Color(0xFF2C3E50)   // Slate Primary
val ScannerSlateDark = Color(0xFF1C2833)      // Slate Dark
val ScannerNavyDark = Color(0xFF121212)       // Dark Header Tint

val ScannerBgLight = Color(0xFF000000)        // App Background AMOLED Dark
val ScannerCardBg = Color(0xFF141414)         // Document Card Background
val ScannerBorderLight = Color(0xFF222222)    // Card & Divider Border

val ScannerTextPrimary = Color(0xFFFFFFFF)    // Main Title Text
val ScannerTextSecondary = Color(0xFF8E8E93)  // Date / Subtitle Text
val ScannerTextMuted = Color(0xFF636366)      // Placeholder / Disabled Text

val ScannerBlueAccent = Color(0xFF0D7DFC)     // PDF / Cloud Accent
val ScannerGoldVip = Color(0xFFEDCC8B)        // Premium Gold Accent
val ScannerRed = Color(0xFFEE3F3F)            // Delete / Alert Red

private val DarkColorScheme = darkColorScheme(
    primary = ScannerGreen,
    onPrimary = Color.White,
    primaryContainer = ScannerGreenContainer,
    onPrimaryContainer = Color.White,
    secondary = ScannerSlatePrimary,
    onSecondary = Color.White,
    background = Color(0xFF000000),
    surface = Color(0xFF141414),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = ScannerTextSecondary,
    outline = ScannerBorderLight
)

@Composable
fun OpenScannerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
