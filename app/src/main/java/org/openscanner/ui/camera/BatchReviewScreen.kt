package org.openscanner.ui.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.camera.BatchWarpProcessor
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.data.model.EnhanceMode
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.ui.theme.ScannerGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BatchReviewScreen(
    docId: Long,
    batchQueue: List<BatchCaptureItem>,
    onRotateItem: (id: Long, clockwise: Boolean) -> Unit,
    onRemoveItem: (id: Long) -> Unit,
    onUpdateCropQuad: (id: Long, quad: DocumentQuadrilateral) -> Unit,
    onClose: () -> Unit,
    onBatchSaved: (savedDocId: Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { batchQueue.size })

    val defaultTitle = remember {
        "New Doc " + SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date())
    }
    var documentTitle by remember { mutableStateOf(defaultTitle) }
    var showEditTitleDialog by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0 to batchQueue.size) }

    var selectedFilter by remember { mutableStateOf(EnhanceMode.NO_SHADOW) }
    var applyToAllPages by remember { mutableStateOf(true) }

    // Per-page filter override if "applyToAllPages" is false
    var pageFilters by remember { mutableStateOf(mapOf<Long, EnhanceMode>()) }

    var cropEditingItem by remember { mutableStateOf<BatchCaptureItem?>(null) }

    val currentItem = batchQueue.getOrNull(pagerState.currentPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEditTitleDialog = true }
                    ) {
                        Text(
                            text = documentTitle,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit Title",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (!isProcessing && batchQueue.isNotEmpty()) {
                                isProcessing = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val app = context.applicationContext as OpenScannerApp
                                    val repo = ScannerRepository(
                                        context = context,
                                        documentDao = app.database.documentDao(),
                                        pageDao = app.database.pageDao(),
                                        folderDao = app.database.folderDao()
                                    )
                                    val processor = BatchWarpProcessor(context, repo)
                                    val savedId = processor.processBatch(
                                        targetDocId = docId,
                                        items = batchQueue,
                                        selectedEnhanceMode = selectedFilter,
                                        customTitle = documentTitle,
                                        onProgress = { done, total ->
                                            processingProgress = done to total
                                        }
                                    )
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        onBatchSaved(savedId)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E0E0E))
            ) {
                // 1. "Apply to all pages" Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apply to all pages",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(
                        selected = applyToAllPages,
                        onClick = { applyToAllPages = !applyToAllPages },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ScannerGreen,
                            unselectedColor = Color.Gray
                        )
                    )
                }

                // 2. Horizontal Filter Thumbnails Strip
                val filterModes = listOf(
                    EnhanceMode.NO_SHADOW to "Beta",
                    EnhanceMode.ORIGINAL to null,
                    EnhanceMode.LIGHTEN to null,
                    EnhanceMode.MAGIC_COLOR to null,
                    EnhanceMode.GRAYSCALE to null,
                    EnhanceMode.BW to null
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(filterModes) { (mode, badge) ->
                        val currentEffectiveFilter = if (applyToAllPages) {
                            selectedFilter
                        } else {
                            currentItem?.let { pageFilters[it.id] } ?: selectedFilter
                        }
                        val isSelected = currentEffectiveFilter == mode

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    if (applyToAllPages) {
                                        selectedFilter = mode
                                    } else {
                                        currentItem?.let { item ->
                                            pageFilters = pageFilters + (item.id to mode)
                                        }
                                    }
                                }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 72.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF242424))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.5.dp,
                                        color = if (isSelected) ScannerGreen else Color(0xFF3A3A3A),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            ) {
                                // Miniature Page Preview with Filter Effect
                                if (currentItem != null) {
                                    Image(
                                        bitmap = currentItem.thumbnailBitmap.asImageBitmap(),
                                        contentDescription = mode.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Beta Badge on Top Right
                                if (badge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(bottomStart = 4.dp),
                                        color = Color(0xFFFF5722),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Text(
                                            text = badge,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                // Dark overlay for filter label at bottom of thumbnail
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        color = if (isSelected) ScannerGreen else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)

                // 3. Bottom Actions Toolbar (Retake, Add, Left, Crop)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BatchActionButton(
                        icon = Icons.Outlined.Cameraswitch,
                        label = "Retake",
                        onClick = {
                            currentItem?.let { item ->
                                onRemoveItem(item.id)
                                onClose() // Return to camera to re-shoot this frame
                            }
                        }
                    )

                    BatchActionButton(
                        icon = Icons.Outlined.AddBox,
                        label = "Add",
                        onClick = {
                            onClose() // Return to camera to capture more batch items
                        }
                    )

                    BatchActionButton(
                        icon = Icons.AutoMirrored.Filled.RotateLeft,
                        label = "Left",
                        onClick = {
                            currentItem?.let { onRotateItem(it.id, false) }
                        }
                    )

                    BatchActionButton(
                        icon = Icons.Outlined.Crop,
                        label = "Crop",
                        onClick = {
                            cropEditingItem = currentItem
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF161616)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Document Page Viewport with Page Peeking
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 36.dp),
                    pageSpacing = 16.dp
                ) { pageIndex ->
                    val item = batchQueue.getOrNull(pageIndex)
                    if (item != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(0.707f) // Standard A4 Paper Ratio
                                .clip(RoundedCornerShape(6.dp)),
                            color = Color.White,
                            shadowElevation = 8.dp
                        ) {
                            Image(
                                bitmap = item.thumbnailBitmap.asImageBitmap(),
                                contentDescription = "Page ${pageIndex + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Page Indicator with Prev / Next Arrow Steppers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Previous Page",
                        tint = if (pagerState.currentPage > 0) Color.White else Color(0xFF444444),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0x66000000),
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${batchQueue.size}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (pagerState.currentPage < batchQueue.size - 1) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    enabled = pagerState.currentPage < batchQueue.size - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Next Page",
                        tint = if (pagerState.currentPage < batchQueue.size - 1) Color.White else Color(0xFF444444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Edit Document Title Dialog
    if (showEditTitleDialog) {
        var tempTitle by remember { mutableStateOf(documentTitle) }
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text("Edit Document Name", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempTitle,
                    onValueChange = { tempTitle = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ScannerGreen,
                        unfocusedBorderColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        documentTitle = tempTitle.trim().ifEmpty { defaultTitle }
                        showEditTitleDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("OK", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // Modal Crop Screen for Batch Item
    cropEditingItem?.let { item ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            org.openscanner.ui.crop.CropScreen(
                imagePath = item.rawFilePath,
                docId = docId,
                initialQuad = item.cropQuad,
                rotationDegrees = item.rotationDegrees,
                onCropConfirmed = { newQuad ->
                    onUpdateCropQuad(item.id, newQuad)
                    cropEditingItem = null
                },
                onClose = { cropEditingItem = null }
            )
        }
    }

    // Progress Dialog during batch processing
    if (isProcessing) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E1E),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Processing Batch...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val progressFrac = if (processingProgress.second > 0) {
                        processingProgress.first.toFloat() / processingProgress.second.toFloat()
                    } else 0f
                    LinearProgressIndicator(
                        progress = { progressFrac },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = ScannerGreen,
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Page ${processingProgress.first} of ${processingProgress.second}",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BatchActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
