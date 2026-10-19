package org.openscanner.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.data.model.Page
import org.openscanner.ui.theme.ScannerGreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageViewerScreen(
    pageId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf<Page?>(null) }
    var showOcrBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(pageId) {
        withContext(Dispatchers.IO) {
            val db = (context.applicationContext as OpenScannerApp).database
            val p = db.pageDao().getPageById(pageId)
            page = p
        }
    }

    // Zoom and pan state
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + offsetChange else Offset.Zero
    }

    var showSignatureDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var imageReloadKey by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page?.let { "Page ${it.pageIndex + 1}" } ?: "Page", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showSignatureDialog = true }) {
                        Icon(
                            Icons.Default.Draw,
                            contentDescription = "Add Signature",
                            tint = Color.White
                        )
                    }
                    Button(
                        onClick = { showOcrBottomSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("OCR Text", fontSize = 12.sp, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            page?.let { p ->
                AsyncImage(
                    model = File(p.processedImagePath),
                    contentDescription = "Page Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = transformState)
                )
            } ?: CircularProgressIndicator(color = ScannerGreen)
        }
    }

    if (showSignatureDialog) {
        org.openscanner.ui.signature.SignatureDialog(
            onDismiss = { showSignatureDialog = false },
            onSignatureCaptured = { sigBmp ->
                showSignatureDialog = false
                val p = page ?: return@SignatureDialog
                coroutineScope.launch(Dispatchers.IO) {
                    val app = context.applicationContext as OpenScannerApp
                    val repo = org.openscanner.data.repository.ScannerRepository(
                        context = context,
                        documentDao = app.database.documentDao(),
                        pageDao = app.database.pageDao(),
                        folderDao = app.database.folderDao()
                    )
                    repo.stampSignatureOnPage(p.id, sigBmp)
                    withContext(Dispatchers.Main) {
                        imageReloadKey++
                        Toast.makeText(context, "Signature applied to document!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showOcrBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOcrBottomSheet = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recognized Text (OCR)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = {
                        val text = page?.ocrText.orEmpty()
                        if (text.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Scanned Text", text))
                            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = ScannerGreen)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                val ocrText = page?.ocrText.orEmpty()
                if (ocrText.isNotBlank()) {
                    Text(
                        text = ocrText,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                } else {
                    Text(
                        text = "No text recognized on this page.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
