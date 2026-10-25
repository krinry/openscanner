package org.openscanner.ui.detail.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.cv.CollageGenerator
import org.openscanner.cv.CollageLayout
import org.openscanner.cv.CollageWatermarkConfig
import org.openscanner.cv.NativeScannerEngine
import org.openscanner.cv.SlotTransform
import org.openscanner.data.model.Page
import org.openscanner.ui.crop.CropScreen
import org.openscanner.ui.theme.ScannerGreen
import java.io.File
import java.io.FileOutputStream

data class CollageTemplateOption(
    val id: String,
    val title: String,
    val layout: CollageLayout,
    val description: String
)

val COLLAGE_TEMPLATES = listOf(
    CollageTemplateOption("4_grid", "4-Grid", CollageLayout.FOUR_GRID, "4-in-1 Grid"),
    CollageTemplateOption("6_grid", "6-Grid", CollageLayout.SIX_GRID, "6-in-1 Grid"),
    CollageTemplateOption("2x1", "2x1 Vertical", CollageLayout.TWO_VERTICAL, "2 Vertical"),
    CollageTemplateOption("1x2", "1x2 Side-by-Side", CollageLayout.TWO_HORIZONTAL, "2 Side-by-Side"),
    CollageTemplateOption("3x1", "3x1 Vertical", CollageLayout.THREE_VERTICAL, "3 Vertical"),
    CollageTemplateOption("3_top_two", "3-in-1 Split", CollageLayout.THREE_TOP_TWO_BOTTOM, "1 Top, 2 Bottom"),
    CollageTemplateOption("driver_license", "Driver License", CollageLayout.DRIVER_LICENSE, "ID Card 2-in-1"),
    CollageTemplateOption("passport", "Passport", CollageLayout.PASSPORT, "Passport & Details"),
    CollageTemplateOption("question_book", "Question Book", CollageLayout.QUESTION_BOOK, "Question Paper")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageStudioDialog(
    pages: List<Page>,
    onDismiss: () -> Unit,
    onGenerateCollage: (
        layout: CollageLayout,
        pageIds: List<Long>,
        watermark: CollageWatermarkConfig?,
        transforms: Map<Int, SlotTransform>?,
        drawBorders: Boolean,
        slotCustomPaths: Map<Int, String>?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTemplate by remember { mutableStateOf(COLLAGE_TEMPLATES[0]) } // 4-grid default
    var activeBottomTab by remember { mutableStateOf("template") } // "template", "page", "watermark", "size"
    var selectedPaperSize by remember { mutableStateOf("A4") }
    var showWatermarkInput by remember { mutableStateOf(false) }

    // Map each slot index (0 until maxPages) to its assigned Page (with auto-duplication)
    val slotAssignments = remember { mutableStateMapOf<Int, Page>() }
    // Transform parameters per slot (pan, zoom, rotation)
    val slotTransforms = remember { mutableStateMapOf<Int, SlotTransform>() }
    // Cropped image file path per slot if user cropped that slot
    val slotCroppedPaths = remember { mutableStateMapOf<Int, String>() }

    // Currently focused slot for editing (pan, zoom, crop, page change)
    var selectedSlotIndex by remember { mutableStateOf<Int?>(null) }

    // State for launching the modal CropScreen
    var cropEditingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var cropEditingPage by remember { mutableStateOf<Page?>(null) }

    // Populate and duplicate images across all slots whenever template or pages change
    LaunchedEffect(selectedTemplate, pages) {
        val slotCount = selectedTemplate.layout.maxPages
        if (pages.isNotEmpty()) {
            for (i in 0 until slotCount) {
                // Auto-duplicate images to fill all slots (e.g. 2 images fill 4-grid and 6-grid)
                slotAssignments[i] = pages[i % pages.size]
            }
        }
        if (selectedSlotIndex != null && selectedSlotIndex!! >= slotCount) {
            selectedSlotIndex = null
        }
    }

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color(0xFF0D0D0D),
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                text = "Collage Studio",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val slotCount = selectedTemplate.layout.maxPages
                                val slotPageIds = (0 until slotCount).map { i ->
                                    slotAssignments[i]?.id ?: pages.getOrNull(i % pages.size.coerceAtLeast(1))?.id ?: 0L
                                }
                                val finalWatermark = if (watermarkConfig.text.isNotBlank()) watermarkConfig else null
                                onGenerateCollage(
                                    selectedTemplate.layout,
                                    slotPageIds,
                                    finalWatermark,
                                    slotTransforms.toMap(),
                                    false, // No borders around images
                                    slotCroppedPaths.toMap()
                                )
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
                        // Tab Content
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
                                            .width(84.dp)
                                            .height(76.dp)
                                            .clickable {
                                                selectedTemplate = template
                                                val slotCount = template.layout.maxPages
                                                if (pages.isNotEmpty()) {
                                                    for (i in 0 until slotCount) {
                                                        slotAssignments[i] = pages[i % pages.size]
                                                    }
                                                }
                                                selectedSlotIndex = null
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                if (selectedSlotIndex != null) {
                                    Text(
                                        text = "Tap a document page to assign to Slot ${(selectedSlotIndex ?: 0) + 1}:",
                                        color = ScannerGreen,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Document Pages (Tap an image to replace, or tap below to swap):",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(pages, key = { it.id }) { page ->
                                        val isAssignedToSelectedSlot = selectedSlotIndex != null &&
                                                slotAssignments[selectedSlotIndex]?.id == page.id

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF222222),
                                            border = BorderStroke(
                                                2.dp,
                                                if (isAssignedToSelectedSlot) ScannerGreen else Color(0xFF383838)
                                            ),
                                            modifier = Modifier
                                                .size(54.dp, 72.dp)
                                                .clickable {
                                                    val targetSlot = selectedSlotIndex ?: 0
                                                    slotAssignments[targetSlot] = page
                                                    slotCroppedPaths.remove(targetSlot)
                                                    slotTransforms[targetSlot] = SlotTransform()
                                                    Toast.makeText(
                                                        context,
                                                        "Assigned Page ${page.pageIndex + 1} to Slot ${targetSlot + 1}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        ) {
                                            Box {
                                                AsyncImage(
                                                    model = File(page.thumbnailPath.ifEmpty { page.processedImagePath }),
                                                    contentDescription = "Page ${page.pageIndex + 1}",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                                                    color = Color.Black.copy(alpha = 0.6f),
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                ) {
                                                    Text(
                                                        text = "P${page.pageIndex + 1}",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                    )
                                                }
                                                if (isAssignedToSelectedSlot) {
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
                        }

                        HorizontalDivider(color = Color(0xFF242424), thickness = 0.5.dp)

                        // Bottom Navigation Bar without border button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CollageBottomActionItem(
                                icon = Icons.Outlined.GridView,
                                label = "Template",
                                isActive = activeBottomTab == "template",
                                onClick = { activeBottomTab = "template" }
                            )
                            CollageBottomActionItem(
                                icon = Icons.Default.Add,
                                label = "Page",
                                isActive = activeBottomTab == "page",
                                onClick = { activeBottomTab = "page" }
                            )
                            CollageBottomActionItem(
                                icon = Icons.Outlined.WaterDamage,
                                label = if (watermarkConfig.text.isNotBlank()) "Watermark*" else "Watermark",
                                isActive = activeBottomTab == "watermark",
                                onClick = {
                                    activeBottomTab = "watermark"
                                    showWatermarkInput = true
                                }
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
                // Center Live Freeform Canvas Viewport
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFF0D0D0D)),
                    contentAlignment = Alignment.Center
                ) {
                    var sheetSize by remember { mutableStateOf(IntSize.Zero) }

                    // Clean White Sheet Preview (No grey box slots!)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f / 1.414f)
                            .padding(6.dp)
                            .onSizeChanged { sheetSize = it }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    // Tapping empty page background deselects image
                                    detectTapGestures(onTap = { selectedSlotIndex = null })
                                }
                        ) {
                            if (sheetSize.width > 0 && sheetSize.height > 0) {
                                val sw = sheetSize.width.toFloat()
                                val sh = sheetSize.height.toFloat()
                                val slots = CollageGenerator.calculateSlotRects(
                                    selectedTemplate.layout,
                                    sheetSize.width,
                                    sheetSize.height
                                )
                                val slotCount = selectedTemplate.layout.maxPages

                                // Render each image floating on the white canvas (no boxes!)
                                for (i in 0 until minOf(slotCount, slots.size)) {
                                    val initRect = slots[i]
                                    val slotW = (initRect.right - initRect.left).toFloat()
                                    val slotH = (initRect.bottom - initRect.top).toFloat()
                                    val initCx = (initRect.left + initRect.right) / 2f
                                    val initCy = (initRect.top + initRect.bottom) / 2f

                                    val curTransform = slotTransforms[i] ?: SlotTransform()
                                    val currentCx = initCx + curTransform.panX * sw
                                    val currentCy = initCy + curTransform.panY * sh

                                    val page = slotAssignments[i]
                                        ?: pages.getOrNull(i % pages.size.coerceAtLeast(1))
                                    val customPath = slotCroppedPaths[i]
                                    val isSelected = selectedSlotIndex == i

                                    PhotoshopCanvasItem(
                                        slotIndex = i,
                                        page = page,
                                        customImagePath = customPath,
                                        centerX = currentCx,
                                        centerY = currentCy,
                                        baseWidth = slotW,
                                        baseHeight = slotH,
                                        sheetWidth = sw,
                                        sheetHeight = sh,
                                        transform = curTransform,
                                        isSelected = isSelected,
                                        onSelect = { selectedSlotIndex = i },
                                        onTransformChange = { newTransform ->
                                            slotTransforms[i] = newTransform
                                        }
                                    )
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
                                                rotate(watermarkConfig.rotationAngle, Offset(px, py)) {
                                                    drawContext.canvas.nativeCanvas.drawText(watermarkConfig.text, px, py, paint)
                                                }
                                            }
                                        }
                                    } else {
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        rotate(watermarkConfig.rotationAngle, Offset(cx, cy)) {
                                            drawContext.canvas.nativeCanvas.drawText(watermarkConfig.text, cx, cy, paint)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Photoshop Quick Edit Bar for selected image
                    selectedSlotIndex?.let { slotIdx ->
                        val curTransform = slotTransforms[slotIdx] ?: SlotTransform()
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1E1E1E),
                            border = BorderStroke(1.dp, ScannerGreen),
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Slot ${slotIdx + 1}",
                                    color = ScannerGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )

                                VerticalDivider(modifier = Modifier.height(18.dp), color = Color(0xFF444444))

                                // Crop button
                                IconButton(
                                    onClick = {
                                        val p = slotAssignments[slotIdx]
                                            ?: pages.getOrNull(slotIdx % pages.size.coerceAtLeast(1))
                                        if (p != null) {
                                            cropEditingSlotIndex = slotIdx
                                            cropEditingPage = p
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Crop,
                                        contentDescription = "Crop",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Zoom Out [-]
                                IconButton(
                                    onClick = {
                                        val newScale = (curTransform.scale - 0.15f).coerceAtLeast(0.2f)
                                        slotTransforms[slotIdx] = curTransform.copy(scale = newScale)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Remove,
                                        contentDescription = "Zoom Out",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Zoom % text
                                Text(
                                    text = "${(curTransform.scale * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Zoom In [+]
                                IconButton(
                                    onClick = {
                                        val newScale = (curTransform.scale + 0.15f).coerceAtMost(5.0f)
                                        slotTransforms[slotIdx] = curTransform.copy(scale = newScale)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Add,
                                        contentDescription = "Zoom In",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Rotate 90 degrees
                                IconButton(
                                    onClick = {
                                        val newRot = (curTransform.rotation + 90f) % 360f
                                        slotTransforms[slotIdx] = curTransform.copy(rotation = newRot)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.RotateRight,
                                        contentDescription = "Rotate 90",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Reset Transform
                                IconButton(
                                    onClick = {
                                        slotTransforms[slotIdx] = SlotTransform()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.RestartAlt,
                                        contentDescription = "Reset",
                                        tint = Color(0xFFDDDDDD),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Close / Deselect
                                IconButton(
                                    onClick = { selectedSlotIndex = null },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Deselect",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Full Watermark Customization Dialog
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

            // Modal Integrated CropScreen for slot
            cropEditingPage?.let { pageToCrop ->
                val rawPath = pageToCrop.originalImagePath.ifEmpty { pageToCrop.processedImagePath }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    CropScreen(
                        imagePath = rawPath,
                        docId = pageToCrop.documentId,
                        onCropConfirmed = { quad ->
                            val slotIndex = cropEditingSlotIndex ?: return@CropScreen
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val rawBitmap = BitmapFactory.decodeFile(rawPath)
                                    if (rawBitmap != null) {
                                        val targetW = Math.round(
                                            maxOf(
                                                Math.hypot((quad.topRight.x - quad.topLeft.x).toDouble(), (quad.topRight.y - quad.topLeft.y).toDouble()),
                                                Math.hypot((quad.bottomRight.x - quad.bottomLeft.x).toDouble(), (quad.bottomRight.y - quad.bottomLeft.y).toDouble())
                                            )
                                        ).toInt().coerceAtLeast(100)
                                        val targetH = Math.round(
                                            maxOf(
                                                Math.hypot((quad.bottomLeft.x - quad.topLeft.x).toDouble(), (quad.bottomLeft.y - quad.topLeft.y).toDouble()),
                                                Math.hypot((quad.bottomRight.x - quad.topRight.x).toDouble(), (quad.bottomRight.y - quad.topRight.y).toDouble())
                                            )
                                        ).toInt().coerceAtLeast(100)

                                        val warped = NativeScannerEngine.warpDocument(rawBitmap, quad, targetW, targetH)
                                        val croppedFile = File(
                                            context.cacheDir,
                                            "collage_crop_${pageToCrop.id}_${slotIndex}_${System.currentTimeMillis()}.jpg"
                                        )
                                        FileOutputStream(croppedFile).use { out ->
                                            warped.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                        }
                                        if (warped != rawBitmap && !warped.isRecycled) {
                                            warped.recycle()
                                        }
                                        if (!rawBitmap.isRecycled) {
                                            rawBitmap.recycle()
                                        }
                                        withContext(Dispatchers.Main) {
                                            slotCroppedPaths[slotIndex] = croppedFile.absolutePath
                                            cropEditingSlotIndex = null
                                            cropEditingPage = null
                                            Toast.makeText(context, "Slot ${slotIndex + 1} cropped successfully", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        cropEditingSlotIndex = null
                                        cropEditingPage = null
                                    }
                                }
                            }
                        },
                        onClose = {
                            cropEditingSlotIndex = null
                            cropEditingPage = null
                        }
                    )
                }
            }
        }
    }
}

/**
 * Freeform Photoshop-style Canvas Item without rigid boxes.
 * Floats directly on the white sheet with 1-finger move, 2-finger zoom/rotate, and corner resize handles.
 */
@Composable
private fun PhotoshopCanvasItem(
    slotIndex: Int,
    page: Page?,
    customImagePath: String?,
    centerX: Float,
    centerY: Float,
    baseWidth: Float,
    baseHeight: Float,
    sheetWidth: Float,
    sheetHeight: Float,
    transform: SlotTransform,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTransformChange: (SlotTransform) -> Unit
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerX - (baseWidth / 2f)).toInt(),
                    (centerY - (baseHeight / 2f)).toInt()
                )
            }
            .size(
                width = with(density) { baseWidth.toDp() },
                height = with(density) { baseHeight.toDp() }
            )
            .graphicsLayer {
                scaleX = transform.scale
                scaleY = transform.scale
                rotationZ = transform.rotation
            }
    ) {
        // Image body with single finger drag (pan) and 2-finger pinch/twist (zoom & rotate)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slotIndex) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onSelect()
                        do {
                            val event = awaitPointerEvent()
                            val pointerCount = event.changes.count { it.pressed }
                            if (pointerCount == 1) {
                                // Single-finger drag: move image anywhere across the entire page
                                val pan = event.calculatePan()
                                if (pan != Offset.Zero) {
                                    val newPanX = transform.panX + (pan.x / sheetWidth.coerceAtLeast(1f))
                                    val newPanY = transform.panY + (pan.y / sheetHeight.coerceAtLeast(1f))
                                    onTransformChange(transform.copy(panX = newPanX, panY = newPanY))
                                    event.changes.forEach { it.consume() }
                                }
                            } else if (pointerCount >= 2) {
                                // Two-finger gesture: real physical rotation and pinch-to-zoom
                                val zoom = event.calculateZoom()
                                val rotation = event.calculateRotation()
                                val pan = event.calculatePan()
                                if (zoom != 1f || rotation != 0f || pan != Offset.Zero) {
                                    val newScale = (transform.scale * zoom).coerceIn(0.2f, 5.0f)
                                    val newRot = transform.rotation + rotation
                                    val newPanX = transform.panX + (pan.x / sheetWidth.coerceAtLeast(1f))
                                    val newPanY = transform.panY + (pan.y / sheetHeight.coerceAtLeast(1f))
                                    onTransformChange(
                                        transform.copy(
                                            scale = newScale,
                                            panX = newPanX,
                                            panY = newPanY,
                                            rotation = newRot
                                        )
                                    )
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            if (page != null) {
                val imagePath = customImagePath ?: page.processedImagePath
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = "Image ${slotIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Empty",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Photoshop-style Selection Frame & Corner Handles (shown ONLY when selected)
        if (isSelected) {
            // Selection Bounding Box Outline
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, ScannerGreen, RoundedCornerShape(2.dp))
            )

            // Top-Left Corner Resize Handle
            CornerResizeHandle(
                alignment = Alignment.TopStart,
                onDragDelta = { dx, dy ->
                    val deltaScale = (-dx - dy) / baseWidth.coerceAtLeast(10f)
                    val newScale = (transform.scale + deltaScale).coerceIn(0.2f, 5.0f)
                    onTransformChange(transform.copy(scale = newScale))
                }
            )

            // Top-Right Corner Resize Handle
            CornerResizeHandle(
                alignment = Alignment.TopEnd,
                onDragDelta = { dx, dy ->
                    val deltaScale = (dx - dy) / baseWidth.coerceAtLeast(10f)
                    val newScale = (transform.scale + deltaScale).coerceIn(0.2f, 5.0f)
                    onTransformChange(transform.copy(scale = newScale))
                }
            )

            // Bottom-Left Corner Resize Handle
            CornerResizeHandle(
                alignment = Alignment.BottomStart,
                onDragDelta = { dx, dy ->
                    val deltaScale = (-dx + dy) / baseWidth.coerceAtLeast(10f)
                    val newScale = (transform.scale + deltaScale).coerceIn(0.2f, 5.0f)
                    onTransformChange(transform.copy(scale = newScale))
                }
            )

            // Bottom-Right Corner Resize Handle
            CornerResizeHandle(
                alignment = Alignment.BottomEnd,
                onDragDelta = { dx, dy ->
                    val deltaScale = (dx + dy) / baseWidth.coerceAtLeast(10f)
                    val newScale = (transform.scale + deltaScale).coerceIn(0.2f, 5.0f)
                    onTransformChange(transform.copy(scale = newScale))
                }
            )

            // Slot badge in top corner
            Surface(
                shape = RoundedCornerShape(bottomEnd = 4.dp),
                color = ScannerGreen,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "${slotIndex + 1}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CornerResizeHandle(
    alignment: Alignment,
    onDragDelta: (Float, Float) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(2.dp, ScannerGreen),
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(20.dp)
            .align(alignment)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount.x, dragAmount.y)
                }
            }
    ) {}
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

                // Opacity Slider
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

                // Tiling Mode Toggle
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
        "3x1" -> {
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().weight(1f)) {}
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().weight(1f)) {}
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().weight(1f)) {}
            }
        }
        "3_top_two" -> {
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().weight(1f)) {}
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
            }
        }
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
                    modifier = Modifier.fillMaxWidth(0.92f).weight(1f)
                ) {}
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth(0.92f).weight(1f)
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
                    modifier = Modifier.fillMaxHeight().weight(1f)
                ) {}
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxHeight().weight(1f)
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
                    modifier = Modifier.fillMaxHeight().weight(1f)
                ) {}
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxHeight().weight(1f)
                ) {}
            }
        }
        else -> { // 2x1 and others
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {}
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth().weight(1f)
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
