package org.openscanner.ui.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.camera.BatchWarpProcessor
import org.openscanner.cv.DocumentQuadrilateral
import androidx.compose.foundation.ExperimentalFoundationApi
import org.openscanner.data.model.EnhanceMode
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.ui.theme.ScannerGreen

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

    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0 to batchQueue.size) }
    var selectedFilter by remember { mutableStateOf(EnhanceMode.MAGIC_COLOR) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val currentItem = batchQueue.getOrNull(pagerState.currentPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Batch Review (${batchQueue.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
            ) {
                // Filmstrip indicator & quick jump
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(batchQueue) { idx, item ->
                        val isSelected = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 58.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ScannerGreen else Color.DarkGray)
                                .padding(if (isSelected) 2.dp else 1.dp)
                                .clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                                }
                        ) {
                            Image(
                                bitmap = item.thumbnailBitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(5.dp))
                            )
                        }
                    }
                }

                // Action buttons toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReviewActionButton(
                        icon = Icons.AutoMirrored.Filled.RotateLeft,
                        label = "Left",
                        onClick = {
                            currentItem?.let { onRotateItem(it.id, false) }
                        }
                    )

                    ReviewActionButton(
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        label = "Right",
                        onClick = {
                            currentItem?.let { onRotateItem(it.id, true) }
                        }
                    )

                    ReviewActionButton(
                        icon = Icons.Outlined.AutoFixHigh,
                        label = selectedFilter.displayName,
                        onClick = { showFilterMenu = true }
                    )

                    ReviewActionButton(
                        icon = Icons.Outlined.Delete,
                        label = "Delete",
                        onClick = {
                            currentItem?.let {
                                onRemoveItem(it.id)
                                if (batchQueue.size <= 1) {
                                    onClose()
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val item = batchQueue.getOrNull(pageIndex)
                if (item != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = item.thumbnailBitmap.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Filter Dropdown
            DropdownMenu(
                expanded = showFilterMenu,
                onDismissRequest = { showFilterMenu = false }
            ) {
                listOf(EnhanceMode.ORIGINAL, EnhanceMode.MAGIC_COLOR, EnhanceMode.BW, EnhanceMode.GRAYSCALE).forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayName) },
                        onClick = {
                            selectedFilter = mode
                            showFilterMenu = false
                        }
                    )
                }
            }

            // Progress Dialog during deferred warp pipeline execution
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
    }
}

@Composable
fun ReviewActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.LightGray, fontSize = 11.sp)
    }
}
