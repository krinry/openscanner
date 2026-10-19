package org.openscanner.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openscanner.ui.theme.ScannerTheme

@Composable
fun BatchSelectionBottomBar(
    selectedCount: Int,
    onMoveToFolder: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExportPdf: () -> Unit
) {
    val colors = ScannerTheme.colors

    Surface(
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatchActionButton(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                label = "Move",
                enabled = selectedCount > 0,
                onClick = onMoveToFolder
            )
            BatchActionButton(
                icon = Icons.Outlined.PictureAsPdf,
                label = "PDF",
                enabled = selectedCount > 0,
                onClick = onExportPdf
            )
            BatchActionButton(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                enabled = selectedCount > 0,
                tint = Color(0xFFEE3F3F),
                onClick = onDeleteSelected
            )
        }
    }
}

@Composable
private fun BatchActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors
    val effectiveTint = when {
        !enabled -> colors.textMuted
        tint != null -> tint
        else -> colors.textPrimary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = effectiveTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = effectiveTint, fontSize = 11.sp)
    }
}
