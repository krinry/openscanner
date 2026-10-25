package org.openscanner.ui.detail.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.openscanner.cv.CollageLayout
import org.openscanner.cv.CollageWatermarkConfig
import org.openscanner.cv.SlotTransform
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
    CollageTemplateOption("question_book", "Question Book", CollageLayout.QUESTION_BOOK, "Question Paper"),
    CollageTemplateOption("driver_license", "Driver License", CollageLayout.DRIVER_LICENSE, "ID Card 2-in-1"),
    CollageTemplateOption("passport", "Passport", CollageLayout.PASSPORT, "Passport & Details"),
    CollageTemplateOption("2x1", "2x1 Vertical", CollageLayout.TWO_VERTICAL, "2 Vertical"),
    CollageTemplateOption("1x2", "1x2 Side-by-Side", CollageLayout.TWO_HORIZONTAL, "2 Side-by-Side"),
    CollageTemplateOption("4_grid", "4-Grid", CollageLayout.FOUR_GRID, "4-in-1 Grid"),
    CollageTemplateOption("6_grid", "6-Grid", CollageLayout.SIX_GRID, "6-in-1 Grid")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageStudioDialog(
    pages: List<Page>,
    onDismiss: () -> Unit,
    onGenerateCollage: (CollageLayout, List<Long>, CollageWatermarkConfig?, Map<Int, SlotTransform>?) -> Unit
) {
    val colors = ScannerTheme.colors
    var selectedTemplate by remember { mutableStateOf(COLLAGE_TEMPLATES[3]) } // 2x1 default
    var selectedPageIds by remember {
        mutableStateOf(pages.take(2).map { it.id }.toSet())
    }
    var activeBottomTab by remember { mutableStateOf("template") } // "page", "watermark", "template", "size"
    var selectedPaperSize by remember { mutableStateOf("A4") }
    var showWatermarkInput by remember { mutableStateOf(false) }

    var watermarkConfig by remember {
        mutableStateOf(
            CollageWatermarkConfig(
                text = "",
                color = 0x88888888.toInt(),
                fontSizeSp = 32f,
                opacity = 0.35f,
                rotationAngle = -45f,
                isTiled = false
            )
        )
    }

    val slotTransforms = remember { mutableStateMapOf<Int, SlotTransform>() }

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
                            val pagesToPass = selectedPageIds.ifEmpty {
                                pages.take(selectedTemplate.layout.maxPages).map { it.id }.toSet()
                            }
                            val finalWatermark = if (watermarkConfig.text.isNotBlank()) watermarkConfig else null
                            onGenerateCollage(selectedTemplate.layout, pagesToPass.toList(), finalWatermark, slotTransforms.toMap())
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
                                        .width(82.dp)
                                        .height(76.dp)
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
                                        TemplateVisualPreview(template.id, isSelected)
                                        Text(
                                            text = template.title,
                                            color = if (isSelected) ScannerGreen else Color(0xFFBBBBBB),
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    } else if (activeBottomTab == "page") {
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

                    // Bottom Navigation Bar
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
                            label = if (watermarkConfig.text.isNotBlank()) "Watermarked" else "Watermark",
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
                        .aspectRatio(1f / 1.414f)
                        .padding(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val activePages = pages.filter { selectedPageIds.contains(it.id) }
                        when (selectedTemplate.id) {
                            "driver_license" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    activePages.take(2).forEachIndexed { index, p ->
                                        CollageSlotCard(
                                            page = p,
                                            slotIndex = index,
                                            transform = slotTransforms[index] ?: SlotTransform(),
                                            onTransformChange = { slotTransforms[index] = it },
                                            modifier = Modifier
                                                .fillMaxWidth(0.88f)
                                                .aspectRatio(1.586f)
                                        )
                                    }
                                }
                            }
                            "passport" -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    activePages.take(2).forEachIndexed { index, p ->
                                        CollageSlotCard(
                                            page = p,
                                            slotIndex = index,
                                            transform = slotTransforms[index] ?: SlotTransform(),
                                            onTransformChange = { slotTransforms[index] = it },
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(1f)
                                        )
                                    }
                                }
                            }
                            "question_book" -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    activePages.take(2).forEachIndexed { index, p ->
                                        CollageSlotCard(
                                            page = p,
                                            slotIndex = index,
                                            transform = slotTransforms[index] ?: SlotTransform(),
                                            onTransformChange = { slotTransforms[index] = it },
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(1f)
                                        )
                                    }
                                }
                            }
                            "1x2" -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    activePages.take(2).forEachIndexed { index, p ->
                                        CollageSlotCard(
                                            page = p,
                                            slotIndex = index,
                                            transform = slotTransforms[index] ?: SlotTransform(),
                                            onTransformChange = { slotTransforms[index] = it },
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(1f)
                                        )
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
                                    for ((rowIndex, row) in activePages.take(4).chunked(2).withIndex()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            row.forEachIndexed { colIndex, p ->
                                                val slotIdx = rowIndex * 2 + colIndex
                                                CollageSlotCard(
                                                    page = p,
                                                    slotIndex = slotIdx,
                                                    transform = slotTransforms[slotIdx] ?: SlotTransform(),
                                                    onTransformChange = { slotTransforms[slotIdx] = it },
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .weight(1f)
                                                )
                                            }
                                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            "6_grid" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for ((rowIndex, row) in activePages.take(6).chunked(2).withIndex()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            row.forEachIndexed { colIndex, p ->
                                                val slotIdx = rowIndex * 2 + colIndex
                                                CollageSlotCard(
                                                    page = p,
                                                    slotIndex = slotIdx,
                                                    transform = slotTransforms[slotIdx] ?: SlotTransform(),
                                                    onTransformChange = { slotTransforms[slotIdx] = it },
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .weight(1f)
                                                )
                                            }
                                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            else -> { // "2x1" and default
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    activePages.take(2).forEachIndexed { index, p ->
                                        CollageSlotCard(
                                            page = p,
                                            slotIndex = index,
                                            transform = slotTransforms[index] ?: SlotTransform(),
                                            onTransformChange = { slotTransforms[index] = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Live Watermark Preview Overlay
                        if (watermarkConfig.text.isNotBlank()) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color = watermarkConfig.color
                                    alpha = (watermarkConfig.opacity * 255).toInt().coerceIn(0, 255)
                                    textSize = watermarkConfig.fontSizeSp * (size.width / 400f)
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isFakeBoldText = true
                                }

                                if (watermarkConfig.isTiled) {
                                    val stepX = size.width / 3f
                                    val stepY = size.height / 5f
                                    for (y in 0..5) {
                                        for (x in 0..3) {
                                            val px = x * stepX + (if (y % 2 == 1) stepX / 2f else 0f)
                                            val py = y * stepY
                                            rotate(watermarkConfig.rotationAngle, androidx.compose.ui.geometry.Offset(px, py)) {
                                                drawContext.canvas.nativeCanvas.drawText(watermarkConfig.text, px, py, paint)
                                            }
                                        }
                                    }
                                } else {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    rotate(watermarkConfig.rotationAngle, androidx.compose.ui.geometry.Offset(cx, cy)) {
                                        drawContext.canvas.nativeCanvas.drawText(watermarkConfig.text, cx, cy, paint)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full Watermark Customization Dialog (Improvement 1)
        if (showWatermarkInput) {
            WatermarkCustomizationDialog(
                initialConfig = watermarkConfig,
                onDismiss = { showWatermarkInput = false },
                onSave = { updated ->
                    watermarkConfig = updated
                    showWatermarkInput = false
                }
            )
        }
    }
}

@Composable
private fun CollageSlotCard(
    page: Page,
    slotIndex: Int,
    transform: SlotTransform,
    onTransformChange: (SlotTransform) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFF2F2F2),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(page.id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (transform.scale * zoom).coerceIn(0.5f, 4f)
                        val newPanX = transform.panX + pan.x
                        val newPanY = transform.panY + pan.y
                        onTransformChange(SlotTransform(newScale, newPanX, newPanY))
                    }
                }
        ) {
            AsyncImage(
                model = File(page.processedImagePath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.panX
                        translationY = transform.panY
                    }
            )
        }
    }
}

@Composable
private fun WatermarkCustomizationDialog(
    initialConfig: CollageWatermarkConfig,
    onDismiss: () -> Unit,
    onSave: (CollageWatermarkConfig) -> Unit
) {
    val initialText = if (initialConfig.text.isBlank()) "CONFIDENTIAL" else initialConfig.text
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(0, initialText.length)
            )
        )
    }

    var selectedColor by remember { mutableIntStateOf(initialConfig.color) }
    var opacity by remember { mutableFloatStateOf(initialConfig.opacity) }
    var fontSizeSp by remember { mutableFloatStateOf(initialConfig.fontSizeSp) }
    var rotationAngle by remember { mutableFloatStateOf(initialConfig.rotationAngle) }
    var isTiled by remember { mutableStateOf(initialConfig.isTiled) }

    val presetColors = listOf(
        0xFF111111.toInt(), // Black
        0xFF888888.toInt(), // Gray
        0xFFD32F2F.toInt(), // Red
        0xFF1976D2.toInt(), // Blue
        0xFF00A86B.toInt()  // Emerald
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Watermark Studio",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Text input with auto-selected "CONFIDENTIAL"
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    label = { Text("Watermark Text") },
                    placeholder = { Text("CONFIDENTIAL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Color Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Color:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    presetColors.forEach { c ->
                        val isPicked = selectedColor == c
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (isPicked) 2.5.dp else 1.dp,
                                    color = if (isPicked) ScannerGreen else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = c },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPicked) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Opacity / Transparency Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacity", fontSize = 12.sp)
                        Text("${(opacity * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.10f..0.90f
                    )
                }

                // Font Size Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Size", fontSize = 12.sp)
                        Text("${fontSizeSp.toInt()} sp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = it },
                        valueRange = 16f..64f
                    )
                }

                // Rotation Angle Slider & Quick Angle Presets
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rotation Angle", fontSize = 12.sp)
                        Text("${rotationAngle.toInt()}°", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = rotationAngle,
                        onValueChange = { rotationAngle = it },
                        valueRange = -90f..90f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(-45f, 0f, 45f, 90f).forEach { ang ->
                            FilterChip(
                                selected = rotationAngle == ang,
                                onClick = { rotationAngle = ang },
                                label = { Text("${ang.toInt()}°", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Tiling Mode Toggle (Multiple vs Single)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Repeat Tile Pattern", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Multiply across whole sheet", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = isTiled,
                        onCheckedChange = { isTiled = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        CollageWatermarkConfig(
                            text = textFieldValue.text,
                            color = selectedColor,
                            fontSizeSp = fontSizeSp,
                            opacity = opacity,
                            rotationAngle = rotationAngle,
                            isTiled = isTiled
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
            ) {
                Text("Apply", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    // Clear watermark
                    onSave(initialConfig.copy(text = ""))
                }
            ) {
                Text("Remove")
            }
        }
    )
}

@Composable
private fun TemplateVisualPreview(id: String, isSelected: Boolean) {
    val borderColor = if (isSelected) ScannerGreen else Color(0xFF777777)
    when (id) {
        "driver_license" -> {
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .weight(1f)
                ) {}
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .weight(1f)
                ) {}
            }
        }
        "passport", "1x2" -> {
            Row(
                modifier = Modifier
                    .size(26.dp, 28.dp)
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
        "question_book" -> {
            Row(
                modifier = Modifier
                    .size(26.dp, 28.dp)
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
                    .size(26.dp, 28.dp)
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
        "6_grid" -> {
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
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
        else -> { // 2x1
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
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
