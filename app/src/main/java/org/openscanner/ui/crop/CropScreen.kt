package org.openscanner.ui.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.cv.EdgeDetector
import org.openscanner.cv.PointF
import org.openscanner.ui.theme.ScannerGreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imagePath: String,
    docId: Long,
    onNavigateToFilter: (imagePath: String, docId: Long, borderJson: String) -> Unit,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quad by remember { mutableStateOf<DocumentQuadrilateral?>(null) }
    var activeTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var viewWidth by remember { mutableStateOf(1f) }
    var viewHeight by remember { mutableStateOf(1f) }

    fun calculateImageFit(viewW: Float, viewH: Float, bmpW: Int, bmpH: Int): RectF {
        if (viewW <= 0f || viewH <= 0f || bmpW <= 0 || bmpH <= 0) return RectF(0f, 0f, viewW, viewH)
        val imageAspect = bmpW.toFloat() / bmpH.toFloat()
        val viewAspect = viewW / viewH
        return if (viewAspect > imageAspect) {
            val renderH = viewH
            val renderW = viewH * imageAspect
            val offX = (viewW - renderW) / 2f
            RectF(offX, 0f, offX + renderW, viewH)
        } else {
            val renderW = viewW
            val renderH = viewW / imageAspect
            val offY = (viewH - renderH) / 2f
            RectF(0f, offY, viewW, offY + renderH)
        }
    }

    fun mapDetectedQuadToScreen(detected: DocumentQuadrilateral, fitRect: RectF, bmpW: Int, bmpH: Int): DocumentQuadrilateral {
        val sx = fitRect.width() / bmpW.toFloat()
        val sy = fitRect.height() / bmpH.toFloat()
        return DocumentQuadrilateral(
            topLeft = PointF(fitRect.left + detected.topLeft.x * sx, fitRect.top + detected.topLeft.y * sy),
            topRight = PointF(fitRect.left + detected.topRight.x * sx, fitRect.top + detected.topRight.y * sy),
            bottomRight = PointF(fitRect.left + detected.bottomRight.x * sx, fitRect.top + detected.bottomRight.y * sy),
            bottomLeft = PointF(fitRect.left + detected.bottomLeft.x * sx, fitRect.top + detected.bottomLeft.y * sy)
        )
    }

    fun autoDetectCorners(bmp: Bitmap, fitRect: RectF) {
        coroutineScope.launch(Dispatchers.Default) {
            val detected = EdgeDetector.detectInBitmap(bmp)
            val screenQuad = mapDetectedQuadToScreen(detected, fitRect, bmp.width, bmp.height)
            withContext(Dispatchers.Main) {
                quad = screenQuad
            }
        }
    }

    // Decode sample bitmap for high performance
    LaunchedEffect(imagePath) {
        withContext(Dispatchers.IO) {
            val file = File(imagePath)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                var sampleSize = 1
                while (opts.outWidth / (sampleSize * 2) >= 1200 && opts.outHeight / (sampleSize * 2) >= 1200) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            }
        }
    }

    fun proceedToNext() {
        quad?.let { q ->
            val bmp = bitmap ?: return
            val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
            val renderW = fitRect.width()
            val renderH = fitRect.height()
            if (renderW <= 0f || renderH <= 0f) return

            // Map coordinates back to original full-resolution image
            val file = File(imagePath)
            val originalOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, originalOpts)
            val origW = originalOpts.outWidth.toFloat()
            val origH = originalOpts.outHeight.toFloat()

            fun mapPoint(pt: PointF): PointF {
                val nx = ((pt.x - fitRect.left) / renderW).coerceIn(0f, 1f)
                val ny = ((pt.y - fitRect.top) / renderH).coerceIn(0f, 1f)
                return PointF(nx * origW, ny * origH)
            }

            val realQuad = DocumentQuadrilateral(
                topLeft = mapPoint(q.topLeft),
                topRight = mapPoint(q.topRight),
                bottomRight = mapPoint(q.bottomRight),
                bottomLeft = mapPoint(q.bottomLeft)
            )

            val borderStr = "${realQuad.topLeft.x},${realQuad.topLeft.y}," +
                    "${realQuad.topRight.x},${realQuad.topRight.y}," +
                    "${realQuad.bottomRight.x},${realQuad.bottomRight.y}," +
                    "${realQuad.bottomLeft.x},${realQuad.bottomLeft.y}"

            onNavigateToFilter(imagePath, docId, borderStr)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop Document", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Button(
                        onClick = { proceedToNext() },
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Next", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF141414)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CropActionButton(
                        icon = Icons.Outlined.SelectAll,
                        label = "All",
                        onClick = {
                            bitmap?.let { b ->
                                val r = calculateImageFit(viewWidth, viewHeight, b.width, b.height)
                                quad = DocumentQuadrilateral(
                                    topLeft = PointF(r.left, r.top),
                                    topRight = PointF(r.right, r.top),
                                    bottomRight = PointF(r.right, r.bottom),
                                    bottomLeft = PointF(r.left, r.bottom)
                                )
                            }
                        }
                    )

                    CropActionButton(
                        icon = Icons.Outlined.AutoFixHigh,
                        label = "Auto",
                        onClick = {
                            bitmap?.let { b ->
                                val r = calculateImageFit(viewWidth, viewHeight, b.width, b.height)
                                autoDetectCorners(b, r)
                            }
                        }
                    )

                    CropActionButton(
                        icon = Icons.AutoMirrored.Filled.RotateLeft,
                        label = "Left",
                        onClick = {
                            bitmap?.let { b ->
                                val matrix = Matrix().apply { postRotate(-90f) }
                                val rotated = Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true)
                                bitmap = rotated
                                val r = calculateImageFit(viewWidth, viewHeight, rotated.width, rotated.height)
                                autoDetectCorners(rotated, r)
                            }
                        }
                    )

                    CropActionButton(
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        label = "Right",
                        onClick = {
                            bitmap?.let { b ->
                                val matrix = Matrix().apply { postRotate(90f) }
                                val rotated = Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true)
                                bitmap = rotated
                                val r = calculateImageFit(viewWidth, viewHeight, rotated.width, rotated.height)
                                autoDetectCorners(rotated, r)
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
            bitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            viewWidth = coordinates.size.width.toFloat()
                            viewHeight = coordinates.size.height.toFloat()
                            if (quad == null) {
                                val r = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
                                autoDetectCorners(bmp, r)
                            }
                        }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Document Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)

                    quad?.let { currentQuad ->
                        DraggablePolygonOverlay(
                            quad = currentQuad,
                            boundsRect = fitRect,
                            onQuadChanged = { quad = it },
                            onTouchPoint = { activeTouchPoint = it }
                        )

                        MagnifierLoupe(
                            bitmap = bmp,
                            touchPoint = activeTouchPoint,
                            imageScale = fitRect.width() / bmp.width.toFloat(),
                            fitRect = fitRect
                        )
                    }
                }
            } ?: CircularProgressIndicator(color = ScannerGreen)
        }
    }
}

@Composable
fun CropActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.LightGray, fontSize = 12.sp)
    }
}
