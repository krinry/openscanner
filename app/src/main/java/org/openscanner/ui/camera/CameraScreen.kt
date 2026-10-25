package org.openscanner.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.camera.CaptureMode
import org.openscanner.camera.DocumentAnalyzer
import org.openscanner.cv.BookSpineDetector
import org.openscanner.cv.IdCardCropper
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.ui.theme.ScannerGreen
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    docId: Long,
    initialMode: String = "Document",
    onPhotoCaptured: (imagePath: String, docId: Long) -> Unit,
    onClose: () -> Unit,
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                try {
                    val targetFile = File(context.cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (viewModel.currentMode.value == CaptureMode.DOCUMENT) {
                        viewModel.addDocumentItemFromFile(targetFile.absolutePath)
                    } else {
                        onPhotoCaptured(targetFile.absolutePath, docId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
        val matchMode = CaptureMode.entries.firstOrNull { it.title.equals(initialMode, ignoreCase = true) }
        if (matchMode != null) {
            viewModel.setMode(matchMode)
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Camera permission is required to scan documents.", color = Color.White)
        }
        return
    }

    val currentMode by viewModel.currentMode.collectAsState()
    val detectedQuad by viewModel.detectedQuad.collectAsState()
    val previewDimens by viewModel.previewDimens.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val batchCount by viewModel.batchCount.collectAsState()
    val batchQueue by viewModel.batchQueue.collectAsState()

    var showGrid by remember { mutableStateOf(false) }
    var autoCropEnabled by remember { mutableStateOf(true) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var idCardFrontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showBatchReview by remember { mutableStateOf(false) }
    var viewLayoutSize by remember { mutableStateOf(IntSize.Zero) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val handleBack = {
        if (currentMode == CaptureMode.ID_CARD && idCardFrontBitmap != null) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Front Side?", fontWeight = FontWeight.Bold) },
            text = { Text("Discard front side and exit?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        idCardFrontBitmap?.recycle()
                        idCardFrontBitmap = null
                        onClose()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val flashIcon = when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
        else -> Icons.Default.FlashOff
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewLayoutSize = it }
    ) {
        // CameraX Live Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(flashMode)
                        .build()
                    imageCapture = capture

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        DocumentAnalyzer { quad, w, h ->
                            if (autoCropEnabled) {
                                viewModel.onDocumentDetected(quad, w, h)
                            }
                        }
                    )

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture,
                            analysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Viewfinder overlay with dynamic guides (ID Card, Book spine/badges, QR, Quad)
        ViewfinderOverlay(
            mode = currentMode,
            quad = if (autoCropEnabled) detectedQuad else null,
            previewWidth = previewDimens.first,
            previewHeight = previewDimens.second,
            showGrid = showGrid,
            idCardStep = if (idCardFrontBitmap == null) 1 else 2
        )

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = handleBack) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Auto Crop Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (autoCropEnabled) ScannerGreen.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { autoCropEnabled = !autoCropEnabled }
                ) {
                    Text(
                        text = if (autoCropEnabled) "Auto-Crop: ON" else "Auto-Crop: OFF",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // Grid Toggle
                IconButton(onClick = { showGrid = !showGrid }) {
                    Icon(
                        if (showGrid) Icons.Filled.GridOn else Icons.Outlined.GridOff,
                        contentDescription = "Grid",
                        tint = if (showGrid) ScannerGreen else Color.White
                    )
                }

                // Flash Icon
                IconButton(onClick = { viewModel.toggleFlash() }) {
                    Icon(
                        flashIcon,
                        contentDescription = "Flash",
                        modifier = Modifier.size(24.dp),
                        tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) ScannerGreen else Color.White
                    )
                }
            }
        }

        // Bottom Controls: Modes Carousel + Filmstrip + Camera Shutter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(bottom = 36.dp, top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Document Mode Filmstrip Queue
            if (currentMode == CaptureMode.DOCUMENT && batchQueue.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${batchQueue.size} pages captured",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap to review",
                            color = ScannerGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { showBatchReview = true }
                        )
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(batchQueue, key = { _, item -> item.id }) { index, item ->
                            Box(
                                modifier = Modifier
                                    .size(width = 62.dp, height = 82.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .clickable { showBatchReview = true }
                            ) {
                                Image(
                                    bitmap = item.thumbnailBitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(3.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .clickable { viewModel.removeBatchItem(item.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove page",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mode Carousel
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(CaptureMode.entries) { mode ->
                    val isSelected = mode == currentMode
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .clickable { viewModel.setMode(mode) }
                    ) {
                        Text(
                            text = mode.title.uppercase(),
                            color = if (isSelected) ScannerGreen else Color.LightGray,
                            fontSize = if (isSelected) 14.sp else 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ScannerGreen)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }
            }

            // Shutter Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Import from Gallery
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Camera Shutter Button with Badge
                Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(ScannerGreen)
                            .clickable {
                                imageCapture?.let { capture ->
                                    when (currentMode) {
                                        CaptureMode.DOCUMENT -> {
                                            viewModel.captureBatchPhoto(
                                                imageCapture = capture,
                                                onSuccess = { /* Document item added */ },
                                                onError = { Toast.makeText(context, "Document capture failed", Toast.LENGTH_SHORT).show() }
                                            )
                                        }
                                        CaptureMode.ID_CARD -> {
                                            viewModel.capturePhoto(
                                                imageCapture = capture,
                                                onPhotoCaptured = { savedPath ->
                                                    coroutineScope.launch(Dispatchers.Default) {
                                                        val rawBitmap = BitmapFactory.decodeFile(savedPath)
                                                        if (rawBitmap != null) {
                                                            val cropW = if (viewLayoutSize.width > 0) viewLayoutSize.width else rawBitmap.width
                                                            val cropH = if (viewLayoutSize.height > 0) viewLayoutSize.height else rawBitmap.height
                                                            val croppedCard = IdCardCropper.cropCard(rawBitmap, cropW, cropH)
                                                            if (rawBitmap != croppedCard && !rawBitmap.isRecycled) {
                                                                rawBitmap.recycle()
                                                            }
                                                            try {
                                                                File(savedPath).delete()
                                                            } catch (e: Exception) { /* Ignore */ }

                                                            withContext(Dispatchers.Main) {
                                                                if (idCardFrontBitmap == null) {
                                                                    idCardFrontBitmap = croppedCard
                                                                    Toast.makeText(context, "Front side saved! Now capture Back side.", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    val front = idCardFrontBitmap!!
                                                                    val back = croppedCard
                                                                    coroutineScope.launch(Dispatchers.IO) {
                                                                        val app = context.applicationContext as OpenScannerApp
                                                                        val repo = ScannerRepository(
                                                                            context = context,
                                                                            documentDao = app.database.documentDao(),
                                                                            pageDao = app.database.pageDao(),
                                                                            folderDao = app.database.folderDao()
                                                                        )
                                                                        val targetDocId = if (docId > 0) docId else null
                                                                        repo.stitchIdCardPages(front, back, targetDocId)
                                                                        if (!front.isRecycled) front.recycle()
                                                                        if (!back.isRecycled) back.recycle()
                                                                        withContext(Dispatchers.Main) {
                                                                            idCardFrontBitmap = null
                                                                            Toast.makeText(context, "ID Card 2-sided scan created!", Toast.LENGTH_LONG).show()
                                                                            onClose()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                onError = { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() }
                                            )
                                        }
                                        CaptureMode.BOOK -> {
                                            viewModel.capturePhoto(
                                                imageCapture = capture,
                                                onPhotoCaptured = { savedPath ->
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val bmp = BitmapFactory.decodeFile(savedPath)
                                                        if (bmp != null) {
                                                            val spineX = BookSpineDetector.detectSpine(bmp)
                                                            val app = context.applicationContext as OpenScannerApp
                                                            val repo = ScannerRepository(
                                                                context = context,
                                                                documentDao = app.database.documentDao(),
                                                                pageDao = app.database.pageDao(),
                                                                folderDao = app.database.folderDao()
                                                            )
                                                            val targetDocId = if (docId > 0) docId else null
                                                            repo.splitAndSaveBookPages(bmp, spineX, targetDocId)
                                                            if (!bmp.isRecycled) bmp.recycle()
                                                            try {
                                                                File(savedPath).delete()
                                                            } catch (e: Exception) { /* Ignore */ }
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Book pages split & saved!", Toast.LENGTH_SHORT).show()
                                                                onClose()
                                                            }
                                                        }
                                                    }
                                                },
                                                onError = { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() }
                                            )
                                        }
                                        else -> {
                                            viewModel.capturePhoto(
                                                imageCapture = capture,
                                                onPhotoCaptured = { savedPath ->
                                                    onPhotoCaptured(savedPath, docId)
                                                },
                                                onError = { /* Log error */ }
                                            )
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Capture",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Shutter badge for Document mode
                    if (currentMode == CaptureMode.DOCUMENT && batchCount > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp),
                            shape = CircleShape,
                            color = ScannerGreen,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = batchCount.toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Right slot: Review action (DOCUMENT), Front thumbnail (ID_CARD Step 2), or Spacer
                if (currentMode == CaptureMode.DOCUMENT && batchCount > 0) {
                    Surface(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable { showBatchReview = true },
                        color = ScannerGreen,
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Review Document",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "$batchCount",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else if (currentMode == CaptureMode.ID_CARD && idCardFrontBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(2.dp, ScannerGreen, RoundedCornerShape(6.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Image(
                            bitmap = idCardFrontBitmap!!.asImageBitmap(),
                            contentDescription = "Front ID Card",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = ScannerGreen.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "FRONT",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(52.dp))
                }
            }
        }

        // Full-page Batch Review Screen Overlay
        if (showBatchReview && batchQueue.isNotEmpty()) {
            BatchReviewScreen(
                docId = docId,
                batchQueue = batchQueue,
                onRotateItem = { id, clockwise -> viewModel.rotateBatchItem(id, clockwise) },
                onRemoveItem = { id -> viewModel.removeBatchItem(id) },
                onUpdateCropQuad = { id, quad -> viewModel.updateBatchItemCrop(id, quad) },
                onClose = { showBatchReview = false },
                onBatchSaved = { savedDocId ->
                    viewModel.clearBatch()
                    showBatchReview = false
                    onClose()
                }
            )
        }
    }
}
