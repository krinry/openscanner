package org.openscanner.ui.detail

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.openscanner.data.model.Page
import org.openscanner.ui.pdf.PdfExportDialog
import org.openscanner.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    docId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (docId: Long) -> Unit,
    onNavigateToViewer: (pageId: Long) -> Unit,
    viewModel: DocumentDetailViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(docId) {
        viewModel.load(docId)
    }

    val document by viewModel.document.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()

    var showPdfDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable {
                                renameText = document?.title ?: ""
                                showRenameDialog = true
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = document?.title ?: "Document",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = OkenTextPrimary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Rename",
                                tint = OkenTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        document?.let {
                            Text(
                                text = "${pages.size} Pages • ${dateFormat.format(Date(it.modifiedTime))}",
                                fontSize = 11.sp,
                                color = OkenTextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OkenTextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showPdfDialog = true }, enabled = pages.isNotEmpty()) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Export", tint = OkenGreen)
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Document", tint = Color(0xFFD32F2F))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                tonalElevation = 8.dp,
                border = BorderStroke(0.5.dp, OkenBorderLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailBottomAction(
                        icon = Icons.Default.AddAPhoto,
                        label = "Add Page",
                        color = OkenGreen,
                        onClick = { onNavigateToCamera(docId) }
                    )
                    DetailBottomAction(
                        icon = Icons.Default.Draw,
                        label = "Sign",
                        color = OkenTextPrimary,
                        onClick = {
                            if (pages.isNotEmpty()) {
                                showSignatureDialog = true
                            } else {
                                Toast.makeText(context, "Add a page before signing", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    DetailBottomAction(
                        icon = Icons.Default.PictureAsPdf,
                        label = "Save PDF",
                        color = OkenTextPrimary,
                        onClick = { showPdfDialog = true }
                    )
                    DetailBottomAction(
                        icon = Icons.Default.Share,
                        label = "Share",
                        color = OkenTextPrimary,
                        onClick = { showPdfDialog = true }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(OkenBgLight)
        ) {
            if (pages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = OkenTextMuted, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No pages in this document.", color = OkenTextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onNavigateToCamera(docId) },
                            colors = ButtonDefaults.buttonColors(containerColor = OkenGreen)
                        ) {
                            Text("Scan Page")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pages, key = { it.id }) { page ->
                        PageThumbnailCard(
                            page = page,
                            onClick = { onNavigateToViewer(page.id) },
                            onDelete = { viewModel.deletePage(page) }
                        )
                    }
                }
            }

            if (isGeneratingPdf) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = OkenGreen, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Processing Document PDF...", color = OkenTextPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPdfDialog) {
        PdfExportDialog(
            onDismiss = { showPdfDialog = false },
            onSaveToDownloads = { options ->
                showPdfDialog = false
                viewModel.savePdfToDownloads(context, options)
            },
            onShare = { options ->
                showPdfDialog = false
                viewModel.exportAndSharePdf(context, options)
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Document Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameDocument(renameText)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OkenGreen)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Document?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this document and all its scanned pages? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteDocument(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSignatureDialog) {
        org.openscanner.ui.signature.SignatureDialog(
            onDismiss = { showSignatureDialog = false },
            onSignatureCaptured = { sigBmp ->
                showSignatureDialog = false
                val firstPage = pages.firstOrNull() ?: return@SignatureDialog
                viewModel.stampSignature(firstPage.id, sigBmp) {
                    Toast.makeText(context, "Signature applied to page!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun PageThumbnailCard(
    page: Page,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = File(page.processedImagePath),
                contentDescription = "Page ${page.pageIndex + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Page Number Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Text(
                    text = "${page.pageIndex + 1}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            // Quick Delete Button
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clickable(onClick = onDelete),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete Page",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DetailBottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}
