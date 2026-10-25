package org.openscanner.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.cv.OcrEngine
import org.openscanner.data.model.FolderWithCount
import org.openscanner.ui.detail.components.DetailBottomBar
import org.openscanner.ui.detail.components.DetailTopBar
import org.openscanner.ui.detail.components.PageGridCard
import org.openscanner.ui.detail.dialogs.CollageStudioDialog
import org.openscanner.ui.detail.dialogs.DocumentMoreDialog
import org.openscanner.ui.detail.dialogs.ShareExportBottomSheet
import org.openscanner.ui.pdf.PdfExportDialog
import org.openscanner.ui.signature.SignatureDialog
import org.openscanner.ui.theme.ScannerTheme
import java.io.File

@Composable
fun DocumentDetailScreen(
    docId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (docId: Long) -> Unit,
    onNavigateToViewer: (pageId: Long) -> Unit,
    viewModel: DocumentDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = ScannerTheme.colors
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(docId) {
        viewModel.load(docId)
    }

    val document by viewModel.document.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()

    var showPdfDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var capturedSignatureBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showCollageDialog by remember { mutableStateOf(false) }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var showShareExportSheet by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }
    var showOcrResultDialog by remember { mutableStateOf(false) }
    var extractedOcrText by remember { mutableStateOf("") }
    var isOcrExtracting by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            DetailTopBar(
                title = document?.title ?: "Document",
                onNavigateBack = onNavigateBack,
                onRenameClick = {
                    renameText = document?.title.orEmpty()
                    showRenameDialog = true
                },
                onExportPdfClick = {
                    if (pages.isNotEmpty()) {
                        showPdfDialog = true
                    } else {
                        Toast.makeText(context, "No pages to export", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        },
        bottomBar = {
            Box {
                DetailBottomBar(
                    onAddClick = { onNavigateToCamera(docId) },
                    onCollageClick = {
                        if (pages.size >= 2) {
                            showCollageDialog = true
                        } else {
                            Toast.makeText(context, "Scan at least 2 pages for collage", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShareClick = {
                        if (pages.isNotEmpty()) {
                            showShareExportSheet = true
                        } else {
                            Toast.makeText(context, "No pages to share", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEditPdfClick = {
                        if (pages.isNotEmpty()) {
                            showPdfDialog = true
                        } else {
                            Toast.makeText(context, "Add pages to edit PDF", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMoreClick = { showMoreDialog = true }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (pages.isEmpty()) {
                // Empty state for document
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = colors.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = colors.primary, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Pages in Document",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap 'Add' below to scan pages into this document",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { onNavigateToCamera(docId) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Page")
                    }
                }
            } else {
                // 2-column page grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pages, key = { it.id }) { page ->
                        PageGridCard(
                            page = page,
                            onClick = { onNavigateToViewer(page.id) }
                        )
                    }
                }
            }

            // PDF Generation Progress Overlay
            if (isGeneratingPdf) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.background.copy(alpha = 0.75f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = colors.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Compiling PDF document...", color = colors.textPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameDocument(renameText.trim())
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Document?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove all pages from internal storage.", color = colors.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument {
                            showDeleteConfirmDialog = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE3F3F))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    if (showMoveFolderDialog) {
        AlertDialog(
            onDismissRequest = { showMoveFolderDialog = false },
            title = { Text("Move to Folder", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = colors.surfaceSecondary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveToFolder(-1L)
                                showMoveFolderDialog = false
                            }
                    ) {
                        Text("Root (No folder)", color = colors.textPrimary, modifier = Modifier.padding(14.dp))
                    }

                    folders.forEach { f ->
                        Surface(
                            color = colors.surfaceSecondary,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveToFolder(f.id)
                                    showMoveFolderDialog = false
                                }
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = colors.folderYellow)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(f.name, color = colors.textPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveFolderDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    if (showPdfDialog) {
        PdfExportDialog(
            onDismiss = { showPdfDialog = false },
            onSaveToDownloads = { options ->
                viewModel.savePdfToDownloads(context, options)
                showPdfDialog = false
            },
            onShare = { options ->
                viewModel.exportAndSharePdf(context, options)
                showPdfDialog = false
            }
        )
    }

    if (showCollageDialog) {
        CollageStudioDialog(
            pages = pages,
            onDismiss = { showCollageDialog = false },
            onGenerateCollage = { layout, pageIds, watermarkConfig, transforms ->
                viewModel.createCollage(layout, pageIds, watermarkConfig, transforms) {
                    showCollageDialog = false
                    Toast.makeText(context, "Collage page generated!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showSignatureDialog) {
        SignatureDialog(
            onDismiss = { showSignatureDialog = false },
            onSignatureCaptured = { bmp ->
                showSignatureDialog = false
                capturedSignatureBitmap = bmp
            }
        )
    }

    capturedSignatureBitmap?.let { sigBmp ->
        val targetPage = pages.firstOrNull()
        if (targetPage != null) {
            org.openscanner.ui.signature.SignaturePlacementDialog(
                page = targetPage,
                signatureBitmap = sigBmp,
                onDismiss = { capturedSignatureBitmap = null },
                onStamp = { relX, relY, relW, relH ->
                    viewModel.stampSignature(targetPage.id, sigBmp, relX, relY, relW, relH) {
                        capturedSignatureBitmap = null
                        Toast.makeText(context, "Signature applied to page!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            Toast.makeText(context, "No pages available to sign", Toast.LENGTH_SHORT).show()
            capturedSignatureBitmap = null
        }
    }

    if (showShareExportSheet) {
        ShareExportBottomSheet(
            pages = pages,
            onDismiss = { showShareExportSheet = false },
            onSharePdf = { showPdfDialog = true },
            onShareJpg = { viewModel.shareAsImages(context) },
            onShareLongImage = { viewModel.shareLongImage(context) },
            onExtractText = {
                isOcrExtracting = true
                coroutineScope.launch(Dispatchers.IO) {
                    val fullText = StringBuilder()
                    for (p in pages) {
                        val bmp = BitmapFactory.decodeFile(p.processedImagePath)
                        if (bmp != null) {
                            try {
                                val recognized = OcrEngine.recognizeText(bmp)
                                if (recognized.isNotBlank()) {
                                    fullText.appendLine("--- Page ${p.pageIndex + 1} ---")
                                    fullText.appendLine(recognized)
                                    fullText.appendLine()
                                }
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        isOcrExtracting = false
                        extractedOcrText = fullText.toString().ifBlank { "No text recognized in pages." }
                        showOcrResultDialog = true
                    }
                }
            },
            onSplitPdf = {
                Toast.makeText(context, "Pages are managed independently in OpenScanner.", Toast.LENGTH_SHORT).show()
            },
            onSaveToGallery = { viewModel.savePagesToGallery(context) },
            onEmailToMyself = { viewModel.emailToMyself(context) }
        )
    }

    if (showMoreDialog) {
        DocumentMoreDialog(
            onDismiss = { showMoreDialog = false },
            onBatchEditClick = {
                Toast.makeText(context, "Batch edit active.", Toast.LENGTH_SHORT).show()
            },
            onSetTagClick = {
                Toast.makeText(context, "Tag management.", Toast.LENGTH_SHORT).show()
            },
            onPdfPasswordClick = {
                showPdfDialog = true
            },
            onMoveToFolderClick = {
                showMoveFolderDialog = true
            },
            onRenameClick = {
                renameText = document?.title.orEmpty()
                showRenameDialog = true
            },
            onDeleteClick = {
                showDeleteConfirmDialog = true
            },
            onSignatureClick = {
                showSignatureDialog = true
            }
        )
    }

    if (showOcrResultDialog) {
        AlertDialog(
            onDismissRequest = { showOcrResultDialog = false },
            title = {
                Text(
                    text = "Extracted Text (Offline OCR)",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 350.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = extractedOcrText,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Scanned Text", extractedOcrText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        showOcrResultDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Copy Text", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOcrResultDialog = false }) {
                    Text("Close", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }
}
