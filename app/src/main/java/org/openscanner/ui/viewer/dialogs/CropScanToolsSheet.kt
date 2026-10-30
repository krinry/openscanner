package org.openscanner.ui.viewer.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.cv.EdgeDetector
import org.openscanner.cv.NativeScannerEngine
import org.openscanner.cv.PointF
import org.openscanner.data.model.EnhanceMode
import org.openscanner.data.model.Page
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.ui.crop.DraggablePolygonOverlay
import org.openscanner.ui.crop.MagnifierLoupe
import org.openscanner.ui.theme.ScannerGreen
import java.io.File
import kotlin.math.max

@Composable
fun CropScanToolsSheet(
    page: Page,
    onDismiss: () -> Unit,
    onPageUpdated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Crop, 1: Filter
    var isSaving by remember { mutableStateOf(false) }

    // Bitmap states
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fullImageWidth by remember { mutableFloatStateOf(0f) }
    var fullImageHeight by remember { mutableFloatStateOf(0f) }
    var rotationAngle by remember { mutableIntStateOf(page.rotationDegrees) }

    // Crop Quad states
    var quad by remember { mutableStateOf<DocumentQuadrilateral?>(null) }
    var activeTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var activeHandlePoint by remember { mutableStateOf<Offset?>(null) }
    var viewWidth by remember { mutableFloatStateOf(1f) }
    var viewHeight by remember { mutableFloatStateOf(1f) }

    // Filter states
    var selectedFilterMode by remember { mutableStateOf(EnhanceMode.fromId(page.enhanceMode)) }
    var brightness by remember { mutableIntStateOf(page.brightness) }
    var contrast by remember { mutableIntStateOf(page.contrast) }
    var warpedFilterPreview by remember { mutableStateOf<Bitmap?>(null) }
    var filterJob by remember { mutableStateOf<Job?>(null) }

    fun calculateImageFit(vW: Float, vH: Float, bW: Int, bH: Int): RectF {
        if (vW <= 0f || vH <= 0f || bW <= 0 || bH <= 0) return RectF(0f, 0f, vW, vH)
        val imageAspect = bW.toFloat() / bH.toFloat()
        val viewAspect = vW / vH
        return if (viewAspect > imageAspect) {
            val renderW = vH * imageAspect
            val oX = (vW - renderW) / 2f
            RectF(oX, 0f, oX + renderW, vH)
        } else {
            val renderH = vW / imageAspect
            val oY = (vH - renderH) / 2f
            RectF(0f, oY, vW, oY + renderH)
        }
    }

    fun mapDetectedQuadToScreen(detected: DocumentQuadrilateral, fitRect: RectF, bW: Int, bH: Int): DocumentQuadrilateral {
        val sx = fitRect.width() / bW.toFloat()
        val sy = fitRect.height() / bH.toFloat()
        return DocumentQuadrilateral(
            topLeft = PointF(
                (fitRect.left + detected.topLeft.x * sx).coerceIn(fitRect.left, fitRect.right),
                (fitRect.top + detected.topLeft.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            ),
            topRight = PointF(
                (fitRect.left + detected.topRight.x * sx).coerceIn(fitRect.left, fitRect.right),
                (fitRect.top + detected.topRight.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            ),
            bottomRight = PointF(
                (fitRect.left + detected.bottomRight.x * sx).coerceIn(fitRect.left, fitRect.right),
                (fitRect.top + detected.bottomRight.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            ),
            bottomLeft = PointF(
                (fitRect.left + detected.bottomLeft.x * sx).coerceIn(fitRect.left, fitRect.right),
                (fitRect.top + detected.bottomLeft.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            )
        )
    }

    // Load initial bitmap
    LaunchedEffect(page.id, rotationAngle) {
        withContext(Dispatchers.IO) {
            val file = listOf(File(page.originalImagePath), File(page.processedImagePath))
                .firstOrNull { it.exists() && it.length() > 0 }
            if (file != null) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                fullImageWidth = opts.outWidth.toFloat()
                fullImageHeight = opts.outHeight.toFloat()

                val maxDim = 1200
                var sample = 1
                while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val dec = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                if (dec != null && rotationAngle != 0) {
                    val m = Matrix().apply { postRotate(rotationAngle.toFloat()) }
                    rawBitmap = Bitmap.createBitmap(dec, 0, 0, dec.width, dec.height, m, true)
                } else {
                    rawBitmap = dec
                }
            }
        }
    }

    // Initialize or recalculate quad when bitmap and view are ready
    LaunchedEffect(rawBitmap, viewWidth, viewHeight) {
        val bmp = rawBitmap ?: return@LaunchedEffect
        if (viewWidth <= 1f || viewHeight <= 1f) return@LaunchedEffect
        val fit = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)

        if (quad == null) {
            if (page.cropBorderJson.isNotBlank()) {
                val parts = page.cropBorderJson.split(",").mapNotNull { it.toFloatOrNull() }
                if (parts.size >= 8) {
                    val origQuad = DocumentQuadrilateral.fromFloatArray(parts.toFloatArray())
                    val origW = if (fullImageWidth > 0f) fullImageWidth else bmp.width.toFloat()
                    val origH = if (fullImageHeight > 0f) fullImageHeight else bmp.height.toFloat()
                    val sx = fit.width() / origW
                    val sy = fit.height() / origH
                    quad = DocumentQuadrilateral(
                        topLeft = PointF(fit.left + origQuad.topLeft.x * sx, fit.top + origQuad.topLeft.y * sy),
                        topRight = PointF(fit.left + origQuad.topRight.x * sx, fit.top + origQuad.topRight.y * sy),
                        bottomRight = PointF(fit.left + origQuad.bottomRight.x * sx, fit.top + origQuad.bottomRight.y * sy),
                        bottomLeft = PointF(fit.left + origQuad.bottomLeft.x * sx, fit.top + origQuad.bottomLeft.y * sy)
                    )
                } else {
                    val detected = EdgeDetector.detectInBitmap(bmp)
                    quad = mapDetectedQuadToScreen(detected, fit, bmp.width, bmp.height)
                }
            } else {
                val detected = EdgeDetector.detectInBitmap(bmp)
                quad = mapDetectedQuadToScreen(detected, fit, bmp.width, bmp.height)
            }
        }
    }

    // Update filter preview whenever switching to filter tab or changing parameters
    fun updateFilterPreview() {
        val bmp = rawBitmap ?: return
        val currentQuad = quad ?: return
        val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
        val renderW = fitRect.width()
        val renderH = fitRect.height()
        if (renderW <= 0f || renderH <= 0f) return

        filterJob?.cancel()
        filterJob = coroutineScope.launch(Dispatchers.Default) {
            fun mapPoint(pt: PointF): PointF {
                val nx = ((pt.x - fitRect.left) / renderW).coerceIn(0f, 1f)
                val ny = ((pt.y - fitRect.top) / renderH).coerceIn(0f, 1f)
                return PointF(nx * bmp.width, ny * bmp.height)
            }

            val mappedQuad = DocumentQuadrilateral(
                topLeft = mapPoint(currentQuad.topLeft),
                topRight = mapPoint(currentQuad.topRight),
                bottomRight = mapPoint(currentQuad.bottomRight),
                bottomLeft = mapPoint(currentQuad.bottomLeft)
            )

            val topW = mappedQuad.topLeft.distanceTo(mappedQuad.topRight)
            val botW = mappedQuad.bottomLeft.distanceTo(mappedQuad.bottomRight)
            val leftH = mappedQuad.topLeft.distanceTo(mappedQuad.bottomLeft)
            val rightH = mappedQuad.topRight.distanceTo(mappedQuad.bottomRight)

            val targetW = max(topW, botW).toInt().coerceAtLeast(300)
            val targetH = max(leftH, rightH).toInt().coerceAtLeast(400)

            val warped = NativeScannerEngine.warpDocument(bmp, mappedQuad, targetW, targetH)
            val copy = warped.copy(Bitmap.Config.ARGB_8888, true)
            NativeScannerEngine.applyEnhancement(copy, selectedFilterMode.id)
            if (brightness != 0 || contrast != 0) {
                NativeScannerEngine.adjustBrightnessContrast(copy, brightness, contrast)
            }
            withContext(Dispatchers.Main) {
                warpedFilterPreview = copy
            }
        }
    }

    LaunchedEffect(activeTab, selectedFilterMode, brightness, contrast) {
        if (activeTab == 1) {
            updateFilterPreview()
        }
    }

    fun saveAdjustments() {
        val q = quad ?: return
        isSaving = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val file = listOf(File(page.originalImagePath), File(page.processedImagePath))
                    .firstOrNull { it.exists() && it.length() > 0 }
                if (file == null) {
                    withContext(Dispatchers.Main) { isSaving = false }
                    return@launch
                }

                val fullBmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@launch
                val rotatedFull = if (rotationAngle != 0) {
                    val m = Matrix().apply { postRotate(rotationAngle.toFloat()) }
                    Bitmap.createBitmap(fullBmp, 0, 0, fullBmp.width, fullBmp.height, m, true)
                } else {
                    fullBmp
                }

                val bmp = rawBitmap ?: fullBmp
                val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
                val renderW = fitRect.width()
                val renderH = fitRect.height()

                fun mapPoint(pt: PointF): PointF {
                    val nx = ((pt.x - fitRect.left) / renderW).coerceIn(0f, 1f)
                    val ny = ((pt.y - fitRect.top) / renderH).coerceIn(0f, 1f)
                    return PointF(nx * rotatedFull.width, ny * rotatedFull.height)
                }

                val realQuad = DocumentQuadrilateral(
                    topLeft = mapPoint(q.topLeft),
                    topRight = mapPoint(q.topRight),
                    bottomRight = mapPoint(q.bottomRight),
                    bottomLeft = mapPoint(q.bottomLeft)
                )

                val topW = realQuad.topLeft.distanceTo(realQuad.topRight)
                val botW = realQuad.bottomLeft.distanceTo(realQuad.bottomRight)
                val leftH = realQuad.topLeft.distanceTo(realQuad.bottomLeft)
                val rightH = realQuad.topRight.distanceTo(realQuad.bottomRight)

                val targetW = max(topW, botW).toInt().coerceAtLeast(300)
                val targetH = max(leftH, rightH).toInt().coerceAtLeast(400)

                val finalWarped = NativeScannerEngine.warpDocument(rotatedFull, realQuad, targetW, targetH)
                val finalProcessed = finalWarped.copy(Bitmap.Config.ARGB_8888, true)
                NativeScannerEngine.applyEnhancement(finalProcessed, selectedFilterMode.id)
                if (brightness != 0 || contrast != 0) {
                    NativeScannerEngine.adjustBrightnessContrast(finalProcessed, brightness, contrast)
                }

                val borderStr = "${realQuad.topLeft.x},${realQuad.topLeft.y}," +
                        "${realQuad.topRight.x},${realQuad.topRight.y}," +
                        "${realQuad.bottomRight.x},${realQuad.bottomRight.y}," +
                        "${realQuad.bottomLeft.x},${realQuad.bottomLeft.y}"

                val app = context.applicationContext as OpenScannerApp
                val repo = ScannerRepository(
                    context = context,
                    documentDao = app.database.documentDao(),
                    pageDao = app.database.pageDao(),
                    folderDao = app.database.folderDao()
                )

                repo.updatePageBitmap(
                    pageId = page.id,
                    newBitmap = finalProcessed,
                    cropBorderJson = borderStr,
                    enhanceModeId = selectedFilterMode.id,
                    rotationDegrees = rotationAngle,
                    brightness = brightness,
                    contrast = contrast
                )

                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(context, "Page scan updated!", Toast.LENGTH_SHORT).show()
                    onPageUpdated()
                    onDismiss()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(context, "Failed to apply changes: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    // Mode Switcher Tabs
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF222222))
                            .padding(3.dp)
                    ) {
                        Surface(
                            color = if (activeTab == 0) ScannerGreen else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { activeTab = 0 }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Crop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Crop", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            color = if (activeTab == 1) ScannerGreen else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { activeTab = 1 }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.FilterAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Filters", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    IconButton(
                        onClick = { saveAdjustments() },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = ScannerGreen, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = ScannerGreen)
                        }
                    }
                }

                // Middle Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .onGloballyPositioned { coordinates ->
                            viewWidth = coordinates.size.width.toFloat()
                            viewHeight = coordinates.size.height.toFloat()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (activeTab == 0) {
                        // Crop View
                        rawBitmap?.let { bmp ->
                            val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)

                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Crop Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            quad?.let { q ->
                                DraggablePolygonOverlay(
                                    quad = q,
                                    boundsRect = fitRect,
                                    onQuadChanged = { updatedQuad -> quad = updatedQuad },
                                    onTouchPoint = { pt -> activeTouchPoint = pt },
                                    onHandleActive = { handlePt, touchPt ->
                                        activeHandlePoint = handlePt
                                        activeTouchPoint = touchPt
                                    }
                                )

                                val imgScale = if (bmp.width > 0) fitRect.width() / bmp.width.toFloat() else 1f
                                MagnifierLoupe(
                                    bitmap = bmp,
                                    touchPoint = activeTouchPoint,
                                    imageScale = imgScale,
                                    fitRect = fitRect,
                                    handlePoint = activeHandlePoint
                                )
                            }
                        } ?: CircularProgressIndicator(color = ScannerGreen)
                    } else {
                        // Filter View
                        warpedFilterPreview?.let { previewBmp ->
                            Image(
                                bitmap = previewBmp.asImageBitmap(),
                                contentDescription = "Enhanced Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } ?: CircularProgressIndicator(color = ScannerGreen)
                    }
                }

                // Bottom Tool Controls
                Surface(
                    color = Color(0xFF141414),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    if (activeTab == 0) {
                        // Crop Action Tools (Rotate Left, Rotate Right, Auto Detect, Select All)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CropBottomButton(
                                icon = Icons.AutoMirrored.Filled.RotateLeft,
                                label = "Left 90°",
                                onClick = { rotationAngle = (rotationAngle - 90 + 360) % 360 }
                            )
                            CropBottomButton(
                                icon = Icons.AutoMirrored.Filled.RotateRight,
                                label = "Right 90°",
                                onClick = { rotationAngle = (rotationAngle + 90) % 360 }
                            )
                            CropBottomButton(
                                icon = Icons.Outlined.AutoFixHigh,
                                label = "Auto Detect",
                                onClick = {
                                    val bmp = rawBitmap ?: return@CropBottomButton
                                    val fit = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
                                    val detected = EdgeDetector.detectInBitmap(bmp)
                                    quad = mapDetectedQuadToScreen(detected, fit, bmp.width, bmp.height)
                                    Toast.makeText(context, "Edges auto-detected", Toast.LENGTH_SHORT).show()
                                }
                            )
                            CropBottomButton(
                                icon = Icons.Outlined.SelectAll,
                                label = "Select All",
                                onClick = {
                                    val bmp = rawBitmap ?: return@CropBottomButton
                                    val fit = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
                                    quad = DocumentQuadrilateral(
                                        topLeft = PointF(fit.left, fit.top),
                                        topRight = PointF(fit.right, fit.top),
                                        bottomRight = PointF(fit.right, fit.bottom),
                                        bottomLeft = PointF(fit.left, fit.bottom)
                                    )
                                }
                            )
                        }
                    } else {
                        // Filters Carousel & Tuning Sliders
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            // Filter Modes Carousel
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(EnhanceMode.entries) { mode ->
                                    val isSelected = selectedFilterMode == mode
                                    Surface(
                                        color = if (isSelected) ScannerGreen.copy(alpha = 0.25f) else Color(0xFF222222),
                                        shape = RoundedCornerShape(12.dp),
                                        border = if (isSelected) BorderStroke(1.5.dp, ScannerGreen) else null,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedFilterMode = mode }
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = mode.displayName,
                                                color = if (isSelected) ScannerGreen else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Brightness and Contrast Sliders
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Brightness: $brightness", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                                    TextButton(onClick = { brightness = 0; contrast = 0 }) {
                                        Text("Reset", color = ScannerGreen, fontSize = 11.sp)
                                    }
                                }
                                Slider(
                                    value = brightness.toFloat(),
                                    onValueChange = { brightness = it.toInt() },
                                    valueRange = -80f..80f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ScannerGreen,
                                        activeTrackColor = ScannerGreen,
                                        inactiveTrackColor = Color(0xFF333333)
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Contrast: $contrast", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                                }
                                Slider(
                                    value = contrast.toFloat(),
                                    onValueChange = { contrast = it.toInt() },
                                    valueRange = -80f..80f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ScannerGreen,
                                        activeTrackColor = ScannerGreen,
                                        inactiveTrackColor = Color(0xFF333333)
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropBottomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
