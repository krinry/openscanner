package org.openscanner.ui.pdf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openscanner.pdf.PdfExportOptions
import org.openscanner.pdf.PdfPageSize
import org.openscanner.pdf.PdfQuality
import org.openscanner.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportDialog(
    onDismiss: () -> Unit,
    onSaveToDownloads: (PdfExportOptions) -> Unit,
    onShare: (PdfExportOptions) -> Unit
) {
    var selectedPageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var selectedQuality by remember { mutableStateOf(PdfQuality.MEDIUM) }
    var watermarkText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    fun currentOptions() = PdfExportOptions(
        pageSize = selectedPageSize,
        quality = selectedQuality,
        watermarkText = watermarkText.ifBlank { null },
        password = password.ifBlank { null }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = OkenGreen)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Export PDF Settings", color = OkenTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Page size selection
                Text("Page Size", color = OkenTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfPageSize.entries.forEach { size ->
                        val isSelected = size == selectedPageSize
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPageSize = size },
                            label = { Text(if (size == PdfPageSize.A4) "A4" else if (size == PdfPageSize.LETTER) "Letter" else "Fit") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OkenGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Quality selection
                Text("File Quality", color = OkenTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfQuality.entries.forEach { q ->
                        val isSelected = q == selectedQuality
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedQuality = q },
                            label = { Text(if (q == PdfQuality.HIGH) "High" else if (q == PdfQuality.MEDIUM) "Normal" else "Small") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OkenGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Watermark
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Watermark (e.g. Confidential)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OkenGreen,
                        unfocusedBorderColor = OkenBorderLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("PDF Password (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OkenGreen,
                        unfocusedBorderColor = OkenBorderLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSaveToDownloads(currentOptions()) },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
                Button(
                    onClick = { onShare(currentOptions()) },
                    colors = ButtonDefaults.buttonColors(containerColor = OkenGreen),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OkenTextSecondary)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
