package org.openscanner.ui.detail.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openscanner.cv.CollageLayout
import org.openscanner.data.model.Page
import org.openscanner.ui.theme.ScannerTheme

@Composable
fun CollageStudioDialog(
    pages: List<Page>,
    onDismiss: () -> Unit,
    onGenerateCollage: (CollageLayout, List<Long>) -> Unit
) {
    val colors = ScannerTheme.colors
    var selectedLayout by remember { mutableStateOf(CollageLayout.TWO_VERTICAL) }
    var selectedPageIds by remember {
        mutableStateOf(pages.take(2).map { it.id }.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = colors.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Collage Studio", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Choose Collage Layout", color = colors.textSecondary, fontSize = 13.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CollageLayout.entries.forEach { layout ->
                        val isSelected = layout == selectedLayout
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedLayout = layout
                                val count = layout.maxPages
                                selectedPageIds = pages.take(count).map { it.id }.toSet()
                            },
                            label = { Text(layout.title, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = Color.White,
                                containerColor = colors.surfaceSecondary,
                                labelColor = colors.textPrimary
                            )
                        )
                    }
                }

                Text("Select up to ${selectedLayout.maxPages} pages:", color = colors.textSecondary, fontSize = 13.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pages.take(6).forEach { page ->
                        val isSelected = selectedPageIds.contains(page.id)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) colors.primaryContainer else colors.surfaceSecondary,
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) colors.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .size(50.dp, 68.dp)
                                .clickable {
                                    val current = selectedPageIds.toMutableSet()
                                    if (current.contains(page.id)) {
                                        current.remove(page.id)
                                    } else {
                                        if (current.size < selectedLayout.maxPages) {
                                            current.add(page.id)
                                        }
                                    }
                                    selectedPageIds = current
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = String.format("%02d", page.pageIndex + 1),
                                    color = if (isSelected) colors.primary else colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedPageIds.isNotEmpty()) {
                        onGenerateCollage(selectedLayout, selectedPageIds.toList())
                    }
                },
                enabled = selectedPageIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("Create Collage")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surface
    )
}
