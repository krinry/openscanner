package org.openscanner.ui.viewer.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.cv.NativeScannerEngine
import org.openscanner.data.model.Page
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.ui.theme.ScannerGreen
import java.io.File

private enum class EditToolMode {
    PEN, HIGHLIGHTER, TEXT, REDACT, TUNE
}

private data class ImageDoodlePath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false,
    val isRedact: Boolean = false
)

private data class ImageTextAnnotation(
    val text: String,
    val normalizedX: Float,
    val normalizedY: Float,
    val color: Color,
    val fontSizeSp: Float = 20f
)

@Composable
fun ImageEditStudioSheet(
    page: Page,
    onDismiss: () -> Unit,
    onPageUpdated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTool by remember { mutableStateOf(EditToolMode.PEN) }
    var selectedColor by remember { mutableStateOf(Color(0xFFE53935)) } // Default red
    var strokeWidth by remember { mutableFloatStateOf(6f) }
    var highlighterWidth by remember { mutableFloatStateOf(24f) }

    // Tuning parameters
    var brightness by remember { mutableIntStateOf(0) }
    var contrast by remember { mutableIntStateOf(0) }
    var rotationStep by remember { mutableIntStateOf(0) }

    // Drawing paths and history
    val paths = remember { mutableStateListOf<ImageDoodlePath>() }
    val undonePaths = remember { mutableStateListOf<ImageDoodlePath>() }
    val textAnnotations = remember { mutableStateListOf<ImageTextAnnotation>() }
    var currentDrawingPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Dialog state for text input
    var showTextInputDialog by remember { mutableStateOf(false) }
    var pendingTextContent by remember { mutableStateOf("") }
    var pendingTextX by remember { mutableFloatStateOf(0.4f) }
    var pendingTextY by remember { mutableFloatStateOf(0.5f) }

    // Viewport sizes
    var viewWidth by remember { mutableFloatStateOf(1f) }
    var viewHeight by remember { mutableFloatStateOf(1f) }

    // Image bitmaps
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Load page bitmap
    LaunchedEffect(page.id, rotationStep) {
        withContext(Dispatchers.IO) {
            val file = File(page.processedImagePath)
            if (file.exists()) {
                val orig = BitmapFactory.decodeFile(file.absolutePath)
                if (orig != null && rotationStep != 0) {
                    val m = Matrix().apply { postRotate((rotationStep * 90).toFloat()) }
                    baseBitmap = Bitmap.createBitmap(orig, 0, 0, orig.width, orig.height, m, true)
                } else {
                    baseBitmap = orig
                }
            }
        }
    }

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

    val paletteColors = listOf(
        Color(0xFFE53935), // Red
        Color(0xFF1E88E5), // Blue
        Color(0xFF43A047), // Green
        Color(0xFFFDD835), // Yellow
        Color(0xFFFB8C00), // Orange
        Color(0xFF8E24AA), // Purple
        Color.White,
        Color.Black
    )

    fun saveEdits() {
        val base = baseBitmap ?: return
        isSaving = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val fullMutable = base.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(fullMutable)

                val fitRect = calculateImageFit(viewWidth, viewHeight, base.width, base.height)
                val scaleX = if (fitRect.width() > 0) base.width.toFloat() / fitRect.width() else 1f
                val scaleY = if (fitRect.height() > 0) base.height.toFloat() / fitRect.height() else 1f

                // Draw all doodle strokes onto fullMutable
                for (p in paths) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        if (p.isRedact) {
                            style = Paint.Style.FILL
                            color = android.graphics.Color.BLACK
                        } else if (p.isHighlighter) {
                            style = Paint.Style.STROKE
                            color = p.color.toArgb()
                            alpha = 90
                            strokeWidth = p.strokeWidth * scaleX
                        } else {
                            style = Paint.Style.STROKE
                            color = p.color.toArgb()
                            strokeWidth = p.strokeWidth * scaleX
                        }
                    }

                    if (p.isRedact && p.points.size >= 2) {
                        val first = p.points.first()
                        val last = p.points.last()
                        val left = (minOf(first.x, last.x) - fitRect.left) * scaleX
                        val top = (minOf(first.y, last.y) - fitRect.top) * scaleY
                        val right = (maxOf(first.x, last.x) - fitRect.left) * scaleX
                        val bottom = (maxOf(first.y, last.y) - fitRect.top) * scaleY
                        canvas.drawRect(left, top, right, bottom, paint)
                    } else if (p.points.size > 1) {
                        val androidPath = android.graphics.Path()
                        val start = p.points.first()
                        androidPath.moveTo((start.x - fitRect.left) * scaleX, (start.y - fitRect.top) * scaleY)
                        for (i in 1 until p.points.size) {
                            val pt = p.points[i]
                            androidPath.lineTo((pt.x - fitRect.left) * scaleX, (pt.y - fitRect.top) * scaleY)
                        }
                        canvas.drawPath(androidPath, paint)
                    }
                }

                // Draw text annotations
                for (t in textAnnotations) {
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = t.color.toArgb()
                        textSize = t.fontSizeSp * (base.width / 400f)
                        isFakeBoldText = true
                    }
                    val drawX = t.normalizedX * base.width
                    val drawY = t.normalizedY * base.height
                    canvas.drawText(t.text, drawX, drawY, textPaint)
                }

                // Adjust brightness / contrast if tuned
                if (brightness != 0 || contrast != 0) {
                    NativeScannerEngine.adjustBrightnessContrast(fullMutable, brightness, contrast)
                }

                val app = context.applicationContext as OpenScannerApp
                val repo = ScannerRepository(
                    context = context,
                    documentDao = app.database.documentDao(),
                    pageDao = app.database.pageDao(),
                    folderDao = app.database.folderDao()
                )

                repo.updatePageBitmap(
                    pageId = page.id,
                    newBitmap = fullMutable,
                    brightness = brightness,
                    contrast = contrast
                )

                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(context, "Image edits saved!", Toast.LENGTH_SHORT).show()
                    onPageUpdated()
                    onDismiss()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(context, "Failed to save edits: ${e.message}", Toast.LENGTH_SHORT).show()
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
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Undo
                        IconButton(
                            onClick = {
                                if (paths.isNotEmpty()) {
                                    val last = paths.removeAt(paths.size - 1)
                                    undonePaths.add(last)
                                } else if (textAnnotations.isNotEmpty()) {
                                    textAnnotations.removeAt(textAnnotations.size - 1)
                                }
                            },
                            enabled = paths.isNotEmpty() || textAnnotations.isNotEmpty()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (paths.isNotEmpty() || textAnnotations.isNotEmpty()) Color.White else Color(0xFF555555)
                            )
                        }

                        // Redo
                        IconButton(
                            onClick = {
                                if (undonePaths.isNotEmpty()) {
                                    val last = undonePaths.removeAt(undonePaths.size - 1)
                                    paths.add(last)
                                }
                            },
                            enabled = undonePaths.isNotEmpty()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (undonePaths.isNotEmpty()) Color.White else Color(0xFF555555)
                            )
                        }

                        // Rotate 90 CW
                        IconButton(onClick = { rotationStep = (rotationStep + 1) % 4 }) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", tint = Color.White)
                        }
                    }

                    IconButton(
                        onClick = { saveEdits() },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = ScannerGreen, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = ScannerGreen)
                        }
                    }
                }

                // Interactive Drawing Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .onGloballyPositioned { coordinates ->
                            viewWidth = coordinates.size.width.toFloat()
                            viewHeight = coordinates.size.height.toFloat()
                        }
                        .pointerInput(activeTool, selectedColor, strokeWidth, highlighterWidth) {
                            if (activeTool == EditToolMode.TEXT) {
                                detectTapGestures { offset ->
                                    val bmp = baseBitmap ?: return@detectTapGestures
                                    val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
                                    if (fitRect.contains(offset.x, offset.y)) {
                                        pendingTextX = ((offset.x - fitRect.left) / fitRect.width()).coerceIn(0f, 1f)
                                        pendingTextY = ((offset.y - fitRect.top) / fitRect.height()).coerceIn(0f, 1f)
                                        pendingTextContent = ""
                                        showTextInputDialog = true
                                    }
                                }
                            } else if (activeTool == EditToolMode.PEN || activeTool == EditToolMode.HIGHLIGHTER || activeTool == EditToolMode.REDACT) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentDrawingPoints = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentDrawingPoints = currentDrawingPoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentDrawingPoints.isNotEmpty()) {
                                            paths.add(
                                                ImageDoodlePath(
                                                    points = currentDrawingPoints,
                                                    color = selectedColor,
                                                    strokeWidth = if (activeTool == EditToolMode.HIGHLIGHTER) highlighterWidth else strokeWidth,
                                                    isHighlighter = activeTool == EditToolMode.HIGHLIGHTER,
                                                    isRedact = activeTool == EditToolMode.REDACT
                                                )
                                            )
                                            undonePaths.clear()
                                            currentDrawingPoints = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        currentDrawingPoints = emptyList()
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    baseBitmap?.let { bmp ->
                        val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)

                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Edit Base Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // Render Canvas Overlay for Strokes and Text
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw committed paths
                            for (p in paths) {
                                if (p.isRedact && p.points.size >= 2) {
                                    val first = p.points.first()
                                    val last = p.points.last()
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(minOf(first.x, last.x), minOf(first.y, last.y)),
                                        size = androidx.compose.ui.geometry.Size(
                                            kotlin.math.abs(last.x - first.x),
                                            kotlin.math.abs(last.y - first.y)
                                        )
                                    )
                                } else if (p.points.size > 1) {
                                    val drawPath = androidx.compose.ui.graphics.Path()
                                    drawPath.moveTo(p.points.first().x, p.points.first().y)
                                    for (i in 1 until p.points.size) {
                                        drawPath.lineTo(p.points[i].x, p.points[i].y)
                                    }
                                    drawPath(
                                        path = drawPath,
                                        color = if (p.isHighlighter) p.color.copy(alpha = 0.35f) else p.color,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = p.strokeWidth,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            // Draw currently in-progress active drag stroke
                            if (currentDrawingPoints.size > 1) {
                                if (activeTool == EditToolMode.REDACT && currentDrawingPoints.size >= 2) {
                                    val first = currentDrawingPoints.first()
                                    val last = currentDrawingPoints.last()
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.8f),
                                        topLeft = Offset(minOf(first.x, last.x), minOf(first.y, last.y)),
                                        size = androidx.compose.ui.geometry.Size(
                                            kotlin.math.abs(last.x - first.x),
                                            kotlin.math.abs(last.y - first.y)
                                        )
                                    )
                                } else {
                                    val activePath = androidx.compose.ui.graphics.Path()
                                    activePath.moveTo(currentDrawingPoints.first().x, currentDrawingPoints.first().y)
                                    for (i in 1 until currentDrawingPoints.size) {
                                        activePath.lineTo(currentDrawingPoints[i].x, currentDrawingPoints[i].y)
                                    }
                                    drawPath(
                                        path = activePath,
                                        color = if (activeTool == EditToolMode.HIGHLIGHTER) selectedColor.copy(alpha = 0.35f) else selectedColor,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = if (activeTool == EditToolMode.HIGHLIGHTER) highlighterWidth else strokeWidth,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }

                        // Render text annotations as overlay chips
                        for (t in textAnnotations) {
                            val posX = fitRect.left + t.normalizedX * fitRect.width()
                            val posY = fitRect.top + t.normalizedY * fitRect.height()
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.offset(x = (posX - 20).dp, y = (posY - 10).dp)
                            ) {
                                Text(
                                    text = t.text,
                                    color = t.color,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } ?: CircularProgressIndicator(color = ScannerGreen)
                }

                // Bottom Tools Selector & Sub-options
                Surface(
                    color = Color(0xFF141414),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        // Sub-options row based on selected active tool
                        when (activeTool) {
                            EditToolMode.PEN, EditToolMode.HIGHLIGHTER -> {
                                // Color palette selector
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(paletteColors) { color ->
                                        val isSelected = selectedColor == color
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) ScannerGreen else Color(0xFF444444),
                                                    shape = CircleShape
                                                )
                                                .clickable { selectedColor = color }
                                        )
                                    }
                                }

                                // Stroke width slider
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Size", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Slider(
                                        value = if (activeTool == EditToolMode.HIGHLIGHTER) highlighterWidth else strokeWidth,
                                        onValueChange = {
                                            if (activeTool == EditToolMode.HIGHLIGHTER) highlighterWidth = it else strokeWidth = it
                                        },
                                        valueRange = if (activeTool == EditToolMode.HIGHLIGHTER) 12f..50f else 2f..24f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = ScannerGreen,
                                            activeTrackColor = ScannerGreen,
                                            inactiveTrackColor = Color(0xFF333333)
                                        ),
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                            EditToolMode.TEXT -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Tap on the image to place text", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                                    Button(
                                        onClick = { showTextInputDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("+ Add Text", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                            EditToolMode.REDACT -> {
                                Text(
                                    text = "Drag a box on the image to blackout sensitive information",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )
                            }
                            EditToolMode.TUNE -> {
                                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
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
                                        colors = SliderDefaults.colors(thumbColor = ScannerGreen, activeTrackColor = ScannerGreen),
                                        modifier = Modifier.height(26.dp)
                                    )
                                    Text("Contrast: $contrast", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                                    Slider(
                                        value = contrast.toFloat(),
                                        onValueChange = { contrast = it.toInt() },
                                        valueRange = -80f..80f,
                                        colors = SliderDefaults.colors(thumbColor = ScannerGreen, activeTrackColor = ScannerGreen),
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF222222), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))

                        // Tool Switcher Icons Row
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolTabItem(
                                icon = Icons.Outlined.Brush,
                                label = "Draw",
                                isSelected = activeTool == EditToolMode.PEN,
                                onClick = { activeTool = EditToolMode.PEN }
                            )
                            ToolTabItem(
                                icon = Icons.Outlined.Highlight,
                                label = "Highlight",
                                isSelected = activeTool == EditToolMode.HIGHLIGHTER,
                                onClick = { activeTool = EditToolMode.HIGHLIGHTER }
                            )
                            ToolTabItem(
                                icon = Icons.Outlined.TextFields,
                                label = "Text",
                                isSelected = activeTool == EditToolMode.TEXT,
                                onClick = { activeTool = EditToolMode.TEXT }
                            )
                            ToolTabItem(
                                icon = Icons.Outlined.Security,
                                label = "Redact",
                                isSelected = activeTool == EditToolMode.REDACT,
                                onClick = { activeTool = EditToolMode.REDACT }
                            )
                            ToolTabItem(
                                icon = Icons.Outlined.Tune,
                                label = "Tune",
                                isSelected = activeTool == EditToolMode.TUNE,
                                onClick = { activeTool = EditToolMode.TUNE }
                            )
                        }
                    }
                }
            }
        }
    }

    // Text Input Dialog
    if (showTextInputDialog) {
        var textValue by remember { mutableStateOf(pendingTextContent) }
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = { Text("Add Text Annotation", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        placeholder = { Text("Enter text") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textValue.isNotBlank()) {
                            textAnnotations.add(
                                ImageTextAnnotation(
                                    text = textValue.trim(),
                                    normalizedX = pendingTextX,
                                    normalizedY = pendingTextY,
                                    color = selectedColor
                                )
                            )
                        }
                        showTextInputDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Add", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) {
                    Text("Cancel", color = Color(0xFFAAAAAA))
                }
            }
        )
    }
}

@Composable
private fun ToolTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) ScannerGreen else Color(0xFFBBBBBB),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) ScannerGreen else Color(0xFFBBBBBB),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
