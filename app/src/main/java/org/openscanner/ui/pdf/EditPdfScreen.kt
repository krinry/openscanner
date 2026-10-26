package org.openscanner.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.cv.ThorvgEngine
import org.openscanner.data.model.Document
import org.openscanner.data.model.Page
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.ink.InkStrokeManager
import org.openscanner.pdf.PdfExportOptions
import org.openscanner.pdf.PdfGenerator
import org.openscanner.pdf.PdfPageSize
import org.openscanner.pdf.PdfQuality
import org.openscanner.ui.theme.ScannerGreen
import org.openscanner.ui.theme.ScannerTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class PdfTool {
    PAN_ZOOM,
    PENCIL,
    HIGHLIGHTER,
    SIGNATURE,
    SHAPE,
    SMUDGE_BLUR,
    TEXT,
    ERASER
}

enum class AnnotationShapeType(val label: String) {
    RECTANGLE("Box"),
    FILLED_RECT("Redaction"),
    HIGHLIGHTER_BOX("Highlight Box"),
    CIRCLE("Circle"),
    ARROW("Arrow"),
    LINE("Line")
}

sealed class PageAnnotation {
    data class Stroke(
        val points: List<Offset>,
        val color: Color,
        val strokeWidth: Float,
        val opacity: Float = 1.0f,
        val isHighlighter: Boolean = false,
        val isSmudge: Boolean = false,
        val isSignature: Boolean = false
    ) : PageAnnotation()

    data class Shape(
        val shapeType: AnnotationShapeType,
        val start: Offset,
        val end: Offset,
        val borderColor: Color,
        val strokeWidth: Float,
        val borderOpacity: Float = 1.0f,
        val fillType: ThorvgEngine.FillType = ThorvgEngine.FillType.NONE,
        val fillColor1: Color = borderColor,
        val fillColor2: Color = borderColor,
        val fillOpacity: Float = 1.0f,
        val dashStyle: ThorvgEngine.DashStyle = ThorvgEngine.DashStyle.SOLID,
        val cornerRadius: Float = 6.0f
    ) : PageAnnotation()

