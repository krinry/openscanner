package org.openscanner.ui.viewer

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.data.model.Document
import org.openscanner.data.model.Page
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.ui.theme.ScannerGreen
import org.openscanner.ui.viewer.components.ViewerBottomBar
import org.openscanner.ui.viewer.dialogs.CropScanToolsSheet
import org.openscanner.ui.viewer.dialogs.ImageEditStudioSheet
import org.openscanner.ui.viewer.dialogs.SignatureWatermarkSheet
import org.openscanner.ui.viewer.dialogs.ViewerMoreMenuSheet
import org.openscanner.ui.viewer.dialogs.ViewerShareSheet
import java.io.File

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
    var showCropFilterSheet by remember { mutableStateOf(false) }
    var showImageEditSheet by remember { mutableStateOf(false) }
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

    // Current active page
    val activePageIndex = pagerState.currentPage.coerceIn(0, pages.size - 1)
    val activePage = pages[activePageIndex]

    // Zoom and pan state for active page
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom when switching pages
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        offset = if (newScale > 1.05f) offset + offsetChange else Offset.Zero
    }

    // Refresh pages list after edit, retake, or delete
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
                onCropClick = { showCropFilterSheet = true },
                onEditClick = { showImageEditSheet = true },
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
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Horizontal Pager for swiping between pages
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = scale <= 1.05f,
                modifier = Modifier.fillMaxSize()
            ) { pageIdx ->
                val p = pages[pageIdx]
                val isActivePage = pageIdx == pagerState.currentPage

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = if (scale > 1.2f) 1f else 2.5f
                                    offset = Offset.Zero
                                }
                            )
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
                                .then(
                                    if (isActivePage) {
                                        Modifier.transformable(state = transformState)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }
    }

    // 1. Crop & Scan Tools Sheet (Crop, Perspective, Loupe, Filters, Brightness/Contrast)
    if (showCropFilterSheet) {
        CropScanToolsSheet(
            page = activePage,
            onDismiss = { showCropFilterSheet = false },
            onPageUpdated = { refreshPages() }
        )
    }

    // 2. Image Edit Studio Sheet (Draw, Highlighter, Text, Redact/Mosaic, Tune)
    if (showImageEditSheet) {
        ImageEditStudioSheet(
            page = activePage,
            onDismiss = { showImageEditSheet = false },
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
