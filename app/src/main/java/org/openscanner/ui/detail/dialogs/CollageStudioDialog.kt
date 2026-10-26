package org.openscanner.ui.detail.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.collection.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
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
import org.openscanner.data.repository.CollagePageSpec
import org.openscanner.ui.crop.CropScreen
import org.openscanner.ui.theme.ScannerGreen
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synchronous in-memory bitmap cache to completely eliminate image flickering during touch gestures.
 */
object CollageBitmapCache {
    private val memoryCache = LruCache<String, Bitmap>(64)

    fun get(path: String): Bitmap? {
        val bmp = memoryCache.get(path)
        if (bmp != null && !bmp.isRecycled) return bmp
        return null
    }

    fun put(path: String, bitmap: Bitmap) {
        memoryCache.put(path, bitmap)
    }

    fun getOrDecode(path: String, reqW: Int, reqH: Int): Bitmap? {
        val cached = get(path)
        if (cached != null) return cached
        if (File(path).exists()) {
            val decoded = CollageGenerator.decodeSampledBitmap(path, reqW, reqH)
            if (decoded != null) {
                memoryCache.put(path, decoded)
                return decoded
            }
        }
        return null
    }

    fun clear() {
        memoryCache.evictAll()
    }
}

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

/**
 * State of a single collage sheet / page in multi-page mode.
 */
