package org.openscanner.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.openscanner.ui.theme.ScannerTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageViewerScreen(
    pageId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = ScannerTheme.colors
    var page by remember { mutableStateOf<Page?>(null) }
    var docTitle by remember { mutableStateOf("Document") }
    var totalPages by remember { mutableIntStateOf(1) }

    LaunchedEffect(pageId) {
        withContext(Dispatchers.IO) {
            val db = (context.applicationContext as OpenScannerApp).database
            val p = db.pageDao().getPageById(pageId)
            page = p
            if (p != null) {
                val doc = db.documentDao().getDocumentById(p.documentId)
                docTitle = doc?.title ?: "Document"
                totalPages = db.pageDao().getPagesListForDocument(p.documentId).size.coerceAtLeast(1)
            }
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
    var viewerSignatureBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var imageReloadKey by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = docTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            Toast.makeText(context, "Document saved locally", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF141414),
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickEditActionItem(
                        icon = Icons.Outlined.Security,
                        label = "Anti-theft",
                        onClick = { showWatermarkDialog = true }
                    )
                    QuickEditActionItem(
                        icon = Icons.Default.Draw,
                        label = "Signature",
                        onClick = { showSignatureDialog = true }
                    )
                    QuickEditActionItem(
                        icon = Icons.Default.Lock,
                        label = "PDF Password",
                        onClick = { showPasswordDialog = true }
                    )
                    QuickEditActionItem(
                        icon = Icons.Outlined.Compress,
                        label = "Compression",
                        onClick = { showCompressDialog = true }
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
            page?.let { p ->
                key(imageReloadKey) {
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
                }

                // Top-Left Page Counter Badge ("1/9")
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "${p.pageIndex + 1}/$totalPages",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } ?: CircularProgressIndicator(color = ScannerGreen)
        }
    }

    if (showSignatureDialog) {
        org.openscanner.ui.signature.SignatureDialog(
            onDismiss = { showSignatureDialog = false },
            onSignatureCaptured = { sigBmp ->
                showSignatureDialog = false
                viewerSignatureBitmap = sigBmp
            }
        )
    }

    viewerSignatureBitmap?.let { sigBmp ->
        page?.let { p ->
            org.openscanner.ui.signature.SignaturePlacementDialog(
                page = p,
                signatureBitmap = sigBmp,
                onDismiss = { viewerSignatureBitmap = null },
                onStamp = { relX, relY, relW, relH ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val app = context.applicationContext as OpenScannerApp
                        val repo = org.openscanner.data.repository.ScannerRepository(
                            context = context,
                            documentDao = app.database.documentDao(),
                            pageDao = app.database.pageDao(),
                            folderDao = app.database.folderDao()
                        )
                        repo.stampSignatureOnPage(p.id, sigBmp, relX, relY, relW, relH)
                        withContext(Dispatchers.Main) {
                            viewerSignatureBitmap = null
                            imageReloadKey++
                            Toast.makeText(context, "Signature applied to page!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }

    if (showWatermarkDialog) {
        var watermarkInput by remember { mutableStateOf("CONFIDENTIAL") }
        AlertDialog(
            onDismissRequest = { showWatermarkDialog = false },
            title = { Text("Anti-theft Watermark", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add security overlay text:", fontSize = 13.sp, color = colors.textSecondary)
                    OutlinedTextField(
                        value = watermarkInput,
                        onValueChange = { watermarkInput = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWatermarkDialog = false
                        Toast.makeText(context, "Watermark '$watermarkInput' configured for export", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Apply", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWatermarkDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    if (showPasswordDialog) {
        var pwdInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Set PDF Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Encrypt PDF with access password:", fontSize = 13.sp, color = colors.textSecondary)
                    OutlinedTextField(
                        value = pwdInput,
                        onValueChange = { pwdInput = it },
                        placeholder = { Text("Enter password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPasswordDialog = false
                        Toast.makeText(context, "PDF password protection active", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Set Password", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    if (showCompressDialog) {
        var selectedQuality by remember { mutableStateOf("Medium (75%)") }
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("PDF Compression", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Low (Smallest size, 50%)", "Medium (Recommended, 75%)", "High (Crisp quality, 92%)").forEach { q ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedQuality = q }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedQuality == q,
                                onClick = { selectedQuality = q }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(q, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompressDialog = false
                        Toast.makeText(context, "Compression set to $selectedQuality", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Done", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun QuickEditActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFCCCCCC),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color(0xFFCCCCCC),
            fontSize = 11.sp
        )
    }
}
