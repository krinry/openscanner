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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.openscanner.cv.CollageLayout
import org.openscanner.data.model.Folder
import org.openscanner.data.model.Page
import org.openscanner.ui.pdf.PdfExportDialog
import org.openscanner.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val DarkBg = Color(0xFF000000)
private val DarkCard = Color(0xFF141414)
private val DarkCardBorder = Color(0xFF222222)
private val DarkTextPrimary = Color(0xFFFFFFFF)
private val DarkTextSecondary = Color(0xFF8E8E93)
private val ScannerBrandGreen = Color(0xFF00BA66)
private val ScannerFolderYellow = Color(0xFFF0AF3A)

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
    val folders by viewModel.folders.collectAsState()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()

    var showPdfDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showCollageDialog by remember { mutableStateOf(false) }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                renameText = document?.title ?: ""
                                showRenameDialog = true
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = document?.title ?: "Document",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = DarkTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = DarkTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showPdfDialog = true },
                        enabled = pages.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Outlined.PictureAsPdf,
                            contentDescription = "PDF Preview & Export",
                            tint = DarkTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        bottomBar = {
            // 5-Action Bottom Navigation Bar: [Add] [Collage] [Share] [Edit PDF] [More]
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkBg,
                tonalElevation = 8.dp,
                border = BorderStroke(0.5.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Add (Camera with plus)
                    ScannerDetailAction(
                        icon = Icons.Default.AddAPhoto,
                        label = "Add",
                        onClick = { onNavigateToCamera(docId) }
                    )

                    // 2. Collage (Grid canvas tool)
                    ScannerDetailAction(
                        icon = Icons.Default.DashboardCustomize,
                        label = "Collage",
                        onClick = {
                            if (pages.size >= 2) {
                                showCollageDialog = true
                            } else {
                                Toast.makeText(context, "Scan at least 2 pages for collage", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // 3. Share
                    ScannerDetailAction(
                        icon = Icons.Outlined.Share,
                        label = "Share",
                        onClick = {
                            if (pages.isNotEmpty()) {
                                showShareSheet = true
                            } else {
                                Toast.makeText(context, "No pages to share", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // 4. Edit PDF
                    ScannerDetailAction(
                        icon = Icons.Default.PictureAsPdf,
                        label = "Edit PDF",
                        onClick = {
                            if (pages.isNotEmpty()) {
                                showPdfDialog = true
                            } else {
                                Toast.makeText(context, "Add pages to edit PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // 5. More (3-dots options)
                    Box {
                        ScannerDetailAction(
                            icon = Icons.Default.MoreVert,
                            label = "More",
                            onClick = { showMoreMenu = true }
                        )

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(DarkCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Move to Folder", color = DarkTextPrimary) },
                                onClick = {
                                    showMoreMenu = false
                                    showMoveFolderDialog = true
                                },
                                leadingIcon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null, tint = DarkTextSecondary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sign Document", color = DarkTextPrimary) },
                                onClick = {
                                    showMoreMenu = false
                                    if (pages.isNotEmpty()) {
                                        showSignatureDialog = true
                                    } else {
                                        Toast.makeText(context, "Add a page before signing", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null, tint = DarkTextSecondary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename", color = DarkTextPrimary) },
                                onClick = {
                                    showMoreMenu = false
                                    renameText = document?.title ?: ""
                                    showRenameDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = DarkTextSecondary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Document", color = Color(0xFFEE3F3F)) },
                                onClick = {
                                    showMoreMenu = false
                                    showDeleteConfirmDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEE3F3F)) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBg)
        ) {
            if (pages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            tint = DarkTextSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No pages in this document.",
                            color = DarkTextSecondary,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { onNavigateToCamera(docId) },
                            colors = ButtonDefaults.buttonColors(containerColor = ScannerBrandGreen),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan First Page")
                        }
                    }
                }
            } else {
                // 2-Column Page Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pages, key = { it.id }) { page ->
                        ScannerPageCard(
                            page = page,
                            onClick = { onNavigateToViewer(page.id) }
                        )
                    }
                }
            }

            if (isGeneratingPdf) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = ScannerBrandGreen, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Processing Document...", color = DarkTextPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs & Sheets ---

    // 1. PDF Export / Edit PDF Dialog
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

    // 2. Collage Studio Dialog
    if (showCollageDialog) {
        ScannerCollageStudioDialog(
            pages = pages,
            onDismiss = { showCollageDialog = false },
            onGenerateCollage = { layout, pageIds ->
                showCollageDialog = false
                viewModel.createCollage(layout, pageIds) {
                    Toast.makeText(context, "Collage sheet added to document!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 3. Share Options Sheet
    if (showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
            containerColor = DarkCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Share Document",
                    color = DarkTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkCardBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showShareSheet = false
                            showPdfDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Share as PDF", color = DarkTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Clean vectorized PDF document", color = DarkTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkCardBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showShareSheet = false
                            viewModel.shareAsImages(context)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF2979FF), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Share as Images (JPEG)", color = DarkTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("All ${pages.size} pages as high quality photos", color = DarkTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // 4. Move to Folder Dialog
    if (showMoveFolderDialog) {
        AlertDialog(
            onDismissRequest = { showMoveFolderDialog = false },
            title = { Text("Move to Folder", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = DarkCardBorder,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveToFolder(-1L)
                                showMoveFolderDialog = false
                                Toast.makeText(context, "Moved to Root", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Text("Root (All docs)", color = DarkTextPrimary, modifier = Modifier.padding(14.dp))
                    }

                    folders.forEach { f ->
                        Surface(
                            color = DarkCardBorder,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveToFolder(f.id)
                                    showMoveFolderDialog = false
                                    Toast.makeText(context, "Moved to ${f.name}", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = ScannerFolderYellow)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(f.name, color = DarkTextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveFolderDialog = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }

    // 5. Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        focusedBorderColor = ScannerBrandGreen,
                        unfocusedBorderColor = DarkCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameDocument(renameText)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerBrandGreen)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }

    // 6. Delete Confirm Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Document?", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete this document and all its scanned pages? This action cannot be undone.",
                    color = DarkTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteDocument(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE3F3F))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }

    // 7. Signature Dialog
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

/**
 * Clean 2-column page thumbnail card with high-contrast leading-zero badge ("01", "02")
 */
@Composable
fun ScannerPageCard(
    page: Page,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.5.dp, Color(0xFF333333))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val pageFile = File(page.processedImagePath)
            if (pageFile.exists()) {
                AsyncImage(
                    model = pageFile,
                    contentDescription = "Page ${page.pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Translucent bottom strip with formatted page number "01", "02", etc.
            val formattedPageNum = String.format("%02d", page.pageIndex + 1)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Text(
                    text = formattedPageNum,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun ScannerDetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = DarkTextPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, color = DarkTextPrimary, fontWeight = FontWeight.Normal)
    }
}

/**
 * Collage Studio Dialog: Combines 2, 4, or 6 pages into single A4 canvas
 */
@Composable
fun ScannerCollageStudioDialog(
    pages: List<Page>,
    onDismiss: () -> Unit,
    onGenerateCollage: (CollageLayout, List<Long>) -> Unit
) {
    var selectedLayout by remember { mutableStateOf(CollageLayout.TWO_VERTICAL) }
    var selectedPageIds by remember {
        mutableStateOf(pages.take(2).map { it.id }.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = ScannerBrandGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Collage Studio", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Choose Collage Layout", color = DarkTextSecondary, fontSize = 13.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CollageLayout.entries.forEach { layout ->
                        val isSelected = layout == selectedLayout
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedLayout = layout
                                val count = layout.maxPages
                                selectedPageIds = pages.take(count).map { it.id }.toSet()
                            },
                            label = { Text(layout.title, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ScannerBrandGreen,
                                selectedLabelColor = Color.White,
                                containerColor = DarkCardBorder,
                                labelColor = DarkTextPrimary
                            )
                        )
                    }
                }

                Text("Select up to ${selectedLayout.maxPages} pages:", color = DarkTextSecondary, fontSize = 13.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pages.take(6).forEach { page ->
                        val isSelected = selectedPageIds.contains(page.id)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) ScannerBrandGreen.copy(alpha = 0.25f) else DarkCardBorder,
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) ScannerBrandGreen else Color.Transparent
                            ),
                            modifier = Modifier
                                .size(50.dp, 68.dp)
                                .clickable {
                                    val current = selectedPageIds.toMutableSet()
                                    if (current.contains(page.id)) {
                                        current.remove(page.id)
                                    } else {
                                        if (current.size < selectedLayout.maxPages) {
                                            current.add(page.id)
                                        }
                                    }
                                    selectedPageIds = current
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = String.format("%02d", page.pageIndex + 1),
                                    color = if (isSelected) ScannerBrandGreen else DarkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedPageIds.isNotEmpty()) {
                        onGenerateCollage(selectedLayout, selectedPageIds.toList())
                    }
                },
                enabled = selectedPageIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ScannerBrandGreen)
            ) {
                Text("Create Collage")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkTextSecondary)
            }
        },
        containerColor = DarkCard
    )
}