class CollageSheetState(
    val id: String = java.util.UUID.randomUUID().toString(),
    val slotAssignments: SnapshotStateMap<Int, Page> = mutableStateMapOf(),
    val slotTransforms: SnapshotStateMap<Int, SlotTransform> = mutableStateMapOf(),
    val slotCroppedPaths: SnapshotStateMap<Int, String> = mutableStateMapOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageStudioDialog(
    pages: List<Page>,
    onDismiss: () -> Unit,
    onGenerateCollage: ((
        layout: CollageLayout,
        pageIds: List<Long>,
        watermark: CollageWatermarkConfig?,
        transforms: Map<Int, SlotTransform>?,
        drawBorders: Boolean,
        slotCustomPaths: Map<Int, String>?
    ) -> Unit)? = null,
    onGenerateMultiCollage: ((
        specs: List<CollagePageSpec>,
        watermark: CollageWatermarkConfig?,
        drawBorders: Boolean
    ) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedTemplate by remember { mutableStateOf(COLLAGE_TEMPLATES[0]) } // 4-grid default
    var activeBottomTab by remember { mutableStateOf("template") } // "template", "page", "watermark", "size"
    var selectedPaperSize by remember { mutableStateOf("A4") }
    var showWatermarkInput by remember { mutableStateOf(false) }

    // Multi-page state list
    val sheets = remember { mutableStateListOf<CollageSheetState>() }

    // Currently focused sheet & slot for editing
    var selectedSheetIndex by remember { mutableStateOf<Int?>(null) }
    var selectedSlotIndex by remember { mutableStateOf<Int?>(null) }

    // State for launching the modal CropScreen
    var cropEditingSheetIndex by remember { mutableStateOf<Int?>(null) }
    var cropEditingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var cropEditingPage by remember { mutableStateOf<Page?>(null) }

    // Preload all document bitmaps into memory cache at startup to completely prevent flickering
    LaunchedEffect(pages) {
        withContext(Dispatchers.IO) {
            for (p in pages) {
                val path = p.processedImagePath.ifEmpty { p.originalImagePath }
                if (path.isNotBlank() && File(path).exists()) {
                    CollageBitmapCache.getOrDecode(path, 1024, 1024)
                }
            }
        }
    }

    // Auto-calculate pages when template is chosen (e.g. 40 images with 2-grid -> 20 pages automatically created!)
    fun recalculateSheets(template: CollageTemplateOption, docPages: List<Page>) {
        val slotsPerPage = template.layout.maxPages
        sheets.clear()
        if (docPages.isEmpty()) {
            sheets.add(CollageSheetState())
            return
        }
        val neededSheets = maxOf(1, (docPages.size + slotsPerPage - 1) / slotsPerPage)
        for (sheetIdx in 0 until neededSheets) {
            val sheet = CollageSheetState()
            for (slotIdx in 0 until slotsPerPage) {
                val pageIdx = sheetIdx * slotsPerPage + slotIdx
                if (pageIdx < docPages.size) {
                    sheet.slotAssignments[slotIdx] = docPages[pageIdx]
                } else {
                    sheet.slotAssignments[slotIdx] = docPages[pageIdx % docPages.size]
                }
            }
            sheets.add(sheet)
        }
        selectedSheetIndex = null
        selectedSlotIndex = null
    }

    LaunchedEffect(selectedTemplate) {
        recalculateSheets(selectedTemplate, pages)
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

    val paperAspectRatio = when (selectedPaperSize) {
        "Letter" -> 8.5f / 11f
        else -> 1f / 1.414f // Standard A4 and A5
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
                                text = "Collage",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val specs = sheets.map { sheet ->
                                    val slotCount = selectedTemplate.layout.maxPages
                                    val slotPageIds = (0 until slotCount).map { i ->
                                        sheet.slotAssignments[i]?.id ?: 0L
                                    }
                                    CollagePageSpec(
                                        layout = selectedTemplate.layout,
                                        pageIds = slotPageIds,
                                        slotTransforms = sheet.slotTransforms.toMap(),
                                        slotCustomPaths = sheet.slotCroppedPaths.toMap()
                                    )
                                }
                                val finalWatermark = if (watermarkConfig.text.isNotBlank()) watermarkConfig else null
                                if (onGenerateMultiCollage != null) {
                                    onGenerateMultiCollage(specs, finalWatermark, false)
                                } else if (onGenerateCollage != null && specs.isNotEmpty()) {
                                    val firstSpec = specs[0]
                                    onGenerateCollage(
                                        firstSpec.layout,
                                        firstSpec.pageIds,
                                        finalWatermark,
                                        firstSpec.slotTransforms,
                                        false,
                                        firstSpec.slotCustomPaths
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "Done",
                                color = Color.White,
                                fontSize = 14.sp,
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
                        // Tab Drawer Content
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
                                                recalculateSheets(template, pages)
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
                                val currentSheet = selectedSheetIndex?.let { sheets.getOrNull(it) } ?: sheets.firstOrNull()
                                val targetSlot = selectedSlotIndex ?: 0

                                Text(
                                    text = "Tap a document page to assign to Slot ${targetSlot + 1} (Page ${(selectedSheetIndex ?: 0) + 1}):",
                                    color = ScannerGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(pages, key = { it.id }) { page ->
                                        val isAssigned = currentSheet?.slotAssignments?.get(targetSlot)?.id == page.id

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF222222),
                                            border = BorderStroke(
                                                2.dp,
                                                if (isAssigned) ScannerGreen else Color(0xFF383838)
                                            ),
                                            modifier = Modifier
                                                .size(54.dp, 72.dp)
                                                .clickable {
                                                    if (currentSheet != null) {
                                                        currentSheet.slotAssignments[targetSlot] = page
                                                        currentSheet.slotCroppedPaths.remove(targetSlot)
                                                        currentSheet.slotTransforms[targetSlot] = SlotTransform()
                                                        Toast.makeText(
                                                            context,
                                                            "Assigned Page ${page.pageIndex + 1} to Slot ${targetSlot + 1}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
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
                                                if (isAssigned) {
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

                        // Bottom Navigation Bar with [+ Page], [Add Watermark], [Template], [Size]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // [+ Page] - Adds a BLANK PAGE to canvas
                            CollageBottomActionItem(
                                icon = Icons.Outlined.PostAdd,
                                label = "Page",
                                isActive = activeBottomTab == "page",
                                onClick = {
                                    val newSheet = CollageSheetState()
                                    // Leaves slotAssignments empty -> blank page!
                                    sheets.add(newSheet)
                                    selectedSheetIndex = sheets.size - 1
                                    selectedSlotIndex = 0
                                    activeBottomTab = "page"
                                    scope.launch {
                                        listState.animateScrollToItem(sheets.size - 1)
                                    }
                                    Toast.makeText(context, "Added Blank Page ${sheets.size}", Toast.LENGTH_SHORT).show()
                                }
                            )

                            // Watermark
                            CollageBottomActionItem(
                                icon = Icons.Outlined.WaterDamage,
                                label = if (watermarkConfig.text.isNotBlank()) "Watermark*" else "Add a watermark",
                                isActive = activeBottomTab == "watermark",
                                onClick = {
                                    activeBottomTab = "watermark"
                                    showWatermarkInput = true
                                }
                            )

                            // Template
                            CollageBottomActionItem(
                                icon = Icons.Outlined.GridView,
                                label = "Template",
                                isActive = activeBottomTab == "template",
                                onClick = { activeBottomTab = "template" }
                            )

                            // Paper Size (A4, A5, Letter)
                            CollageBottomActionItem(
                                icon = Icons.Outlined.AspectRatio,
                                label = selectedPaperSize,
                                isActive = activeBottomTab == "size",
                                onClick = {
                                    selectedPaperSize = when (selectedPaperSize) {
                                        "A4" -> "A5"
                                        "A5" -> "Letter"
                                        else -> "A4"
                                    }
                                }
                            )
                        }
                    }
                }
            ) { padding ->
                // Vertically scrollable multi-page canvas area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFF0D0D0D))
                ) {
                    // Disable LazyColumn scrolling when an item is selected so it never steals touch gestures!
                    val isInteractingWithItem = selectedSlotIndex != null

                    LazyColumn(
                        state = listState,
                        userScrollEnabled = !isInteractingWithItem,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(sheets, key = { _, sheet -> sheet.id }) { sheetIdx, sheet ->
                            CollageSheetCard(
                                sheetIndex = sheetIdx,
                                totalSheets = sheets.size,
                                sheetState = sheet,
                                layout = selectedTemplate.layout,
                                aspectRatio = paperAspectRatio,
                                isSheetSelected = selectedSheetIndex == sheetIdx,
                                selectedSlotIndex = if (selectedSheetIndex == sheetIdx) selectedSlotIndex else null,
                                watermarkConfig = watermarkConfig,
                                onSelectSlot = { slot ->
                                    selectedSheetIndex = sheetIdx
                                    selectedSlotIndex = slot
                                },
                                onDeselect = {
                                    if (selectedSheetIndex == sheetIdx) {
                                        selectedSheetIndex = null
                                        selectedSlotIndex = null
                                    }
                                },
                                onDeleteSlot = { slot ->
                                    sheet.slotAssignments.remove(slot)
                                    sheet.slotCroppedPaths.remove(slot)
                                    sheet.slotTransforms.remove(slot)
                                    if (selectedSheetIndex == sheetIdx && selectedSlotIndex == slot) {
                                        selectedSlotIndex = null
                                    }
                                },
                                onDeleteSheet = {
                                    if (sheets.size > 1) {
                                        sheets.removeAt(sheetIdx)
                                        if (selectedSheetIndex == sheetIdx) {
                                            selectedSheetIndex = null
                                            selectedSlotIndex = null
                                        }
                                    }
                                },
                                onTransformChange = { slot, newTransform ->
                                    sheet.slotTransforms[slot] = newTransform
                                }
                            )
                        }
                    }

                    // Integrated Floating Window with All Controls (Zoom, Rotation, +90°, X, Y, Z Scrubbers)
                    if (selectedSheetIndex != null && selectedSlotIndex != null) {
                        val sIdx = selectedSheetIndex!!
                        val slotIdx = selectedSlotIndex!!
                        val currentSheet = sheets.getOrNull(sIdx)
                        if (currentSheet != null) {
                            FloatingTransformToolbar(
                                sheetIndex = sIdx,
                                slotIndex = slotIdx,
                                sheet = currentSheet,
                                onCrop = {
                                    val p = currentSheet.slotAssignments[slotIdx]
                                    if (p != null) {
                                        cropEditingSheetIndex = sIdx
                                        cropEditingSlotIndex = slotIdx
                                        cropEditingPage = p
                                    }
                                },
                                onClose = {
                                    selectedSheetIndex = null
                                    selectedSlotIndex = null
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
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

            // Modal CropScreen
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
                            val sheetIndex = cropEditingSheetIndex ?: return@CropScreen
                            val slotIndex = cropEditingSlotIndex ?: return@CropScreen
                            val targetSheet = sheets.getOrNull(sheetIndex) ?: return@CropScreen

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
                                        CollageBitmapCache.put(croppedFile.absolutePath, warped)
                                        if (!rawBitmap.isRecycled) {
                                            rawBitmap.recycle()
                                        }
                                        withContext(Dispatchers.Main) {
                                            targetSheet.slotCroppedPaths[slotIndex] = croppedFile.absolutePath
                                            cropEditingSheetIndex = null
                                            cropEditingSlotIndex = null
                                            cropEditingPage = null
                                            Toast.makeText(context, "Slot ${slotIndex + 1} cropped successfully", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        cropEditingSheetIndex = null
                                        cropEditingSlotIndex = null
                                        cropEditingPage = null
                                    }
                                }
                            }
                        },
                        onClose = {
                            cropEditingSheetIndex = null
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
 * Isolated Floating Window hosting all controls:
 * - Row 1: P:S Badge, Crop button, Zoom scrubber, Rotation scrubber, single 90° button, Reset button, Close button.
 * - Row 2: X, Y, Z Scrubbers (click, hold & drag with up=+, down=-, right=+, left=-) + Center (0,0) alignment button.
 * Being an isolated composable, state reads here never recompose the parent canvas or LazyColumn!
 */
@Composable
private fun FloatingTransformToolbar(
    sheetIndex: Int,
    slotIndex: Int,
    sheet: CollageSheetState,
    onCrop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val curTransform = sheet.slotTransforms[slotIndex] ?: SlotTransform()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E),
        border = BorderStroke(1.2.dp, ScannerGreen),
        shadowElevation = 14.dp,
        modifier = modifier.padding(bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Badge, Crop, Zoom Scrubber, Rotation Scrubber, Single 90° button, Reset, Close
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = ScannerGreen.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    Text(
                        text = "P${sheetIndex + 1}:S${slotIndex + 1}",
                        color = ScannerGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Crop
                IconButton(onClick = onCrop, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Crop, contentDescription = "Crop", tint = Color.White, modifier = Modifier.size(17.dp))
                }

                // Zoom Scrubber (Drag Up/Right = +, Down/Left = -)
                ValueScrubber(
                    label = "Zoom",
                    value = curTransform.scale * 100f,
                    displayValue = "${(curTransform.scale * 100).toInt()}%",
                    step = 1f,
                    minValue = 20f,
                    maxValue = 500f,
                    onValueChange = { sheet.slotTransforms[slotIndex] = curTransform.copy(scale = it / 100f) }
                )

                // Rotation Scrubber (Drag Up/Right = +, Down/Left = -)
                ValueScrubber(
                    label = "Rot",
                    value = curTransform.rotation,
                    displayValue = "${curTransform.rotation.toInt()}°",
                    step = 1f,
                    minValue = 0f,
                    maxValue = 360f,
                    onValueChange = {
                        val r = ((it % 360f) + 360f) % 360f
                        sheet.slotTransforms[slotIndex] = curTransform.copy(rotation = r)
                    }
                )

                // Exactly ONE 90° rotate button
                IconButton(
                    onClick = {
                        val newRot = (curTransform.rotation + 90f) % 360f
                        sheet.slotTransforms[slotIndex] = curTransform.copy(rotation = newRot)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Outlined.RotateRight, contentDescription = "+90°", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                // Reset
                IconButton(
                    onClick = { sheet.slotTransforms[slotIndex] = SlotTransform() },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset", tint = Color.LightGray, modifier = Modifier.size(17.dp))
                }

                // Close / Deselect
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            // Row 2: X, Y, Z Scrubbers (click, hold & move) + Center (0,0) alignment button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // X Scrubber (Pan X)
                ValueScrubber(
                    label = "X",
                    value = curTransform.panX * 100f,
                    displayValue = "${(curTransform.panX * 100).toInt()}%",
                    step = 0.5f,
                    minValue = -100f,
                    maxValue = 100f,
                    onValueChange = { sheet.slotTransforms[slotIndex] = curTransform.copy(panX = it / 100f) }
                )

                // Y Scrubber (Pan Y)
                ValueScrubber(
                    label = "Y",
                    value = curTransform.panY * 100f,
                    displayValue = "${(curTransform.panY * 100).toInt()}%",
                    step = 0.5f,
                    minValue = -100f,
                    maxValue = 100f,
                    onValueChange = { sheet.slotTransforms[slotIndex] = curTransform.copy(panY = it / 100f) }
                )

                // Z Scrubber (Layer / Stacking Order)
                ValueScrubber(
                    label = "Z",
                    value = curTransform.zIndex,
                    displayValue = "${curTransform.zIndex.toInt()}",
                    step = 1f,
                    minValue = 0f,
                    maxValue = 50f,
                    onValueChange = { sheet.slotTransforms[slotIndex] = curTransform.copy(zIndex = it.coerceIn(0f, 50f)) }
                )

                // Center alignment button
                IconButton(
                    onClick = { sheet.slotTransforms[slotIndex] = curTransform.copy(panX = 0f, panY = 0f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Center X/Y", tint = ScannerGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Click, hold & drag value scrubber:
 * - Moving UP or RIGHT increases value (+)
 * - Moving DOWN or LEFT decreases value (-)
 * - Live real-time preview
 * - Tap to open direct numerical input
 */
@Composable
private fun ValueScrubber(
    label: String,
    value: Float,
    displayValue: String,
    step: Float = 1f,
    minValue: Float = -Float.MAX_VALUE,
    maxValue: Float = Float.MAX_VALUE,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var showDirectInput by remember { mutableStateOf(false) }
    var directInputText by remember { mutableStateOf("") }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDragging) Color(0xFF26332B) else Color(0xFF282828),
        border = BorderStroke(1.dp, if (isDragging) ScannerGreen else Color(0xFF444444)),
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var accumulatedValue = currentValue
                        var totalMovedX = 0f
                        var totalMovedY = 0f
                        var dragStarted = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                if (dragStarted) {
                                    isDragging = false
                                } else {
                                    // Tap without dragging opens direct manual input
                                    directInputText = "${currentValue.toInt()}"
                                    showDirectInput = true
                                }
                                break
                            }

                            val change = pressed[0].positionChange()
                            totalMovedX += change.x
                            totalMovedY += change.y

                            if (!dragStarted && (kotlin.math.abs(totalMovedX) > 6f || kotlin.math.abs(totalMovedY) > 6f)) {
                                dragStarted = true
                                isDragging = true
                            }

                            if (dragStarted) {
                                // Up = +, Down = -, Right = +, Left = -
                                // In Android screen coordinates: dy < 0 is UP (-dy is +), dx > 0 is RIGHT (dx is +)
                                val netChange = change.x - change.y
                                val delta = (netChange / 4f) * step
                                accumulatedValue = (accumulatedValue + delta).coerceIn(minValue, maxValue)
                                currentOnValueChange(accumulatedValue)
                                pressed[0].consume()
                            }
                        }
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "$label:",
                color = ScannerGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = displayValue,
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showDirectInput) {
        AlertDialog(
            onDismissRequest = { showDirectInput = false },
            title = { Text("Set $label Value", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = directInputText,
                    onValueChange = { directInputText = it },
                    label = { Text("Enter number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        directInputText.toFloatOrNull()?.let { v ->
                            onValueChange(v.coerceIn(minValue, maxValue))
                        }
                        showDirectInput = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectInput = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Renders a single collage sheet card with its page header, white canvas, floating items, and snapping guidelines.
 */
@Composable
private fun CollageSheetCard(
    sheetIndex: Int,
    totalSheets: Int,
    sheetState: CollageSheetState,
    layout: CollageLayout,
    aspectRatio: Float,
    isSheetSelected: Boolean,
    selectedSlotIndex: Int?,
    watermarkConfig: CollageWatermarkConfig,
    onSelectSlot: (Int) -> Unit,
    onDeselect: () -> Unit,
    onDeleteSlot: (Int) -> Unit,
    onDeleteSheet: () -> Unit,
    onTransformChange: (Int, SlotTransform) -> Unit
) {
    var sheetSize by remember { mutableStateOf(IntSize.Zero) }

    // Snapping guidelines local state (isolated to this card so dragging doesn't recompose the entire dialog!)
    var snapGuideVertical by remember { mutableStateOf(false) }
    var snapGuideHorizontal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(0.92f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Page Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Page ${sheetIndex + 1} of $totalSheets",
                color = Color(0xFFCCCCCC),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (totalSheets > 1) {
                IconButton(
                    onClick = onDeleteSheet,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete Page",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // White Canvas Sheet (Pure white background, clean shadow, no rigid boxes)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .onSizeChanged { sheetSize = it }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Layer 0: Tap background to deselect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onDeselect()
                        }
                )

                if (sheetSize.width > 0 && sheetSize.height > 0) {
                    val sw = sheetSize.width.toFloat()
                    val sh = sheetSize.height.toFloat()
                    val slots = CollageGenerator.calculateSlotRects(layout, sheetSize.width, sheetSize.height)
                    val slotCount = layout.maxPages

                    // Render each slot item
                    for (i in 0 until minOf(slotCount, slots.size)) {
                        val initRect = slots[i]
                        val slotW = (initRect.right - initRect.left).toFloat()
                        val slotH = (initRect.bottom - initRect.top).toFloat()
                        val initCx = (initRect.left + initRect.right) / 2f
                        val initCy = (initRect.top + initRect.bottom) / 2f

                        val page = sheetState.slotAssignments[i]
                        val customPath = sheetState.slotCroppedPaths[i]
                        val isSelected = isSheetSelected && selectedSlotIndex == i

                        if (page != null || !customPath.isNullOrBlank()) {
                            PhotoshopCanvasItem(
                                slotIndex = i,
                                page = page,
                                customImagePath = customPath,
                                initCx = initCx,
                                initCy = initCy,
                                slotWidth = slotW,
                                slotHeight = slotH,
                                sheetWidth = sw,
                                sheetHeight = sh,
                                getTransform = { sheetState.slotTransforms[i] ?: SlotTransform() },
                                isSelected = isSelected,
                                onSelect = { onSelectSlot(i) },
                                onTransformChange = { newTrans -> onTransformChange(i, newTrans) },
                                onDelete = { onDeleteSlot(i) },
                                onSnapChange = { snapV, snapH ->
                                    snapGuideVertical = snapV
                                    snapGuideHorizontal = snapH
                                }
                            )
                        } else {
                            // Blank slot placeholder box
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(initRect.left, initRect.top) }
                                    .size(
                                        width = with(LocalDensity.current) { slotW.toDp() },
                                        height = with(LocalDensity.current) { slotH.toDp() }
                                    )
                                    .padding(6.dp)
                                    .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(4.dp))
                                    .clickable { onSelectSlot(i) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Gray, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Add image", color = Color.Gray, fontSize = 10.5.sp)
                                }
                            }
                        }
                    }
                }

                // Magnetic Snapping Guidelines Overlay (Dashed Green Lines matching screenshot)
                if (isSheetSelected && (snapGuideVertical || snapGuideHorizontal)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                        if (snapGuideVertical) {
                            drawLine(
                                color = ScannerGreen,
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }
                        if (snapGuideHorizontal) {
                            drawLine(
                                color = ScannerGreen,
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }
                    }
                }

                // Watermark overlay
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
    }
}

/**
 * Freeform Photoshop-style Canvas Item without rigid boxes.
 * Features:
 * - Border wraps EXACTLY to image size, not slot/column size.
 * - Corner handle simultaneously SCALES and ROTATES image smoothly.
 * - 1-finger move with magnetic snapping to vertical/horizontal center and edges.
 * - Red (X) delete handle on top-left.
 * - Zero flickering: transformations executed in graphicsLayer (Draw Phase) reading getTransform(),
 *   so NO recomposition occurs during motion. Synchronous in-memory bitmap cache ensures zero blank frames.
 */
@Composable
private fun PhotoshopCanvasItem(
    slotIndex: Int,
    page: Page?,
    customImagePath: String?,
    initCx: Float,
    initCy: Float,
    slotWidth: Float,
    slotHeight: Float,
    sheetWidth: Float,
    sheetHeight: Float,
    getTransform: () -> SlotTransform,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTransformChange: (SlotTransform) -> Unit,
    onDelete: () -> Unit,
    onSnapChange: (Boolean, Boolean) -> Unit
) {
    val density = LocalDensity.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)
    val currentOnSnapChange by rememberUpdatedState(onSnapChange)
    val currentOnDelete by rememberUpdatedState(onDelete)

    // Synchronous cached bitmap lookup to eliminate all image loading flickers
    val imagePath = customImagePath ?: page?.processedImagePath
    var cachedBitmap by remember(imagePath) {
        mutableStateOf(if (!imagePath.isNullOrBlank()) CollageBitmapCache.get(imagePath) else null)
    }

    LaunchedEffect(imagePath) {
        if (cachedBitmap == null && !imagePath.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                val bmp = CollageBitmapCache.getOrDecode(imagePath, 1024, 1024)
                withContext(Dispatchers.Main) {
                    cachedBitmap = bmp
                }
            }
        }
    }

    // Calculate exact image dimensions fitted within slot rectangle (FitCenter)
    // Ensures border wraps the actual image, NOT the column/slot box!
    val bmpW = (cachedBitmap?.width ?: slotWidth.toInt()).toFloat().coerceAtLeast(10f)
    val bmpH = (cachedBitmap?.height ?: slotHeight.toInt()).toFloat().coerceAtLeast(10f)
    val fitScale = minOf(slotWidth / bmpW, slotHeight / bmpH)
    val baseWidth = bmpW * fitScale
    val baseHeight = bmpH * fitScale

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (initCx - (baseWidth / 2f)).toInt(),
                    (initCy - (baseHeight / 2f)).toInt()
                )
            }
            .size(
                width = with(density) { baseWidth.toDp() },
                height = with(density) { baseHeight.toDp() }
            )
            .zIndex(getTransform().zIndex)
            .graphicsLayer {
                // Reading getTransform() in Draw Phase -> ZERO recomposition during motion
                val t = getTransform()
                translationX = t.panX * sheetWidth
                translationY = t.panY * sheetHeight
                scaleX = t.scale
                scaleY = t.scale
                rotationZ = t.rotation
            }
    ) {
        // Image content with 1-finger move and 2-finger physical rotate/zoom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slotIndex) {
                    val SNAP_THRESHOLD_PX = 18f

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            currentOnSelect()
                            down.consume()
                            var prevCentroid = down.position

                            val initialTransform = getTransform()
                            var activePanX = initialTransform.panX
                            var activePanY = initialTransform.panY
                            var activeScale = initialTransform.scale
                            var activeRotation = initialTransform.rotation
                            var activeZIndex = initialTransform.zIndex

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    currentOnSnapChange(false, false)
                                    break
                                }

                                val count = pressed.size
                                if (count == 1) {
                                    val currentPos = pressed[0].position
                                    val localDelta = currentPos - prevCentroid
                                    prevCentroid = currentPos

                                    if (localDelta.x != 0f || localDelta.y != 0f) {
                                        val rad = Math.toRadians(activeRotation.toDouble())
                                        val cosA = cos(rad).toFloat()
                                        val sinA = sin(rad).toFloat()
                                        val worldDx = (localDelta.x * cosA - localDelta.y * sinA) * activeScale
                                        val worldDy = (localDelta.x * sinA + localDelta.y * cosA) * activeScale

                                        activePanX += (worldDx / sheetWidth.coerceAtLeast(1f))
                                        activePanY += (worldDy / sheetHeight.coerceAtLeast(1f))

                                        // Magnetic center and edge snapping
                                        val curCenterX = initCx + activePanX * sheetWidth
                                        val curCenterY = initCy + activePanY * sheetHeight
                                        val pageCenterX = sheetWidth / 2f
                                        val pageCenterY = sheetHeight / 2f

                                        var snapV = false
                                        var snapH = false

                                        // Center horizontal snap (vertical dashed guide line down sheet center)
                                        if (kotlin.math.abs(curCenterX - pageCenterX) < SNAP_THRESHOLD_PX) {
                                            activePanX = (pageCenterX - initCx) / sheetWidth
                                            snapV = true
                                        }

                                        // Center vertical snap (horizontal dashed guide line across sheet center)
                                        if (kotlin.math.abs(curCenterY - pageCenterY) < SNAP_THRESHOLD_PX) {
                                            activePanY = (pageCenterY - initCy) / sheetHeight
                                            snapH = true
                                        }

                                        // Page boundary snapping
                                        val halfW = (baseWidth * activeScale) / 2f
                                        val halfH = (baseHeight * activeScale) / 2f
                                        if (kotlin.math.abs(curCenterX - halfW) < SNAP_THRESHOLD_PX) {
                                            activePanX = (halfW - initCx) / sheetWidth
                                        }
                                        if (kotlin.math.abs((curCenterX + halfW) - sheetWidth) < SNAP_THRESHOLD_PX) {
                                            activePanX = (sheetWidth - halfW - initCx) / sheetWidth
                                        }
                                        if (kotlin.math.abs(curCenterY - halfH) < SNAP_THRESHOLD_PX) {
                                            activePanY = (halfH - initCy) / sheetHeight
                                        }
                                        if (kotlin.math.abs((curCenterY + halfH) - sheetHeight) < SNAP_THRESHOLD_PX) {
                                            activePanY = (sheetHeight - halfH - initCy) / sheetHeight
                                        }

                                        currentOnSnapChange(snapV, snapH)
                                        currentOnTransformChange(
                                            SlotTransform(
                                                scale = activeScale,
                                                panX = activePanX,
                                                panY = activePanY,
                                                rotation = activeRotation,
                                                zIndex = activeZIndex
                                            )
                                        )
                                    }
                                    pressed[0].consume()
                                } else if (count >= 2) {
                                    val p0 = pressed[0].position
                                    val p1 = pressed[1].position
                                    val centroid = (p0 + p1) / 2f
                                    val localPan = centroid - prevCentroid
                                    prevCentroid = centroid

                                    val zoomChange = event.calculateZoom()
                                    val rotationChange = event.calculateRotation()

                                    val rad = Math.toRadians(activeRotation.toDouble())
                                    val cosA = cos(rad).toFloat()
                                    val sinA = sin(rad).toFloat()
                                    val worldDx = (localPan.x * cosA - localPan.y * sinA) * activeScale
                                    val worldDy = (localPan.x * sinA + localPan.y * cosA) * activeScale

                                    activeScale = (activeScale * zoomChange).coerceIn(0.2f, 5.0f)
                                    activeRotation = (activeRotation + rotationChange) % 360f
                                    activePanX += (worldDx / sheetWidth.coerceAtLeast(1f))
                                    activePanY += (worldDy / sheetHeight.coerceAtLeast(1f))

                                    val curCenterX = initCx + activePanX * sheetWidth
                                    val curCenterY = initCy + activePanY * sheetHeight
                                    val pageCenterX = sheetWidth / 2f
                                    val pageCenterY = sheetHeight / 2f

                                    var snapV = false
                                    var snapH = false

                                    if (kotlin.math.abs(curCenterX - pageCenterX) < SNAP_THRESHOLD_PX) {
                                        activePanX = (pageCenterX - initCx) / sheetWidth
                                        snapV = true
                                    }
                                    if (kotlin.math.abs(curCenterY - pageCenterY) < SNAP_THRESHOLD_PX) {
                                        activePanY = (pageCenterY - initCy) / sheetHeight
                                        snapH = true
                                    }

                                    currentOnSnapChange(snapV, snapH)
                                    currentOnTransformChange(
                                        SlotTransform(
                                            scale = activeScale,
                                            panX = activePanX,
                                            panY = activePanY,
                                            rotation = activeRotation,
                                            zIndex = activeZIndex
                                        )
                                    )
                                    pressed.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
        ) {
            if (cachedBitmap != null) {
                Image(
                    bitmap = cachedBitmap!!.asImageBitmap(),
                    contentDescription = "Image ${slotIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (page != null) {
                AsyncImage(
                    model = File(imagePath.orEmpty()),
                    contentDescription = "Image ${slotIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Selection Border & Corner Handles (Matching exact screenshot & image dimensions)
        if (isSelected) {
            // Border wraps the EXACT image boundaries
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, ScannerGreen, RoundedCornerShape(2.dp))
            )

            // Top-Left: Red circular close/delete button
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-11).dp, y = (-11).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable { currentOnDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Bottom-Right: Green circular handle that SCALES and ROTATES simultaneously
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 11.dp, y = 11.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ScannerGreen)
                    .pointerInput(slotIndex) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()

                                val startTransform = getTransform()
                                val startScale = startTransform.scale
                                val startRotation = startTransform.rotation
                                val baseRadius = kotlin.math.hypot(baseWidth / 2f, baseHeight / 2f).coerceAtLeast(1f)
                                val baseAngleDeg = Math.toDegrees(kotlin.math.atan2(baseHeight / 2f, baseWidth / 2f).toDouble()).toFloat()

                                var totalDx = 0f
                                var totalDy = 0f

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break

                                    val dragChange = pressed[0].positionChange()
                                    totalDx += dragChange.x
                                    totalDy += dragChange.y

                                    // Vector from center to initial corner in rotated sheet space
                                    val rad = Math.toRadians((startRotation + baseAngleDeg).toDouble())
                                    val initialCornerX = kotlin.math.cos(rad).toFloat() * (baseRadius * startScale)
                                    val initialCornerY = kotlin.math.sin(rad).toFloat() * (baseRadius * startScale)

                                    // New corner position relative to image center
                                    val newCornerX = initialCornerX + totalDx
                                    val newCornerY = initialCornerY + totalDy

                                    // 1. Proportional scale based on distance from center
                                    val newRadius = kotlin.math.hypot(newCornerX, newCornerY)
                                    val newScale = (newRadius / baseRadius).coerceIn(0.2f, 5.0f)

                                    // 2. Physical rotation based on angle from center
                                    val currentAngleDeg = Math.toDegrees(kotlin.math.atan2(newCornerY, newCornerX).toDouble()).toFloat()
                                    val deltaAngle = currentAngleDeg - (startRotation + baseAngleDeg)
                                    val newRotation = (startRotation + deltaAngle) % 360f

                                    currentOnTransformChange(
                                        startTransform.copy(
                                            scale = newScale,
                                            rotation = (newRotation + 360f) % 360f
                                        )
                                    )
                                    pressed[0].consume()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.OpenInFull,
                    contentDescription = "Resize and Rotate",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
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
                    modifier = Modifier.fillMaxWidth().weight(1f)) {}
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth().weight(1f)) {}
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
