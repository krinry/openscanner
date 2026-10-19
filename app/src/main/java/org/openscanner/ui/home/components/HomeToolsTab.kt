package org.openscanner.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openscanner.ui.theme.ScannerTheme
import org.openscanner.ui.theme.ThemeManager

@Composable
fun HomeToolsTab(
    onScanIdCard: () -> Unit,
    onScanBook: () -> Unit,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onSignTool: () -> Unit
) {
    val context = LocalContext.current
    val colors = ScannerTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Scanner Tools",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        // Theme Switcher Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { ThemeManager.toggleTheme(context) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.5.dp, colors.cardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = (if (colors.isDark) Color(0xFFFFD54F) else Color(0xFF2979FF)).copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (colors.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = if (colors.isDark) Color(0xFFFFD54F) else Color(0xFF2979FF),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (colors.isDark) "Switch to Light Theme" else "Switch to Dark Theme",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (colors.isDark) "Currently in AMOLED Dark Mode" else "Currently in Clean Light Mode",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = colors.isDark,
                        onCheckedChange = { ThemeManager.toggleTheme(context) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primary,
                            checkedTrackColor = colors.primaryContainer
                        )
                    )
                }
            }
        }

        item {
            ScannerToolCard(
                title = "ID Card & Passport Mode",
                subtitle = "Front & back auto-stitched onto A4 page",
                icon = Icons.Default.Badge,
                accentColor = Color(0xFF00BFA5),
                onClick = onScanIdCard
            )
        }

        item {
            ScannerToolCard(
                title = "Import Pictures to PDF",
                subtitle = "Batch convert gallery photos to clean scans",
                icon = Icons.Default.AddPhotoAlternate,
                accentColor = Color(0xFF2979FF),
                onClick = onImportImages
            )
        }

        item {
            ScannerToolCard(
                title = "Import & Split PDF",
                subtitle = "High-resolution PDF rasterization & editing",
                icon = Icons.Default.PictureAsPdf,
                accentColor = Color(0xFFFF5252),
                onClick = onImportPdf
            )
        }

        item {
            ScannerToolCard(
                title = "E-Signature & Paper Seal",
                subtitle = "Extract transparent ink signature from camera",
                icon = Icons.Default.Draw,
                accentColor = Color(0xFFFFAB00),
                onClick = onSignTool
            )
        }

        item {
            ScannerToolCard(
                title = "Book Curve Splitter",
                subtitle = "Dual-page capture with 3D unwarping",
                icon = Icons.Default.AutoStories,
                accentColor = Color(0xFFAA00FF),
                onClick = onScanBook
            )
        }

        // OpenScanner Guarantee Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.5.dp, colors.cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "OpenScanner Community",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "100% Free, Offline & Open-Source. Zero Ads, Zero Cloud Tracking, Zero Watermarks.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ScannerToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.5.dp, colors.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = colors.textSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary)
        }
    }
}
