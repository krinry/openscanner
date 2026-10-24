package org.openscanner.ui.detail.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.openscanner.cv.CollageLayout
import org.openscanner.data.model.Page
import org.openscanner.ui.theme.ScannerGreen
import org.openscanner.ui.theme.ScannerTheme
import java.io.File

data class CollageTemplateOption(
    val id: String,
    val title: String,
    val layout: CollageLayout,
    val description: String
)

val COLLAGE_TEMPLATES = listOf(
    CollageTemplateOption("question_book", "Question b...", CollageLayout.TWO_VERTICAL, "Question Paper"),
    CollageTemplateOption("driver_license", "Driver lice...", CollageLayout.TWO_VERTICAL, "ID Card 2-in-1"),
    CollageTemplateOption("passport", "Passport", CollageLayout.TWO_HORIZONTAL, "Passport & Details"),
    CollageTemplateOption("2x1", "2x1", CollageLayout.TWO_VERTICAL, "2 Vertical"),
    CollageTemplateOption("1x2", "1x2", CollageLayout.TWO_HORIZONTAL, "2 Side-by-Side"),
    CollageTemplateOption("4_grid", "4-Grid", CollageLayout.FOUR_GRID, "4-in-1 Grid"),
    CollageTemplateOption("6_grid", "6-Grid", CollageLayout.SIX_GRID, "6-in-1 Grid")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageStudioDialog(
    pages: List<Page>,
    onDismiss: () -> Unit,
    onGenerateCollage: (CollageLayout, List<Long>) -> Unit
) {
    val colors = ScannerTheme.colors
    var selectedTemplate by remember { mutableStateOf(COLLAGE_TEMPLATES[3]) } // 2x1 default
    var selectedPageIds by remember {
        mutableStateOf(pages.take(2).map { it.id }.toSet())
    }
    var activeBottomTab by remember { mutableStateOf("template") } // "page", "watermark", "template", "size"
    var watermarkText by remember { mutableStateOf("") }
    var selectedPaperSize by remember { mutableStateOf("A4") }
    var showWatermarkInput by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = Color(0xFF0D0D0D),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Collage",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val pagesToPass = selectedPageIds.ifEmpty { pages.take(selectedTemplate.layout.maxPages).map { it.id }.toSet() }
                            onGenerateCollage(selectedTemplate.layout, pagesToPass.toList())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Done",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414))
                        .navigationBarsPadding()
                ) {
                    // Drawer Content depending on active tab
                    if (activeBottomTab == "template") {
                        // Template Carousel
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(COLLAGE_TEMPLATES, key = { it.id }) { template ->
                                val isSelected = template.id == selectedTemplate.id
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF1E2822) else Color(0xFF222222),
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (isSelected) ScannerGreen else Color(0xFF333333)
                                    ),
                                    modifier = Modifier
                                        .width(76.dp)
                                        .height(72.dp)
                                        .clickable {
                                            selectedTemplate = template
                                            val max = template.layout.maxPages
                                            selectedPageIds = pages.take(max).map { it.id }.toSet()
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Miniature template icon representation
                                        TemplateVisualPreview(template.id, isSelected)
                                        Text(
                                            text = template.title,
                                            color = if (isSelected) ScannerGreen else Color(0xFFBBBBBB),
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    } else if (activeBottomTab == "page") {
                        // Page selector tray
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(pages, key = { it.id }) { page ->
                                val isSelected = selectedPageIds.contains(page.id)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF222222),
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (isSelected) ScannerGreen else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .size(54.dp, 72.dp)
                                        .clickable {
                                            val current = selectedPageIds.toMutableSet()
                                            if (current.contains(page.id)) {
                                                if (current.size > 1) current.remove(page.id)
                                            } else {
                                                if (current.size < selectedTemplate.layout.maxPages) {
                                                    current.add(page.id)
                                                }
                                            }
                                            selectedPageIds = current
                                        }
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = File(page.thumbnailPath.ifEmpty { page.processedImagePath }),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (isSelected) {
                                            Surface(
                                                shape = CircleShape,
                                                color = ScannerGreen,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF242424), thickness = 0.5.dp)

                    // Bottom Navigation Bar matching Screenshot 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CollageBottomActionItem(
                            icon = Icons.Default.Add,
                            label = "Page",
                            isActive = activeBottomTab == "page",
                            onClick = { activeBottomTab = "page" }
                        )
                        CollageBottomActionItem(
                            icon = Icons.Outlined.WaterDamage,
                            label = if (watermarkText.isNotBlank()) "Watermarked" else "Add a watermark",
                            isActive = activeBottomTab == "watermark",
                            onClick = {
                                activeBottomTab = "watermark"
                                showWatermarkInput = true
                            }
                        )
                        CollageBottomActionItem(
                            icon = Icons.Outlined.GridView,
                            label = "Template",
                            isActive = activeBottomTab == "template",
                            onClick = { activeBottomTab = "template" }
                        )
                        CollageBottomActionItem(
                            icon = Icons.Outlined.Description,
                            label = selectedPaperSize,
                            isActive = activeBottomTab == "size",
                            onClick = {
                                selectedPaperSize = if (selectedPaperSize == "A4") "Letter" else "A4"
                            }
                        )
                    }
                }
            }
        ) { padding ->
            // Center Live Preview Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF0D0D0D)),
                contentAlignment = Alignment.Center
            ) {
                // A4 White Sheet Preview
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f / 1.414f) // Standard A4 Aspect Ratio
                        .padding(8.dp)
                ) {
                    val activePages = pages.filter { selectedPageIds.contains(it.id) }
                    when (selectedTemplate.id) {
                        "2x1", "driver_license", "question_book" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                activePages.take(2).forEach { p ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFF2F2F2),
                                        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        AsyncImage(
                                            model = File(p.processedImagePath),
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                        "1x2", "passport" -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                activePages.take(2).forEach { p ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFF2F2F2),
                                        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(1f)
                                    ) {
                                        AsyncImage(
                                            model = File(p.processedImagePath),
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                        "4_grid" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (row in activePages.take(4).chunked(2)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        row.forEach { p ->
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFF2F2F2),
                                                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(1f)
                                            ) {
                                                AsyncImage(
                                                    model = File(p.processedImagePath),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (row in activePages.take(6).chunked(2)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        row.forEach { p ->
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFF2F2F2),
                                                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(1f)
                                            ) {
                                                AsyncImage(
                                                    model = File(p.processedImagePath),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showWatermarkInput) {
            var tempText by remember { mutableStateOf(watermarkText) }
            AlertDialog(
                onDismissRequest = { showWatermarkInput = false },
                title = { Text("Add Watermark", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = tempText,
                        onValueChange = { tempText = it },
                        placeholder = { Text("e.g. CONFIDENTIAL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            watermarkText = tempText
                            showWatermarkInput = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                    ) {
                        Text("Save", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWatermarkInput = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun TemplateVisualPreview(id: String, isSelected: Boolean) {
    val borderColor = if (isSelected) ScannerGreen else Color(0xFF777777)
    when (id) {
        "2x1", "driver_license", "question_book" -> {
            Column(
                modifier = Modifier
                    .size(24.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {}
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {}
            }
        }
        "1x2", "passport" -> {
            Row(
                modifier = Modifier
                    .size(24.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {}
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {}
            }
        }
        "4_grid" -> {
            Column(
                modifier = Modifier
                    .size(24.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
            }
        }
        else -> {
            Column(
                modifier = Modifier
                    .size(24.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
            }
        }
    }
}

@Composable
private fun CollageBottomActionItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) ScannerGreen else Color(0xFFAAAAAA),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) ScannerGreen else Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
