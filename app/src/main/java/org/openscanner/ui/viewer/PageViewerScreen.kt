package org.openscanner.ui.viewer

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.data.model.Document
import org.openscanner.data.model.Page
import org.openscanner.ui.crop.CropScreen
import org.openscanner.ui.editor.UniversalImageEditStudio
import org.openscanner.ui.theme.ScannerGreen
import org.openscanner.ui.viewer.components.ViewerBottomBar
import org.openscanner.ui.viewer.dialogs.SignatureWatermarkSheet
import org.openscanner.ui.viewer.dialogs.ViewerMoreMenuSheet
import org.openscanner.ui.viewer.dialogs.ViewerShareSheet
import java.io.File

/**
 * Universal High-performance Document Page Viewer with Butter-Smooth Horizontal Swiping & Zoom (like OKEN Scanner).
 *
 * Capabilities:
 * - 100% Free horizontal swiping between all document pages with 16dp spacing.
 * - True 2-Finger Pinch-to-Zoom (from 1x up to 5x) without blocking 1-finger horizontal swipes!
 * - Double-tap to zoom (toggle 1x <-> 2.5x).
 * - 1-Finger Pan when zoomed in, with boundary bounds clamping.
 * - Floating Zoom HUD: Reset Pill ("2.5x | Reset") + Floating Quick Zoom Buttons (+ / -).
 * - Animated navigation chevrons on screen edges.
 * - Reuses unified [CropScreen] and [UniversalImageEditStudio] across the entire app.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PageViewerScreen(
    pageId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var document by remember { mutableStateOf<Document?>(null) }
    var docTitle by remember { mutableStateOf("Document") }
    var initialIndex by remember { mutableIntStateOf(0) }
    var isLoaded by remember { mutableStateOf(false) }
    var imageReloadKey by remember { mutableIntStateOf(0) }

    // Dialog sheets state
    var showCropScreen by remember { mutableStateOf(false) }
    var showImageEditStudio by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showSignatureWatermarkSheet by remember { mutableStateOf(false) }
    var showMoreMenuSheet by remember { mutableStateOf(false) }

    // Initial data load
    LaunchedEffect(pageId) {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext as OpenScannerApp
            val p = app.database.pageDao().getPageById(pageId)
            if (p != null) {
                val doc = app.database.documentDao().getDocumentById(p.documentId)
                val allPages = app.database.pageDao().getPagesListForDocument(p.documentId)
                val idx = allPages.indexOfFirst { it.id == pageId }.coerceAtLeast(0)
                document = doc
                docTitle = doc?.title ?: "Document"
                pages = allPages
                initialIndex = idx
                isLoaded = true
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Page not found", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
            }
        }
    }

    if (!isLoaded || pages.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = ScannerGreen)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    ) {
        pages.size
    }

    // Scroll directly to target page index when loaded
    LaunchedEffect(isLoaded, initialIndex) {
        if (isLoaded && initialIndex in pages.indices && pagerState.currentPage != initialIndex) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    // Current active page
    val activePageIndex = pagerState.currentPage.coerceIn(0, pages.size - 1)
    val activePage = pages[activePageIndex]

    // Zoom and pan state for active page
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewWidth by remember { mutableFloatStateOf(1f) }
    var viewHeight by remember { mutableFloatStateOf(1f) }

    // Reset zoom when switching pages
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    fun clampOffset(proposed: Offset, s: Float, vW: Float, vH: Float): Offset {
        if (s <= 1.02f) return Offset.Zero
        val maxOffsetX = ((vW * (s - 1f)) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((vH * (s - 1f)) / 2f).coerceAtLeast(0f)
        return Offset(
            x = proposed.x.coerceIn(-maxOffsetX, maxOffsetX),
            y = proposed.y.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    // Refresh pages list after edit, crop, or delete
    fun refreshPages() {
        coroutineScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as OpenScannerApp
            val updated = app.database.pageDao().getPagesListForDocument(activePage.documentId)
            withContext(Dispatchers.Main) {
                if (updated.isEmpty()) {
                    onNavigateBack()
                } else {
                    pages = updated
                    imageReloadKey++
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = docTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Top-right Page Counter Badge (e.g. "1/19", "5/50")
                    Surface(
                        color = Color(0xFF262626),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${pages.size}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF101010))
            )
        },
        bottomBar = {
            ViewerBottomBar(
                onCropClick = { showCropScreen = true },
                onEditClick = { showImageEditStudio = true },
                onShareClick = { showShareSheet = true },
                onSignatureWatermarkClick = { showSignatureWatermarkSheet = true },
                onMoreClick = { showMoreMenuSheet = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .onGloballyPositioned { coords ->
                    viewWidth = coords.size.width.toFloat()
                    viewHeight = coords.size.height.toFloat()
                },
            contentAlignment = Alignment.Center
        ) {
            // Horizontal Pager for buttery smooth swiping between pages
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = scale <= 1.05f,
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxSize()
            ) { pageIdx ->
                val p = pages[pageIdx]
                val isActivePage = pageIdx == pagerState.currentPage

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 1. Double tap to toggle zoom 1x <-> 2.5x
                        .pointerInput(isActivePage, scale) {
                            if (!isActivePage) return@pointerInput
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                        offset = Offset.Zero
                                    }
                                }
                            )
                        }
                        // 2. Multi-touch 2-Finger Pinch Zoom + 1-Finger Pan when zoomed in
                        .pointerInput(isActivePage, scale) {
                            if (!isActivePage) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedPointers = event.changes.filter { it.pressed }

                                    if (pressedPointers.size >= 2) {
                                        // 2-Finger Pinch-to-Zoom
                                        val p1 = pressedPointers[0].position
                                        val p2 = pressedPointers[1].position
                                        val prevP1 = pressedPointers[0].previousPosition
                                        val prevP2 = pressedPointers[1].previousPosition

                                        val currentDist = (p1 - p2).getDistance()
                                        val prevDist = (prevP1 - prevP2).getDistance()

                                        if (prevDist > 0f) {
                                            val factor = currentDist / prevDist
                                            val newScale = (scale * factor).coerceIn(1f, 5f)
                                            scale = newScale
                                            if (newScale <= 1.05f) {
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                val center = (p1 + p2) / 2f
                                                val prevCenter = (prevP1 + prevP2) / 2f
                                                offset = clampOffset(offset + (center - prevCenter), newScale, viewWidth, viewHeight)
                                            }
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (pressedPointers.size == 1 && scale > 1.05f) {
                                        // 1-Finger Pan while zoomed in
                                        val change = pressedPointers[0]
                                        val delta = change.position - change.previousPosition
                                        offset = clampOffset(offset + delta, scale, viewWidth, viewHeight)
                                        change.consume()
                                    }
                                    // When 1 pointer and scale <= 1.05f: event is unconsumed so HorizontalPager smoothly scrolls!
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    key(p.id, imageReloadKey) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(p.processedImagePath))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Page ${p.pageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = if (isActivePage) scale else 1f,
                                    scaleY = if (isActivePage) scale else 1f,
                                    translationX = if (isActivePage) offset.x else 0f,
                                    translationY = if (isActivePage) offset.y else 0f
                                )
                        )
                    }
                }
            }

            // Left Navigation Chevron Arrow (shown when not on first page and not zoomed in)
            if (pagerState.currentPage > 0 && scale <= 1.05f) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(42.dp)
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Page",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Right Navigation Chevron Arrow (shown when not on last page and not zoomed in)
            if (pagerState.currentPage < pages.size - 1 && scale <= 1.05f) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(42.dp)
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Page",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Floating Reset Zoom Pill (appears when user is zoomed in)
            if (scale > 1.05f) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, ScannerGreen),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clickable {
                            scale = 1f
                            offset = Offset.Zero
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${(scale * 10).toInt() / 10f}x",
                            color = ScannerGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Floating Quick Zoom Action Buttons (+ and -) on the right side
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Zoom In (+) Button
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    border = BorderStroke(0.8.dp, Color(0xFF444444)),
                    modifier = Modifier
                        .size(38.dp)
                        .clickable {
                            val newScale = (scale * 1.35f).coerceIn(1f, 5f)
                            scale = newScale
                            offset = clampOffset(offset, newScale, viewWidth, viewHeight)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Zoom Out (-) Button
                if (scale > 1.05f) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        border = BorderStroke(0.8.dp, Color(0xFF444444)),
                        modifier = Modifier
                            .size(38.dp)
                            .clickable {
                                val newScale = (scale / 1.35f).coerceIn(1f, 5f)
                                if (newScale <= 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = newScale
                                    offset = clampOffset(offset, newScale, viewWidth, viewHeight)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom Out",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. Unified Universal Crop Screen (Single Source of Truth for cropping across the app)
    if (showCropScreen) {
        Dialog(
            onDismissRequest = { showCropScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            CropScreen(
                page = activePage,
                onClose = { showCropScreen = false },
                onPageUpdated = {
                    showCropScreen = false
                    refreshPages()
                }
            )
        }
    }

    // 2. Universal Image Edit Studio (Single Source of Truth for drawing, highlighter, smudge, text, redact, tune)
    if (showImageEditStudio) {
        UniversalImageEditStudio(
            page = activePage,
            onDismiss = { showImageEditStudio = false },
            onPageUpdated = { refreshPages() }
        )
    }

    // 3. Center Share Sheet (Shares currently open image as JPG, PDF, or text)
    if (showShareSheet) {
        ViewerShareSheet(
            page = activePage,
            docTitle = docTitle,
            onDismiss = { showShareSheet = false }
        )
    }

    // 4. Signature & Watermark Sheet (Draw/stamp signature, custom watermark)
    if (showSignatureWatermarkSheet) {
        SignatureWatermarkSheet(
            page = activePage,
            onDismiss = { showSignatureWatermarkSheet = false },
            onPageUpdated = { refreshPages() }
        )
    }

    // 5. 3-Dot More Sheet (Save to gallery, Retake, Edit page name, Delete)
    if (showMoreMenuSheet) {
        ViewerMoreMenuSheet(
            page = activePage,
            docTitle = docTitle,
            onDismiss = { showMoreMenuSheet = false },
            onPageUpdated = { refreshPages() },
            onPageDeleted = { refreshPages() },
            onTitleUpdated = { newTitle -> docTitle = newTitle }
        )
    }
}
