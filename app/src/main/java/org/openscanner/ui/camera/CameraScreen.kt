package org.openscanner.ui.camera

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.R
import org.openscanner.camera.CaptureMode
import org.openscanner.camera.DocumentAnalyzer
import org.openscanner.ui.theme.ScannerGreen
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    docId: Long,
    initialMode: String = "Single",
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
                    val targetFile = java.io.File(context.cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    onPhotoCaptured(targetFile.absolutePath, docId)
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

    var showGrid by remember { mutableStateOf(false) }
    var autoCropEnabled by remember { mutableStateOf(true) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var idCardFrontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val flashIcon = when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
        else -> Icons.Default.FlashOff
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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

        // Green boundary overlay with corner brackets & grid
        ViewfinderOverlay(
            mode = currentMode,
            quad = if (autoCropEnabled) detectedQuad else null,
            previewWidth = previewDimens.first,
            previewHeight = previewDimens.second,
            showGrid = showGrid
        )

        // Guide pill for ID Card & QR
        if (currentMode == CaptureMode.ID_CARD || currentMode == CaptureMode.QR_CODE) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp),
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ScannerGreen)
            ) {
                Text(
                    text = if (currentMode == CaptureMode.ID_CARD) {
                        if (idCardFrontBitmap == null) "Step 1 of 2: Align FRONT of ID Card" else "Step 2 of 2: Align BACK of ID Card"
                    } else "Align QR code within the frame",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
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

        // Bottom Controls: Modes Carousel + Camera Shutter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(bottom = 36.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Carousel
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
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

                // Camera Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(ScannerGreen)
                        .clickable {
                            imageCapture?.let { capture ->
                                if (currentMode == CaptureMode.ID_CARD) {
                                    viewModel.capturePhoto(
                                        imageCapture = capture,
                                        onPhotoCaptured = { savedPath ->
                                            val bmp = BitmapFactory.decodeFile(savedPath)
                                            if (bmp != null) {
                                                if (idCardFrontBitmap == null) {
                                                    idCardFrontBitmap = bmp
                                                    Toast.makeText(context, "Front side saved! Now capture Back side.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val front = idCardFrontBitmap!!
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val app = context.applicationContext as OpenScannerApp
                                                        val repo = org.openscanner.data.repository.ScannerRepository(
                                                            context = context,
                                                            documentDao = app.database.documentDao(),
                                                            pageDao = app.database.pageDao(),
                                                            folderDao = app.database.folderDao()
                                                        )
                                                        val targetDocId = if (docId > 0) docId else repo.createNewDocument("ID Card Scan")
                                                        repo.stitchIdCardPages(front, bmp, targetDocId)
                                                        withContext(Dispatchers.Main) {
                                                            idCardFrontBitmap = null
                                                            Toast.makeText(context, "ID Card 2-sided scan created!", Toast.LENGTH_LONG).show()
                                                            onClose()
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onError = { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() }
                                    )
                                } else {
                                    viewModel.capturePhoto(
                                        imageCapture = capture,
                                        onPhotoCaptured = { savedPath ->
                                            onPhotoCaptured(savedPath, docId)
                                        },
                                        onError = { /* Log error */ }
                                    )
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

                // Batch Mode Counter
                if (currentMode == CaptureMode.BATCH && batchCount > 0) {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onClose() },
                        color = ScannerGreen
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = batchCount.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}