    data class Text(
        val text: String,
        val position: Offset,
        val color: Color,
        val fontSize: Float = 16f
    ) : PageAnnotation()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPdfScreen(
    docId: Long,
    initialPageIndex: Int = 0,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val app = context.applicationContext as OpenScannerApp
    val repository = remember {
        ScannerRepository(context, app.database.documentDao(), app.database.pageDao(), app.database.folderDao())
    }

    var document by remember { mutableStateOf<Document?>(null) }
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(initialPageIndex) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Active editing tool
    var activeTool by remember { mutableStateOf(PdfTool.PENCIL) }
    var selectedShapeType by remember { mutableStateOf(AnnotationShapeType.RECTANGLE) }
    var selectedColor by remember { mutableStateOf(Color(0xFFFFEB3B)) } // Fluorescent highlighter yellow
    var strokeWidth by remember { mutableFloatStateOf(8f) }
    var selectedFillType by remember { mutableStateOf(ThorvgEngine.FillType.NONE) }
    var selectedDashStyle by remember { mutableStateOf(ThorvgEngine.DashStyle.SOLID) }
    var annotationOpacity by remember { mutableFloatStateOf(1.0f) }
    var gradientEndColor by remember { mutableStateOf(Color(0xFFFF9800)) }
    var showSignatureDialog by remember { mutableStateOf(false) }

    // Annotations map: pageId -> List<PageAnnotation>
    var pageAnnotations by remember { mutableStateOf(mapOf<Long, List<PageAnnotation>>()) }
    // Undo/Redo stacks: pageId -> History
    var undoStack by remember { mutableStateOf(mapOf<Long, List<List<PageAnnotation>>>()) }
    var redoStack by remember { mutableStateOf(mapOf<Long, List<List<PageAnnotation>>>()) }

    // Current in-progress drawing element
    var currentDrawingPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var shapeStartPoint by remember { mutableStateOf<Offset?>(null) }
    var shapeEndPoint by remember { mutableStateOf<Offset?>(null) }

    // Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // PDF Export Options Side Sheet
    var showPdfSettingsSheet by remember { mutableStateOf(false) }
    var exportPageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var exportQuality by remember { mutableStateOf(PdfQuality.MEDIUM) }
    var exportPassword by remember { mutableStateOf("") }
    var exportWatermark by remember { mutableStateOf("") }

    // Text annotation dialog
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var textTapPosition by remember { mutableStateOf(Offset.Zero) }

    // Load Document and Pages
    LaunchedEffect(docId) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val doc = repository.getDocumentById(docId)
            val pageList = repository.getPagesListForDocument(docId)
            withContext(Dispatchers.Main) {
                document = doc
                pages = pageList
                if (initialPageIndex in pageList.indices) {
                    currentPageIndex = initialPageIndex
                }
                isLoading = false
            }
        }
    }

    val currentPage = pages.getOrNull(currentPageIndex)
    val currentAnnotations = currentPage?.let { pageAnnotations[it.id] } ?: emptyList()

    // Helper to push state to undo stack
    fun pushUndo(pageId: Long, newAnnotations: List<PageAnnotation>) {
        val currentList = pageAnnotations[pageId] ?: emptyList()
        val currentHistory = undoStack[pageId] ?: emptyList()
        undoStack = undoStack + (pageId to (currentHistory + listOf(currentList)))
        redoStack = redoStack + (pageId to emptyList())
        pageAnnotations = pageAnnotations + (pageId to newAnnotations)
    }

    fun undo(pageId: Long) {
        val history = undoStack[pageId] ?: return
        if (history.isEmpty()) return
        val previousState = history.last()
        val current = pageAnnotations[pageId] ?: emptyList()

        undoStack = undoStack + (pageId to history.dropLast(1))
        val currentRedo = redoStack[pageId] ?: emptyList()
        redoStack = redoStack + (pageId to (currentRedo + listOf(current)))
        pageAnnotations = pageAnnotations + (pageId to previousState)
    }

    fun redo(pageId: Long) {
        val redos = redoStack[pageId] ?: return
        if (redos.isEmpty()) return
        val nextState = redos.last()
        val current = pageAnnotations[pageId] ?: emptyList()

        redoStack = redoStack + (pageId to redos.dropLast(1))
        val currentUndo = undoStack[pageId] ?: emptyList()
        undoStack = undoStack + (pageId to (currentUndo + listOf(current)))
        pageAnnotations = pageAnnotations + (pageId to nextState)
    }

    // Save baked annotations to storage files using ThorVG vector rasterizer
    suspend fun saveAnnotatedPages(): Boolean = withContext(Dispatchers.IO) {
        val activeWatermark = exportWatermark.ifBlank { null }
        if (pageAnnotations.isEmpty() && activeWatermark == null) return@withContext true
        try {
            val pagesToProcess = if (activeWatermark == null) {
                pages.filter { pageAnnotations[it.id]?.isNotEmpty() == true }
            } else {
                pages
            }

            for (p in pagesToProcess) {
                val annotations = pageAnnotations[p.id] ?: emptyList()
                val file = File(p.processedImagePath)
                if (!file.exists()) continue

                val baseBmp = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                val mutableBmp = baseBmp.copy(Bitmap.Config.ARGB_8888, true)

                val bmpW = mutableBmp.width.toFloat()
                val bmpH = mutableBmp.height.toFloat()
                val sx = bmpW / canvasSize.width.coerceAtLeast(1)
                val sy = bmpH / canvasSize.height.coerceAtLeast(1)

                // Render each annotation using ThorVG high-performance vector engine
                annotations.forEach { item ->
                    when (item) {
                        is PageAnnotation.Stroke -> {
                            val scaledWidth = item.strokeWidth * sx
                            if (item.isSmudge) {
                                // Apply high-speed Gaussian/smudge blur directly on the stroke's bounding area
                                if (item.points.isNotEmpty()) {
                                    val minX = (item.points.minOf { it.x } * sx - scaledWidth).toInt().coerceAtLeast(0)
                                    val minY = (item.points.minOf { it.y } * sy - scaledWidth).toInt().coerceAtLeast(0)
                                    val maxX = (item.points.maxOf { it.x } * sx + scaledWidth).toInt().coerceAtMost(mutableBmp.width)
                                    val maxY = (item.points.maxOf { it.y } * sy + scaledWidth).toInt().coerceAtMost(mutableBmp.height)
                                    ThorvgEngine.applySmudgeBlur(
                                        bitmap = mutableBmp,
                                        left = minX,
                                        top = minY,
                                        right = maxX,
                                        bottom = maxY,
                                        radius = (scaledWidth * 0.7f).toInt().coerceIn(6, 32)
                                    )
                                }
                            } else {
                                val flatPoints = FloatArray(item.points.size * 2)
                                for (i in item.points.indices) {
                                    flatPoints[i * 2] = item.points[i].x * sx
                                    flatPoints[i * 2 + 1] = item.points[i].y * sy
                                }
                                val strokeColor = if (item.isHighlighter) {
                                    item.color.copy(alpha = 0.45f * item.opacity)
                                } else {
                                    item.color
                                }
                                ThorvgEngine.renderSmoothVectorStroke(
                                    bitmap = mutableBmp,
                                    points = flatPoints,
                                    strokeWidth = scaledWidth,
                                    color = strokeColor,
                                    opacity = item.opacity,
                                    isCubicSmoothed = true
                                )
                            }
                        }
                        is PageAnnotation.Shape -> {
                            val thShapeType = when (item.shapeType) {
                                AnnotationShapeType.RECTANGLE -> ThorvgEngine.ShapeType.RECTANGLE
                                AnnotationShapeType.FILLED_RECT -> ThorvgEngine.ShapeType.FILLED_BOX
                                AnnotationShapeType.HIGHLIGHTER_BOX -> ThorvgEngine.ShapeType.HIGHLIGHTER_BOX
                                AnnotationShapeType.CIRCLE -> ThorvgEngine.ShapeType.CIRCLE
                                AnnotationShapeType.LINE -> ThorvgEngine.ShapeType.LINE
                                AnnotationShapeType.ARROW -> ThorvgEngine.ShapeType.ARROW
                            }
                            ThorvgEngine.renderStyledShape(
                                bitmap = mutableBmp,
                                shapeType = thShapeType,
                                x1 = item.start.x * sx,
                                y1 = item.start.y * sy,
                                x2 = item.end.x * sx,
                                y2 = item.end.y * sy,
                                strokeWidth = item.strokeWidth * sx,
                                borderColor = item.borderColor,
                                borderOpacity = item.borderOpacity,
                                fillType = item.fillType,
                                fillColor1 = item.fillColor1,
                                fillColor2 = item.fillColor2,
                                fillOpacity = item.fillOpacity,
                                dashStyle = item.dashStyle,
                                cornerRadius = item.cornerRadius * sx
                            )
                        }
                        is PageAnnotation.Text -> {
                            val paint = Paint().apply {
                                isAntiAlias = true
                                color = item.color.toArgb()
                                textSize = item.fontSize * sx
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                            val canvas = android.graphics.Canvas(mutableBmp)
                            canvas.drawText(item.text, item.position.x * sx, item.position.y * sy, paint)
                        }
                    }
                }

                // Render vector watermark using ThorVG SVG capabilities if specified
                val watermark = activeWatermark ?: p.watermarkText
                if (!watermark.isNullOrBlank()) {
                    ThorvgEngine.renderWatermark(
                        bitmap = mutableBmp,
                        text = watermark,
                        angle = -35f,
                        opacity = 0.28f,
                        colorHex = "#9E9E9E"
                    )
                }

                // Write back high-res processed image
                FileOutputStream(file).use { out ->
                    mutableBmp.compress(Bitmap.CompressFormat.JPEG, 94, out)
                }
                val thumbFile = File(p.thumbnailPath)
                if (thumbFile.exists()) {
                    val thumb = Bitmap.createScaledBitmap(mutableBmp, 240, (240f * mutableBmp.height / mutableBmp.width).toInt().coerceAtLeast(1), true)
                    FileOutputStream(thumbFile).use { out ->
                        thumb.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    }
                    if (!thumb.isRecycled) thumb.recycle()
                }
                if (!baseBmp.isRecycled) baseBmp.recycle()
                if (!mutableBmp.isRecycled) mutableBmp.recycle()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun compileAndSharePdf() {
        isSaving = true
        coroutineScope.launch {
            saveAnnotatedPages()
            val currentDoc = document
            val targetPages = repository.getPagesListForDocument(docId)
            val options = PdfExportOptions(
                pageSize = exportPageSize,
                quality = exportQuality,
                password = exportPassword.ifBlank { null },
                watermarkText = exportWatermark.ifBlank { null }
            )

            val pdfDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val cleanTitle = currentDoc?.title?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").orEmpty().ifBlank { "Document" }
            val pdfFile = File(pdfDir, "${cleanTitle}.pdf")

            val result = PdfGenerator.generatePdf(context, targetPages, options, pdfFile)
            isSaving = false
            result.fold(
                onSuccess = { file ->
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Document PDF"))
                },
                onFailure = { err ->
                    Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document?.title ?: "Edit PDF",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "Page ${currentPageIndex + 1} of ${pages.size.coerceAtLeast(1)}",
                        color = ScannerGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Undo
                IconButton(
                    onClick = { currentPage?.let { undo(it.id) } },
                    enabled = currentPage?.let { (undoStack[it.id]?.isNotEmpty()) } == true
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (currentPage?.let { (undoStack[it.id]?.isNotEmpty()) } == true) Color.White else Color.Gray.copy(alpha = 0.35f)
                    )
                }

                // Redo
                IconButton(
                    onClick = { currentPage?.let { redo(it.id) } },
                    enabled = currentPage?.let { (redoStack[it.id]?.isNotEmpty()) } == true
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (currentPage?.let { (redoStack[it.id]?.isNotEmpty()) } == true) Color.White else Color.Gray.copy(alpha = 0.35f)
                    )
                }

                // PDF Settings Gear
                IconButton(onClick = { showPdfSettingsSheet = true }) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "PDF Settings",
                        tint = ScannerGreen
                    )
                }

                // Save & Share Button
                Button(
                    onClick = { compileAndSharePdf() },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                // Secondary Controls: Color Palette, Stroke Size, Shapes, Styling, Opacity
                if (activeTool != PdfTool.PAN_ZOOM && activeTool != PdfTool.ERASER) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // Color Palette
                        val colorsPalette = listOf(
                            Color(0xFFFFEB3B), // Fluorescent Yellow
                            Color(0xFF00E676), // Neon Green
                            Color(0xFF29B6F6), // Sky Blue
                            Color(0xFFFF5252), // Coral Red
                            Color(0xFFAB47BC), // Purple
                            Color(0xFF212121), // Charcoal Black
                            Color(0xFFFFFFFF)  // Pure White
                        )

                        // 1. Shapes Selector & Custom Styling (If Shape Tool Active)
                        if (activeTool == PdfTool.SHAPE) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                items(AnnotationShapeType.entries) { st ->
                                    val isSelected = selectedShapeType == st
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) ScannerGreen.copy(alpha = 0.22f) else Color(0xFF222222),
                                        border = BorderStroke(1.dp, if (isSelected) ScannerGreen else Color.DarkGray),
                                        modifier = Modifier.clickable { selectedShapeType = st }
                                    ) {
                                        Text(
                                            text = st.label,
                                            color = if (isSelected) ScannerGreen else Color.LightGray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            // Custom Styling: Gradient Fill & Border Dash
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Fill Type: None, Solid, Linear Grad, Radial Grad
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(
                                        ThorvgEngine.FillType.NONE to "Outline",
                                        ThorvgEngine.FillType.SOLID to "Solid",
                                        ThorvgEngine.FillType.LINEAR_GRADIENT to "Linear",
                                        ThorvgEngine.FillType.RADIAL_GRADIENT to "Radial"
                                    ).forEach { (ft, label) ->
                                        val isSel = selectedFillType == ft
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSel) ScannerGreen.copy(alpha = 0.25f) else Color(0xFF1E1E1E),
                                            border = BorderStroke(1.dp, if (isSel) ScannerGreen else Color(0xFF333333)),
                                            modifier = Modifier.clickable { selectedFillType = ft }
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSel) ScannerGreen else Color.Gray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                // Border Dash: Solid, Dashed, Dotted
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(
                                        ThorvgEngine.DashStyle.SOLID to "Solid",
                                        ThorvgEngine.DashStyle.DASHED to "Dash",
                                        ThorvgEngine.DashStyle.DOTTED to "Dot"
                                    ).forEach { (ds, label) ->
                                        val isSel = selectedDashStyle == ds
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSel) ScannerGreen.copy(alpha = 0.25f) else Color(0xFF1E1E1E),
                                            border = BorderStroke(1.dp, if (isSel) ScannerGreen else Color(0xFF333333)),
                                            modifier = Modifier.clickable { selectedDashStyle = ds }
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSel) ScannerGreen else Color.Gray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Palette, Stroke Size, and Opacity Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Color Palette
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                colorsPalette.forEach { c ->
                                    val isSelected = selectedColor == c
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.DarkGray,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColor = c }
                                    )
                                }
                            }

                            // Opacity & Size Controls
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Alpha", color = Color.Gray, fontSize = 10.sp)
                                Slider(
                                    value = annotationOpacity,
                                    onValueChange = { annotationOpacity = it },
                                    valueRange = 0.1f..1.0f,
                                    modifier = Modifier.width(66.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ScannerGreen,
                                        activeTrackColor = ScannerGreen,
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Size", color = Color.Gray, fontSize = 10.sp)
                                Slider(
                                    value = strokeWidth,
                                    onValueChange = { strokeWidth = it },
                                    valueRange = 2f..36f,
                                    modifier = Modifier.width(72.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ScannerGreen,
                                        activeTrackColor = ScannerGreen,
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)
                }

                // Main Tool Strip (Pan/Zoom, Pencil, Marker, Signature, Shapes, Smudge, Text, Eraser)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PdfToolButton(
                        icon = Icons.Outlined.PanTool,
                        label = "Move",
                        isActive = activeTool == PdfTool.PAN_ZOOM,
                        onClick = { activeTool = PdfTool.PAN_ZOOM }
                    )
                    PdfToolButton(
                        icon = Icons.Default.Edit,
                        label = "Pencil",
                        isActive = activeTool == PdfTool.PENCIL,
                        onClick = {
                            activeTool = PdfTool.PENCIL
                            if (strokeWidth > 16f) strokeWidth = 8f
                        }
                    )
                    PdfToolButton(
                        icon = Icons.Outlined.Draw,
                        label = "Marker",
                        isActive = activeTool == PdfTool.HIGHLIGHTER,
                        onClick = {
                            activeTool = PdfTool.HIGHLIGHTER
                            if (strokeWidth < 14f) strokeWidth = 22f
                        }
                    )
                    PdfToolButton(
                        icon = Icons.Outlined.Gesture,
                        label = "Sign",
                        isActive = activeTool == PdfTool.SIGNATURE,
                        onClick = {
                            activeTool = PdfTool.SIGNATURE
                            showSignatureDialog = true
                        }
                    )
                    PdfToolButton(
                        icon = Icons.Outlined.Category,
                        label = "Shapes",
                        isActive = activeTool == PdfTool.SHAPE,
                        onClick = { activeTool = PdfTool.SHAPE }
                    )
                    PdfToolButton(
                        icon = Icons.Outlined.BlurOn,
                        label = "Smudge",
                        isActive = activeTool == PdfTool.SMUDGE_BLUR,
                        onClick = {
                            activeTool = PdfTool.SMUDGE_BLUR
                            if (strokeWidth < 18f) strokeWidth = 26f
                        }
                    )
                    PdfToolButton(
                        icon = Icons.Default.TextFields,
                        label = "Text",
                        isActive = activeTool == PdfTool.TEXT,
                        onClick = {
                            activeTool = PdfTool.TEXT
                            textTapPosition = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                            showTextDialog = true
                        }
                    )
                    PdfToolButton(
                        icon = Icons.Outlined.CleaningServices,
                        label = "Clear",
                        isActive = activeTool == PdfTool.ERASER,
                        onClick = {
                            currentPage?.let { p ->
                                pushUndo(p.id, emptyList())
                                Toast.makeText(context, "Page cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)

                // Bottom Page Filmstrip Thumbnails
                if (pages.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                            val isSelected = index == currentPageIndex
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        currentPageIndex = index
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 46.dp, height = 62.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF222222))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.5.dp,
                                            color = if (isSelected) ScannerGreen else Color.DarkGray,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                ) {
                                    val file = File(item.processedImagePath)
                                    if (file.exists()) {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = "Page ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${index + 1}",
                                    color = if (isSelected) ScannerGreen else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .onSizeChanged { canvasSize = it }
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = ScannerGreen, modifier = Modifier.align(Alignment.Center))
            } else if (currentPage != null) {
                val pageFile = File(currentPage.processedImagePath)

                // Main Interactive Canvas Area with Zoom/Pan & Real-Time Drawing Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeTool, currentPage.id) {
                            if (activeTool == PdfTool.PAN_ZOOM) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                    if (zoomScale <= 1.01f) {
                                        panOffset = Offset.Zero
                                    } else {
                                        val maxX = (canvasSize.width * (zoomScale - 1f)) / 2f
                                        val maxY = (canvasSize.height * (zoomScale - 1f)) / 2f
                                        panOffset = Offset(
                                            (panOffset.x + pan.x).coerceIn(-maxX, maxX),
                                            (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                                        )
                                    }
                                }
                            } else {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        if (activeTool == PdfTool.SHAPE) {
                                            shapeStartPoint = startOffset
                                            shapeEndPoint = startOffset
                                        } else {
                                            currentDrawingPoints = listOf(startOffset)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        if (activeTool == PdfTool.SHAPE) {
                                            shapeEndPoint = change.position
                                        } else {
                                            currentDrawingPoints = currentDrawingPoints + change.position
                                        }
                                    },
                                    onDragEnd = {
                                        if (activeTool == PdfTool.SHAPE) {
                                            val s = shapeStartPoint
                                            val e = shapeEndPoint
                                            if (s != null && e != null) {
                                                val newShape = PageAnnotation.Shape(
                                                    shapeType = selectedShapeType,
                                                    start = s,
                                                    end = e,
                                                    borderColor = if (selectedShapeType == AnnotationShapeType.FILLED_RECT) Color.Black else selectedColor,
                                                    strokeWidth = strokeWidth,
                                                    borderOpacity = annotationOpacity,
                                                    fillType = when (selectedShapeType) {
                                                        AnnotationShapeType.FILLED_RECT -> ThorvgEngine.FillType.SOLID
                                                        AnnotationShapeType.HIGHLIGHTER_BOX -> ThorvgEngine.FillType.SOLID
                                                        else -> selectedFillType
                                                    },
                                                    fillColor1 = when (selectedShapeType) {
                                                        AnnotationShapeType.FILLED_RECT -> Color.Black
                                                        AnnotationShapeType.HIGHLIGHTER_BOX -> selectedColor.copy(alpha = 0.35f)
                                                        else -> selectedColor
                                                    },
                                                    fillColor2 = gradientEndColor,
                                                    fillOpacity = if (selectedShapeType == AnnotationShapeType.HIGHLIGHTER_BOX) 0.35f * annotationOpacity else annotationOpacity,
                                                    dashStyle = selectedDashStyle,
                                                    cornerRadius = 8f
                                                )
                                                pushUndo(currentPage.id, currentAnnotations + newShape)
                                            }
                                            shapeStartPoint = null
                                            shapeEndPoint = null
                                        } else if (currentDrawingPoints.isNotEmpty()) {
                                            val isHighlighter = activeTool == PdfTool.HIGHLIGHTER
                                            val isSmudge = activeTool == PdfTool.SMUDGE_BLUR
                                            val isSign = activeTool == PdfTool.SIGNATURE
                                            val stroke = PageAnnotation.Stroke(
                                                points = currentDrawingPoints,
                                                color = if (isSmudge) Color.DarkGray else selectedColor,
                                                strokeWidth = strokeWidth,
                                                opacity = annotationOpacity,
                                                isHighlighter = isHighlighter,
                                                isSmudge = isSmudge,
                                                isSignature = isSign
                                            )
                                            pushUndo(currentPage.id, currentAnnotations + stroke)
                                            currentDrawingPoints = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        shapeStartPoint = null
                                        shapeEndPoint = null
                                        currentDrawingPoints = emptyList()
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Page Image
                    if (pageFile.exists()) {
                        AsyncImage(
                            model = pageFile,
                            contentDescription = "Page Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    }

                    // Vector Canvas Drawing Overlay
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // 1. Render all committed annotations for current page
                        currentAnnotations.forEach { item ->
                            when (item) {
                                is PageAnnotation.Stroke -> {
                                    val strokeAlpha = if (item.isHighlighter) 0.40f * item.opacity else if (item.isSmudge) 0.90f else item.opacity
                                    if (item.points.size > 1) {
                                        val p = androidx.compose.ui.graphics.Path()
                                        p.moveTo(item.points[0].x, item.points[0].y)
                                        for (i in 1 until item.points.size) {
                                            val prev = item.points[i - 1]
                                            val curr = item.points[i]
                                            val midX = (prev.x + curr.x) / 2f
                                            val midY = (prev.y + curr.y) / 2f
                                            p.quadraticBezierTo(prev.x, prev.y, midX, midY)
                                        }
                                        p.lineTo(item.points.last().x, item.points.last().y)
                                        drawPath(
                                            path = p,
                                            color = item.color.copy(alpha = strokeAlpha),
                                            style = Stroke(
                                                width = item.strokeWidth,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                                is PageAnnotation.Shape -> {
                                    val left = minOf(item.start.x, item.end.x)
                                    val top = minOf(item.start.y, item.end.y)
                                    val width = (maxOf(item.start.x, item.end.x) - left).coerceAtLeast(1f)
                                    val height = (maxOf(item.start.y, item.end.y) - top).coerceAtLeast(1f)

                                    val pathEffect = when (item.dashStyle) {
                                        ThorvgEngine.DashStyle.SOLID -> null
                                        ThorvgEngine.DashStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                                        ThorvgEngine.DashStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(5f, 6f), 0f)
                                    }

                                    val fillBrush = when (item.fillType) {
                                        ThorvgEngine.FillType.NONE -> null
                                        ThorvgEngine.FillType.SOLID -> Brush.linearGradient(
                                            colors = listOf(item.fillColor1.copy(alpha = item.fillOpacity), item.fillColor1.copy(alpha = item.fillOpacity))
                                        )
                                        ThorvgEngine.FillType.LINEAR_GRADIENT -> Brush.linearGradient(
                                            colors = listOf(item.fillColor1.copy(alpha = item.fillOpacity), item.fillColor2.copy(alpha = item.fillOpacity)),
                                            start = Offset(left, top),
                                            end = Offset(left + width, top + height)
                                        )
                                        ThorvgEngine.FillType.RADIAL_GRADIENT -> Brush.radialGradient(
                                            colors = listOf(item.fillColor1.copy(alpha = item.fillOpacity), item.fillColor2.copy(alpha = item.fillOpacity)),
                                            center = Offset(left + width / 2f, top + height / 2f),
                                            radius = maxOf(width, height) / 2f
                                        )
                                    }

                                    when (item.shapeType) {
                                        AnnotationShapeType.RECTANGLE -> {
                                            if (fillBrush != null) {
                                                drawRoundRect(
                                                    brush = fillBrush,
                                                    topLeft = Offset(left, top),
                                                    size = Size(width, height),
                                                    cornerRadius = CornerRadius(item.cornerRadius)
                                                )
                                            }
                                            drawRoundRect(
                                                color = item.borderColor.copy(alpha = item.borderOpacity),
                                                topLeft = Offset(left, top),
                                                size = Size(width, height),
                                                cornerRadius = CornerRadius(item.cornerRadius),
                                                style = Stroke(width = item.strokeWidth, pathEffect = pathEffect)
                                            )
                                        }
                                        AnnotationShapeType.FILLED_RECT -> {
                                            drawRoundRect(
                                                color = Color.Black.copy(alpha = 0.95f),
                                                topLeft = Offset(left, top),
                                                size = Size(width, height),
                                                cornerRadius = CornerRadius(item.cornerRadius)
                                            )
                                        }
                                        AnnotationShapeType.HIGHLIGHTER_BOX -> {
                                            drawRoundRect(
                                                color = item.borderColor.copy(alpha = 0.35f * item.borderOpacity),
                                                topLeft = Offset(left, top),
                                                size = Size(width, height),
                                                cornerRadius = CornerRadius(item.cornerRadius)
                                            )
                                        }
                                        AnnotationShapeType.CIRCLE -> {
                                            if (fillBrush != null) {
                                                drawOval(
                                                    brush = fillBrush,
                                                    topLeft = Offset(left, top),
                                                    size = Size(width, height)
                                                )
                                            }
                                            drawOval(
                                                color = item.borderColor.copy(alpha = item.borderOpacity),
                                                topLeft = Offset(left, top),
                                                size = Size(width, height),
                                                style = Stroke(width = item.strokeWidth, pathEffect = pathEffect)
                                            )
                                        }
                                        AnnotationShapeType.LINE -> {
                                            drawLine(
                                                color = item.borderColor.copy(alpha = item.borderOpacity),
                                                start = item.start,
                                                end = item.end,
                                                strokeWidth = item.strokeWidth,
                                                cap = StrokeCap.Round,
                                                pathEffect = pathEffect
                                            )
                                        }
                                        AnnotationShapeType.ARROW -> {
                                            drawLine(
                                                color = item.borderColor.copy(alpha = item.borderOpacity),
                                                start = item.start,
                                                end = item.end,
                                                strokeWidth = item.strokeWidth,
                                                cap = StrokeCap.Round,
                                                pathEffect = pathEffect
                                            )
                                            val angle = atan2((item.end.y - item.start.y).toDouble(), (item.end.x - item.start.x).toDouble())
                                            val arrowSize = (item.strokeWidth * 3.5f).coerceIn(20f, 45f)
                                            val x1 = item.end.x - arrowSize * cos(angle - PI / 6).toFloat()
                                            val y1 = item.end.y - arrowSize * sin(angle - PI / 6).toFloat()
                                            val x2 = item.end.x - arrowSize * cos(angle + PI / 6).toFloat()
                                            val y2 = item.end.y - arrowSize * sin(angle + PI / 6).toFloat()
                                            drawLine(color = item.borderColor.copy(alpha = item.borderOpacity), start = item.end, end = Offset(x1, y1), strokeWidth = item.strokeWidth, cap = StrokeCap.Round)
                                            drawLine(color = item.borderColor.copy(alpha = item.borderOpacity), start = item.end, end = Offset(x2, y2), strokeWidth = item.strokeWidth, cap = StrokeCap.Round)
                                        }
                                    }
                                }
                                is PageAnnotation.Text -> {
                                    // Placeholder visual anchor
                                    drawCircle(color = item.color, radius = 6f, center = item.position)
                                }
                            }
                        }

                        // 2. Render live stroke in-progress
                        if (currentDrawingPoints.size > 1) {
                            val p = androidx.compose.ui.graphics.Path()
                            p.moveTo(currentDrawingPoints[0].x, currentDrawingPoints[0].y)
                            for (i in 1 until currentDrawingPoints.size) {
                                val prev = currentDrawingPoints[i - 1]
                                val curr = currentDrawingPoints[i]
                                val midX = (prev.x + curr.x) / 2f
                                val midY = (prev.y + curr.y) / 2f
                                p.quadraticBezierTo(prev.x, prev.y, midX, midY)
                            }
                            p.lineTo(currentDrawingPoints.last().x, currentDrawingPoints.last().y)
                            val isHighlighter = activeTool == PdfTool.HIGHLIGHTER
                            val isSmudge = activeTool == PdfTool.SMUDGE_BLUR
                            drawPath(
                                path = p,
                                color = if (isSmudge) Color.DarkGray else selectedColor.copy(alpha = if (isHighlighter) 0.45f * annotationOpacity else annotationOpacity),
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // 3. Render live shape in-progress
                        val s = shapeStartPoint
                        val e = shapeEndPoint
                        if (s != null && e != null) {
                            val left = minOf(s.x, e.x)
                            val top = minOf(s.y, e.y)
                            val width = (maxOf(s.x, e.x) - left).coerceAtLeast(1f)
                            val height = (maxOf(s.y, e.y) - top).coerceAtLeast(1f)

                            val pathEffect = when (selectedDashStyle) {
                                ThorvgEngine.DashStyle.SOLID -> null
                                ThorvgEngine.DashStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                                ThorvgEngine.DashStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(5f, 6f), 0f)
                            }

                            val liveFillBrush = when (selectedFillType) {
                                ThorvgEngine.FillType.NONE -> null
                                ThorvgEngine.FillType.SOLID -> Brush.linearGradient(
                                    colors = listOf(selectedColor.copy(alpha = annotationOpacity), selectedColor.copy(alpha = annotationOpacity))
                                )
                                ThorvgEngine.FillType.LINEAR_GRADIENT -> Brush.linearGradient(
                                    colors = listOf(selectedColor.copy(alpha = annotationOpacity), gradientEndColor.copy(alpha = annotationOpacity)),
                                    start = Offset(left, top),
                                    end = Offset(left + width, top + height)
                                )
                                ThorvgEngine.FillType.RADIAL_GRADIENT -> Brush.radialGradient(
                                    colors = listOf(selectedColor.copy(alpha = annotationOpacity), gradientEndColor.copy(alpha = annotationOpacity)),
                                    center = Offset(left + width / 2f, top + height / 2f),
                                    radius = maxOf(width, height) / 2f
                                )
                            }

                            when (selectedShapeType) {
                                AnnotationShapeType.RECTANGLE -> {
                                    if (liveFillBrush != null) {
                                        drawRoundRect(
                                            brush = liveFillBrush,
                                            topLeft = Offset(left, top),
                                            size = Size(width, height),
                                            cornerRadius = CornerRadius(8f)
                                        )
                                    }
                                    drawRoundRect(
                                        color = selectedColor.copy(alpha = annotationOpacity),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height),
                                        cornerRadius = CornerRadius(8f),
                                        style = Stroke(width = strokeWidth, pathEffect = pathEffect)
                                    )
                                }
                                AnnotationShapeType.FILLED_RECT -> {
                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = 0.95f),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height),
                                        cornerRadius = CornerRadius(8f)
                                    )
                                }
                                AnnotationShapeType.HIGHLIGHTER_BOX -> {
                                    drawRoundRect(
                                        color = selectedColor.copy(alpha = 0.35f * annotationOpacity),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height),
                                        cornerRadius = CornerRadius(8f)
                                    )
                                }
                                AnnotationShapeType.CIRCLE -> {
                                    if (liveFillBrush != null) {
                                        drawOval(
                                            brush = liveFillBrush,
                                            topLeft = Offset(left, top),
                                            size = Size(width, height)
                                        )
                                    }
                                    drawOval(
                                        color = selectedColor.copy(alpha = annotationOpacity),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height),
                                        style = Stroke(width = strokeWidth, pathEffect = pathEffect)
                                    )
                                }
                                AnnotationShapeType.LINE -> {
                                    drawLine(
                                        color = selectedColor.copy(alpha = annotationOpacity),
                                        start = s,
                                        end = e,
                                        strokeWidth = strokeWidth,
                                        cap = StrokeCap.Round,
                                        pathEffect = pathEffect
                                    )
                                }
                                AnnotationShapeType.ARROW -> {
                                    drawLine(
                                        color = selectedColor.copy(alpha = annotationOpacity),
                                        start = s,
                                        end = e,
                                        strokeWidth = strokeWidth,
                                        cap = StrokeCap.Round,
                                        pathEffect = pathEffect
                                    )
                                    val angle = atan2((e.y - s.y).toDouble(), (e.x - s.x).toDouble())
                                    val arrowSize = (strokeWidth * 3.5f).coerceIn(20f, 45f)
                                    val x1 = e.x - arrowSize * cos(angle - PI / 6).toFloat()
                                    val y1 = e.y - arrowSize * sin(angle - PI / 6).toFloat()
                                    val x2 = e.x - arrowSize * cos(angle + PI / 6).toFloat()
                                    val y2 = e.y - arrowSize * sin(angle + PI / 6).toFloat()
                                    drawLine(color = selectedColor.copy(alpha = annotationOpacity), start = e, end = Offset(x1, y1), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                                    drawLine(color = selectedColor.copy(alpha = annotationOpacity), start = e, end = Offset(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                                }
                            }
                        }
                    }
                }
            }

            // Saving progress overlay
            if (isSaving) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.70f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ScannerGreen)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Compiling final PDF...", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Material 3 Sliding Settings Sheet for PDF Features
    AnimatedVisibility(
        visible = showPdfSettingsSheet,
        enter = fadeIn() + slideInHorizontally { it },
        exit = fadeOut() + slideOutHorizontally { it }
    ) {
        Box(modifier = Modifier.fillMaxSize().zIndex(200f)) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showPdfSettingsSheet = false }
            )

            // Side Sheet Container
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 280.dp, max = 340.dp)
                    .align(Alignment.CenterEnd)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
                color = Color(0xFF181818),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PDF Export Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        IconButton(onClick = { showPdfSettingsSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF282828))

                    // 1. Page Size
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Page Size", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        PdfPageSize.entries.forEach { ps ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { exportPageSize = ps }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = exportPageSize == ps,
                                    onClick = { exportPageSize = ps },
                                    colors = RadioButtonDefaults.colors(selectedColor = ScannerGreen, unselectedColor = Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(ps.title, color = if (exportPageSize == ps) ScannerGreen else Color.LightGray, fontSize = 13.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF282828))

                    // 2. Quality & Compression
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Compression / Quality", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        PdfQuality.entries.forEach { pq ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { exportQuality = pq }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = exportQuality == pq,
                                    onClick = { exportQuality = pq },
                                    colors = RadioButtonDefaults.colors(selectedColor = ScannerGreen, unselectedColor = Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(pq.title, color = if (exportQuality == pq) ScannerGreen else Color.LightGray, fontSize = 13.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF282828))

                    // 3. Watermark Text
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Watermark Text (Optional)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        OutlinedTextField(
                            value = exportWatermark,
                            onValueChange = { exportWatermark = it },
                            placeholder = { Text("e.g. CONFIDENTIAL / COPY", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ScannerGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF282828))

                    // 4. Password Protection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Password Protection (AES 128-bit)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        OutlinedTextField(
                            value = exportPassword,
                            onValueChange = { exportPassword = it },
                            placeholder = { Text("Leave blank for no password", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ScannerGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            showPdfSettingsSheet = false
                            Toast.makeText(context, "PDF settings applied!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    // Text Caption Dialog
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text Annotation", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Type text here...") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ScannerGreen,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            currentPage?.let { p ->
                                val note = PageAnnotation.Text(
                                    text = textInput.trim(),
                                    position = textTapPosition,
                                    color = selectedColor
                                )
                                pushUndo(p.id, currentAnnotations + note)
                            }
                        }
                        textInput = ""
                        showTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Add", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // Digital Signature Capture Dialog
    if (showSignatureDialog) {
        SignatureCaptureDialog(
            onDismissRequest = { showSignatureDialog = false },
            onSignatureCaptured = { capturedStrokes, inkColor ->
                currentPage?.let { p ->
                    if (capturedStrokes.isNotEmpty()) {
                        val allPoints = capturedStrokes.flatten()
                        val minX = allPoints.minOf { it.x }
                        val maxX = allPoints.maxOf { it.x }
                        val minY = allPoints.minOf { it.y }
                        val maxY = allPoints.maxOf { it.y }
                        val origW = (maxX - minX).coerceAtLeast(1f)
                        val origH = (maxY - minY).coerceAtLeast(1f)

                        val targetW = (canvasSize.width * 0.42f).coerceIn(120f, 320f)
                        val scale = targetW / origW
                        val targetH = origH * scale

                        val targetCenterX = canvasSize.width * 0.65f
                        val targetCenterY = canvasSize.height * 0.78f
                        val offsetX = targetCenterX - targetW / 2f
                        val offsetY = targetCenterY - targetH / 2f

                        val newStrokes = capturedStrokes.map { strokeList ->
                            val scaledPoints = strokeList.map { pt ->
                                Offset(
                                    x = (pt.x - minX) * scale + offsetX,
                                    y = (pt.y - minY) * scale + offsetY
                                )
                            }
                            PageAnnotation.Stroke(
                                points = scaledPoints,
                                color = inkColor,
                                strokeWidth = 3.5f,
                                opacity = 1.0f,
                                isSignature = true
                            )
                        }
                        pushUndo(p.id, currentAnnotations + newStrokes)
                        Toast.makeText(context, "Signature placed on page", Toast.LENGTH_SHORT).show()
                    }
                }
                showSignatureDialog = false
            }
        )
    }
}

@Composable
fun SignatureCaptureDialog(
    onDismissRequest: () -> Unit,
    onSignatureCaptured: (List<List<Offset>>, Color) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedInkColor by remember { mutableStateOf(Color(0xFF0D47A1)) } // Default Royal Blue ink
    val signatureColors = listOf(
        Color(0xFF0D47A1) to "Blue",
        Color(0xFF1A1A1A) to "Black",
        Color(0xFF1B5E20) to "Green",
        Color(0xFFB71C1C) to "Red"
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Gesture,
                        contentDescription = null,
                        tint = ScannerGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Digital Signature",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
                TextButton(
                    onClick = {
                        strokes.clear()
                        currentStroke = emptyList()
                    }
                ) {
                    Text("Clear", color = Color(0xFFFF5252), fontSize = 13.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Ink Color Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Ink Color:", color = Color.Gray, fontSize = 12.sp)
                    signatureColors.forEach { (c, _) ->
                        val isSelected = selectedInkColor == c
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) ScannerGreen else Color.DarkGray,
                                    shape = CircleShape
                                )
                                .clickable { selectedInkColor = c }
                        )
                    }
                }

                // Interactive Signature Pad
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF9F9FB))
                        .border(1.dp, Color(0xFF444444), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokes.add(currentStroke)
                                        currentStroke = emptyList()
                                    }
                                },
                                onDragCancel = {
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        // Guideline dashed baseline
                        val baselineY = size.height * 0.75f
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(24f, baselineY),
                            end = Offset(size.width - 24f, baselineY),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )

                        // Render committed strokes with smooth quadratic Bézier
                        strokes.forEach { pts ->
                            if (pts.size > 1) {
                                val p = androidx.compose.ui.graphics.Path()
                                p.moveTo(pts[0].x, pts[0].y)
                                for (i in 1 until pts.size) {
                                    val prev = pts[i - 1]
                                    val curr = pts[i]
                                    val midX = (prev.x + curr.x) / 2f
                                    val midY = (prev.y + curr.y) / 2f
                                    p.quadraticBezierTo(prev.x, prev.y, midX, midY)
                                }
                                p.lineTo(pts.last().x, pts.last().y)
                                drawPath(
                                    path = p,
                                    color = selectedInkColor,
                                    style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }

                        // Render active stroke in progress
                        if (currentStroke.size > 1) {
                            val p = androidx.compose.ui.graphics.Path()
                            p.moveTo(currentStroke[0].x, currentStroke[0].y)
                            for (i in 1 until currentStroke.size) {
                                val prev = currentStroke[i - 1]
                                val curr = currentStroke[i]
                                val midX = (prev.x + curr.x) / 2f
                                val midY = (prev.y + curr.y) / 2f
                                p.quadraticBezierTo(prev.x, prev.y, midX, midY)
                            }
                            p.lineTo(currentStroke.last().x, currentStroke.last().y)
                            drawPath(
                                path = p,
                                color = selectedInkColor,
                                style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    if (strokes.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            text = "✍️ Sign here with your finger or stylus",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (strokes.isNotEmpty()) {
                        onSignatureCaptured(strokes.toList(), selectedInkColor)
                    }
                },
                enabled = strokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScannerGreen,
                    disabledContainerColor = Color.DarkGray
                )
            ) {
                Text("Place on Document", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = Color.LightGray)
            }
        }
    )
}

@Composable
private fun PdfToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) ScannerGreen.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isActive) ScannerGreen else Color.LightGray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isActive) ScannerGreen else Color.LightGray,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
