package org.openscanner.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openscanner.data.model.Document
import org.openscanner.data.model.Folder
import org.openscanner.ui.home.components.*
import org.openscanner.ui.home.dialogs.*
import org.openscanner.ui.pdf.PdfExportDialog
import org.openscanner.ui.theme.ScannerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCamera: (docId: Long, mode: String) -> Unit,
    onNavigateToDetail: (docId: Long) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = ScannerTheme.colors

    val documents by viewModel.documents.collectAsState()
    val foldersWithCount by viewModel.foldersWithCount.collectAsState()
    val allFoldersWithCount by viewModel.allFoldersWithCount.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedDocIds by viewModel.selectedDocIds.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Documents, 1: Tools
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var docToRename by remember { mutableStateOf<Document?>(null) }
    var docToMove by remember { mutableStateOf<Document?>(null) }
    var showMoveCopyDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showBatchPdfDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    // System Back button navigation
    BackHandler(enabled = isSelectMode || isSearchExpanded || selectedFolder != null) {
        if (isSelectMode) {
            viewModel.clearSelection()
        } else if (isSearchExpanded) {
            isSearchExpanded = false
            viewModel.onSearchQueryChanged("")
        } else if (selectedFolder != null) {
            viewModel.navigateUp()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isImporting = true
            Toast.makeText(context, "Importing ${uris.size} photos...", Toast.LENGTH_SHORT).show()
            viewModel.importImagesFromGallery(uris) { docId ->
                isImporting = false
                onNavigateToDetail(docId)
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            Toast.makeText(context, "Importing PDF document...", Toast.LENGTH_SHORT).show()
            viewModel.importPdfDocument(uri) { docId ->
                isImporting = false
                onNavigateToDetail(docId)
            }
        }
    }

    val totalDocCount = remember(documents, allFoldersWithCount, selectedFolder) {
        if (selectedFolder != null) {
            documents.size
        } else {
            val folderDocs = allFoldersWithCount.sumOf { it.docCount }
            val rootDocs = documents.count { it.folderId <= 0 }
            rootDocs + folderDocs
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            HomeTopBar(
                isSelectMode = isSelectMode,
                selectedDocCount = selectedDocIds.size,
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAllDocs() },
                searchQuery = searchQuery,
                onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                isSearchExpanded = isSearchExpanded,
                onToggleSearch = { isSearchExpanded = !isSearchExpanded },
                selectedFolder = selectedFolder,
                totalDocCount = totalDocCount,
                foldersWithCount = allFoldersWithCount,
                onSelectFolder = { viewModel.selectFolder(it) },
                sortOrder = sortOrder,
                onSortOrderChanged = { viewModel.setSortOrder(it) },
                isGridView = isGridView,
                onToggleGridView = { viewModel.toggleGridView() },
                onEnterSelectMode = { viewModel.toggleSelectMode() },
                onCreateFolderClick = { showNewFolderDialog = true },
                onNavigateUp = { viewModel.navigateUp() },
                onProfileClick = onNavigateToProfile,
                showSubHeader = selectedTab == 0
            )
        },
        bottomBar = {
            if (isSelectMode) {
                BatchSelectionBottomBar(
                    selectedCount = selectedDocIds.size,
                    onShare = {
                        if (selectedDocIds.isNotEmpty()) {
                            showBatchPdfDialog = true
                        }
                    },
                    onMoveCopy = { showMoveCopyDialog = true },
                    onRename = {
                        val singleId = selectedDocIds.firstOrNull()
                        if (singleId != null) {
                            val doc = documents.find { it.id == singleId }
                            if (doc != null) {
                                docToRename = doc
                            }
                        }
                    },
                    onMerge = { showMergeDialog = true },
                    onDeleteSelected = { viewModel.batchDeleteSelected() },
                    onMore = { showMoreMenu = true }
                )
            } else {
                HomeBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onCameraClick = { onNavigateToCamera(-1L, "Document") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedTab == 1) {
                HomeToolsTab(
                    onScanIdCard = { onNavigateToCamera(-1L, "ID Card") },
                    onScanDocs = { onNavigateToCamera(-1L, "Document") },
                    onScanBook = { onNavigateToCamera(-1L, "Book") },
                    onScanQr = { onNavigateToCamera(-1L, "QR") },
                    onToText = { onNavigateToCamera(-1L, "Document") },
                    onImportFiles = { showImportDialog = true },
                    onCollageImages = {
                        val firstDoc = documents.firstOrNull()
                        if (firstDoc != null) onNavigateToDetail(firstDoc.id)
                        else Toast.makeText(context, "Scan or import documents first to create collage", Toast.LENGTH_SHORT).show()
                    },
                    onPdfWatermark = {
                        val firstDoc = documents.firstOrNull()
                        if (firstDoc != null) onNavigateToDetail(firstDoc.id)
                        else Toast.makeText(context, "Scan or import documents first to add watermark", Toast.LENGTH_SHORT).show()
                    },
                    onPdfMerge = { showBatchPdfDialog = true },
                    onPdfCompression = {
                        val firstDoc = documents.firstOrNull()
                        if (firstDoc != null) onNavigateToDetail(firstDoc.id)
                        else Toast.makeText(context, "Scan or import documents first to compress", Toast.LENGTH_SHORT).show()
                    },
                    onPdfSignature = { onNavigateToCamera(-1L, "Signature") },
                    onPdfPassword = {
                        val firstDoc = documents.firstOrNull()
                        if (firstDoc != null) onNavigateToDetail(firstDoc.id)
                        else Toast.makeText(context, "Scan or import documents first to protect with password", Toast.LENGTH_SHORT).show()
                    },
                    onPageReorder = {
                        val firstDoc = documents.firstOrNull()
                        if (firstDoc != null) onNavigateToDetail(firstDoc.id)
                        else Toast.makeText(context, "Scan or import documents first to reorder pages", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                // Documents Tab Content
                if (documents.isEmpty() && foldersWithCount.isEmpty()) {
                    // Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = colors.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (selectedFolder != null) Icons.Default.FolderOpen else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No results found"
                            else if (selectedFolder != null) "Folder is Empty"
                            else "No Scanned Documents",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try a different search keyword"
                            else if (selectedFolder != null) "Add documents or create subfolders inside '${selectedFolder?.name}'"
                            else "Tap the camera shutter to scan your first page",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (selectedFolder != null) {
                                    OutlinedButton(
                                        onClick = { showNewFolderDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                                    ) {
                                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("New Subfolder")
                                    }
                                }
                                Button(
                                    onClick = { onNavigateToCamera(-1L, "Document") },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start Scanning")
                                }
                            }
                        }
                    }
                } else {
                    // Content with Folder Section & Documents
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Folders section inside grid (top level or subfolders)
                            if (foldersWithCount.isNotEmpty() || selectedFolder != null) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                    FolderSection(
                                        foldersWithCount = foldersWithCount,
                                        isGridView = true,
                                        isInsideFolder = selectedFolder != null,
                                        onSelectFolder = { viewModel.selectFolder(it) },
                                        onRenameFolder = { folderToRename = it },
                                        onDeleteFolder = { viewModel.deleteFolder(it) },
                                        onCreateFolderClick = { showNewFolderDialog = true }
                                    )
                                }
                            }

                            // Document cards
                            items(documents, key = { it.id }) { doc ->
                                ScannerDocumentGridCard(
                                    document = doc,
                                    isSelected = selectedDocIds.contains(doc.id),
                                    isSelectMode = isSelectMode,
                                    onClick = {
                                        if (isSelectMode) {
                                            viewModel.toggleSelectDoc(doc.id)
                                        } else {
                                            onNavigateToDetail(doc.id)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelectDoc(doc.id)
                                    },
                                    onDelete = { viewModel.deleteDocument(doc) },
                                    onRename = { docToRename = doc },
                                    onMoveToFolder = { docToMove = doc }
                                )
                            }
                        }
                    } else {
                        // List View
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Folders section inside list (top level or subfolders)
                            if (foldersWithCount.isNotEmpty() || selectedFolder != null) {
                                item {
                                    FolderSection(
                                        foldersWithCount = foldersWithCount,
                                        isGridView = false,
                                        isInsideFolder = selectedFolder != null,
                                        onSelectFolder = { viewModel.selectFolder(it) },
                                        onRenameFolder = { folderToRename = it },
                                        onDeleteFolder = { viewModel.deleteFolder(it) },
                                        onCreateFolderClick = { showNewFolderDialog = true }
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }

                            // Document rows
                            items(documents, key = { it.id }) { doc ->
                                ScannerDocumentListItem(
                                    document = doc,
                                    isSelected = selectedDocIds.contains(doc.id),
                                    isSelectMode = isSelectMode,
                                    onClick = {
                                        if (isSelectMode) {
                                            viewModel.toggleSelectDoc(doc.id)
                                        } else {
                                            onNavigateToDetail(doc.id)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelectDoc(doc.id)
                                    },
                                    onSelectToggle = { viewModel.toggleSelectDoc(doc.id) },
                                    onDelete = { viewModel.deleteDocument(doc) },
                                    onRename = { docToRename = doc },
                                    onMoveToFolder = { docToMove = doc }
                                )
                            }
                        }
                    }
                }
            }

            // Loading overlay for import operations
            if (isImporting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.background.copy(alpha = 0.75f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.primary)
                    }
                }
            }
        }
    }

    // Dialogs
    if (showNewFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name -> viewModel.createFolder(name) }
        )
    }

    folderToRename?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onDismiss = { folderToRename = null },
            onConfirm = { newName -> viewModel.renameFolder(folder.id, newName) }
        )
    }

    docToRename?.let { doc ->
        RenameDocumentDialog(
            document = doc,
            onDismiss = { docToRename = null },
            onConfirm = { newTitle -> viewModel.renameDocument(doc.id, newTitle) }
        )
    }

    if (showMoveCopyDialog) {
        MoveCopyDialog(
            selectedDocCount = selectedDocIds.size,
            foldersWithCount = allFoldersWithCount,
            onDismiss = { showMoveCopyDialog = false },
            onCreateFolder = { name, parentId -> viewModel.createFolder(name, parentId) },
            onConfirm = { action, targetFolderId, folderName ->
                val count = selectedDocIds.size
                if (action == MoveCopyAction.MOVE) {
                    viewModel.batchMoveSelected(targetFolderId) {
                        Toast.makeText(context, "$count document(s) moved to $folderName", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.batchCopySelected(targetFolderId) {
                        Toast.makeText(context, "$count document(s) copied to $folderName", Toast.LENGTH_SHORT).show()
                    }
                }
                showMoveCopyDialog = false
            }
        )
    }

    docToMove?.let { doc ->
        MoveCopyDialog(
            selectedDocCount = 1,
            foldersWithCount = allFoldersWithCount,
            onDismiss = { docToMove = null },
            onCreateFolder = { name, parentId -> viewModel.createFolder(name, parentId) },
            onConfirm = { action, targetFolderId, folderName ->
                if (action == MoveCopyAction.MOVE) {
                    viewModel.moveDocumentToFolder(doc.id, targetFolderId)
                    Toast.makeText(context, "Moved '${doc.title}' to $folderName", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.batchCopySelected(targetFolderId)
                    Toast.makeText(context, "Copied '${doc.title}' to $folderName", Toast.LENGTH_SHORT).show()
                }
                docToMove = null
            }
        )
    }

    if (showMergeDialog && selectedDocIds.size > 1) {
        MergeDocumentsDialog(
            selectedCount = selectedDocIds.size,
            onDismiss = { showMergeDialog = false },
            onConfirmMerge = { title, deleteOriginals ->
                val count = selectedDocIds.size
                viewModel.mergeSelected(title, deleteOriginals) { newDocId ->
                    Toast.makeText(context, "$count documents merged into '$title'", Toast.LENGTH_SHORT).show()
                    onNavigateToDetail(newDocId)
                }
                showMergeDialog = false
            }
        )
    }

    if (showMoreMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Batch Options (${selectedDocIds.size} selected)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                ListItem(
                    headlineContent = { Text("Export as PDF", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = colors.primary) },
                    modifier = Modifier.clickable {
                        showMoreMenu = false
                        showBatchPdfDialog = true
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Deselect All", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray) },
                    modifier = Modifier.clickable {
                        showMoreMenu = false
                        viewModel.clearSelection()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    if (showBatchPdfDialog && selectedDocIds.isNotEmpty()) {
        val firstSelectedDocId = selectedDocIds.first()
        showBatchPdfDialog = false
        onNavigateToDetail(firstSelectedDocId)
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Files", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select file type to import into OpenScanner:", color = colors.textSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showImportDialog = false
                            imagePickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Photos from Gallery", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            showImportDialog = false
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, colors.primary)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = colors.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import PDF Document", color = colors.textPrimary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }
}
