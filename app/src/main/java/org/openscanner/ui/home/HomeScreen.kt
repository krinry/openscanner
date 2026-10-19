package org.openscanner.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.ViewList
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.openscanner.data.model.Document
import org.openscanner.data.model.Folder
import org.openscanner.data.model.FolderWithCount
import org.openscanner.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Global AMOLED Dark Palette
private val DarkBg = Color(0xFF000000)
private val DarkCard = Color(0xFF141414)
private val DarkCardBorder = Color(0xFF222222)
private val DarkTextPrimary = Color(0xFFFFFFFF)
private val DarkTextSecondary = Color(0xFF8E8E93)
private val ScannerBrandGreen = Color(0xFF00BA66)
private val ScannerFolderYellow = Color(0xFFF0AF3A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCamera: (docId: Long, mode: String) -> Unit,
    onNavigateToDetail: (docId: Long) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val foldersWithCount by viewModel.foldersWithCount.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedDocIds by viewModel.selectedDocIds.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Docs, 1: Tools
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showFolderSelectorDropdown by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var docToRename by remember { mutableStateOf<Document?>(null) }
    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

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

    val totalDocCount = remember(documents, foldersWithCount, selectedFolder) {
        if (selectedFolder != null) {
            documents.size
        } else {
            val folderDocs = foldersWithCount.sumOf { it.docCount }
            val rootDocs = documents.count { it.folderId <= 0 }
            rootDocs + folderDocs
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg)
                    .statusBarsPadding()
            ) {
                if (isSelectMode) {
                    // Batch Selection Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextPrimary)
                        }
                        Text(
                            text = "${selectedDocIds.size} Selected",
                            color = DarkTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        TextButton(onClick = { viewModel.selectAllDocs() }) {
                            Text("Select All", color = ScannerBrandGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // OpenScanner Clean Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Profile Avatar
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFF1E2822),
                            border = BorderStroke(1.5.dp, ScannerBrandGreen)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = ScannerBrandGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // OpenScanner Logo
                        Text(
                            text = "OpenScanner",
                            color = ScannerBrandGreen,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 21.sp,
                            letterSpacing = 0.5.sp
                        )

                        // Search Icon
                        IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = DarkTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Collapsible Search Input
                    AnimatedVisibility(visible = isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search document or OCR text...", color = DarkTextSecondary, fontSize = 14.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DarkTextSecondary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = DarkTextSecondary)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = DarkTextPrimary,
                                unfocusedTextColor = DarkTextPrimary,
                                focusedBorderColor = ScannerBrandGreen,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedContainerColor = DarkCard,
                                unfocusedContainerColor = DarkCard
                            ),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    if (selectedTab == 0) {
                        // Sub-header Filter & Actions Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // "All docs(count) ▾"
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { showFolderSelectorDropdown = true }
                                        .padding(vertical = 4.dp)
                                ) {
                                    if (selectedFolder != null) {
                                        IconButton(
                                            onClick = { viewModel.selectFolder(null) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = DarkTextPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    val currentLabel = selectedFolder?.let { "${it.name}(${documents.size})" }
                                        ?: "All docs(${totalDocCount})"

                                    Text(
                                        text = currentLabel,
                                        color = DarkTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 200.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Folder",
                                        tint = ScannerBrandGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Dropdown of folders
                                DropdownMenu(
                                    expanded = showFolderSelectorDropdown,
                                    onDismissRequest = { showFolderSelectorDropdown = false },
                                    modifier = Modifier.background(DarkCard)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "All docs (${totalDocCount})",
                                                color = if (selectedFolder == null) ScannerBrandGreen else DarkTextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectFolder(null)
                                            showFolderSelectorDropdown = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = if (selectedFolder == null) ScannerBrandGreen else DarkTextSecondary
                                            )
                                        }
                                    )
                                    foldersWithCount.forEach { fwc ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${fwc.folder.name} (${fwc.docCount})",
                                                    color = if (selectedFolder?.id == fwc.folder.id) ScannerBrandGreen else DarkTextPrimary,
                                                    fontWeight = if (selectedFolder?.id == fwc.folder.id) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                viewModel.selectFolder(fwc.folder)
                                                showFolderSelectorDropdown = false
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = ScannerFolderYellow
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            // 4 Header Actions: [+] [⇅] [⊞] [☑]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // 1. New Folder icon [+]
                                IconButton(
                                    onClick = { showNewFolderDialog = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.CreateNewFolder,
                                        contentDescription = "New Folder",
                                        tint = DarkTextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // 2. Sort icon [⇅]
                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.SwapVert,
                                            contentDescription = "Sort",
                                            tint = DarkTextPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        modifier = Modifier.background(DarkCard)
                                    ) {
                                        DocSortOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        order.title,
                                                        color = if (sortOrder == order) ScannerBrandGreen else DarkTextPrimary
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.setSortOrder(order)
                                                    showSortMenu = false
                                                },
                                                trailingIcon = {
                                                    if (sortOrder == order) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = ScannerBrandGreen)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                // 3. Layout Toggle [⊞]
                                IconButton(
                                    onClick = { viewModel.toggleGridView() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                                        contentDescription = "Toggle Grid/List",
                                        tint = DarkTextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // 4. Batch Select [☑]
                                IconButton(
                                    onClick = { viewModel.toggleSelectMode() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Checklist,
                                        contentDescription = "Select Mode",
                                        tint = if (isSelectMode) ScannerBrandGreen else DarkTextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (isSelectMode) {
                // Batch Actions Bottom Bar
                Surface(
                    color = DarkCard,
                    tonalElevation = 8.dp,
                    border = BorderStroke(0.5.dp, DarkCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(enabled = selectedDocIds.isNotEmpty()) {
                                    showMoveToFolderDialog = true
                                }
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.DriveFileMove,
                                contentDescription = "Move",
                                tint = if (selectedDocIds.isNotEmpty()) DarkTextPrimary else DarkTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Move", color = if (selectedDocIds.isNotEmpty()) DarkTextPrimary else DarkTextSecondary, fontSize = 12.sp)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(enabled = selectedDocIds.isNotEmpty()) {
                                    viewModel.batchDeleteSelected()
                                }
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = if (selectedDocIds.isNotEmpty()) Color(0xFFEE3F3F) else DarkTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Delete", color = if (selectedDocIds.isNotEmpty()) Color(0xFFEE3F3F) else DarkTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                // 2-Tab Navigation Bar with Elevated Camera FAB
                Surface(
                    color = DarkBg,
                    tonalElevation = 6.dp,
                    border = BorderStroke(0.5.dp, DarkCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tab 1: Docs
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedTab = 0 }
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = "Docs",
                                tint = if (selectedTab == 0) ScannerBrandGreen else DarkTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Docs",
                                color = if (selectedTab == 0) ScannerBrandGreen else DarkTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        // Elevated Camera Shutter FAB
                        FloatingActionButton(
                            onClick = { onNavigateToCamera(-1L, "Single") },
                            shape = CircleShape,
                            containerColor = ScannerBrandGreen,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(6.dp),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Scan",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Tab 2: Tools
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedTab = 1 }
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = "Tools",
                                tint = if (selectedTab == 1) ScannerBrandGreen else DarkTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tools",
                                color = if (selectedTab == 1) ScannerBrandGreen else DarkTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
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
            when (selectedTab) {
                0 -> { // Documents View
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Folders section (on top level)
                            if (selectedFolder == null) {
                                items(foldersWithCount, key = { "folder_${it.folder.id}" }) { fwc ->
                                    ScannerFolderGridCard(
                                        folderWithCount = fwc,
                                        onClick = { viewModel.selectFolder(fwc.folder) },
                                        onRename = { folderToRename = fwc.folder },
                                        onDelete = { viewModel.deleteFolder(fwc.folder) }
                                    )
                                }
                            }

                            // Documents section
                            items(documents, key = { "doc_${it.id}" }) { doc ->
                                val isSelected = selectedDocIds.contains(doc.id)
                                ScannerDocumentGridCard(
                                    document = doc,
                                    isSelected = isSelected,
                                    isSelectMode = isSelectMode,
                                    onClick = {
                                        if (isSelectMode) {
                                            viewModel.toggleSelectDoc(doc.id)
                                        } else {
                                            onNavigateToDetail(doc.id)
                                        }
                                    },
                                    onDelete = { viewModel.deleteDocument(doc) },
                                    onRename = { docToRename = doc }
                                )
                            }
                        }
                    } else {
                        // Standard List View
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Folders section (on top level)
                            if (selectedFolder == null) {
                                items(foldersWithCount, key = { "folder_${it.folder.id}" }) { fwc ->
                                    ScannerFolderListItem(
                                        folderWithCount = fwc,
                                        onClick = { viewModel.selectFolder(fwc.folder) },
                                        onRename = { folderToRename = fwc.folder },
                                        onDelete = { viewModel.deleteFolder(fwc.folder) }
                                    )
                                }
                            }

                            // Documents list
                            if (documents.isEmpty() && (selectedFolder != null || foldersWithCount.isEmpty())) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 80.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Outlined.DocumentScanner,
                                                contentDescription = null,
                                                tint = DarkTextSecondary,
                                                modifier = Modifier.size(54.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = if (selectedFolder != null) "Folder is empty" else "No documents scanned yet",
                                                color = DarkTextSecondary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = { onNavigateToCamera(-1L, "Single") },
                                                colors = ButtonDefaults.buttonColors(containerColor = ScannerBrandGreen),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Text("Scan Document")
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(documents, key = { "doc_${it.id}" }) { doc ->
                                    val isSelected = selectedDocIds.contains(doc.id)
                                    ScannerDocumentListItem(
                                        document = doc,
                                        isSelected = isSelected,
                                        isSelectMode = isSelectMode,
                                        onClick = {
                                            if (isSelectMode) {
                                                viewModel.toggleSelectDoc(doc.id)
                                            } else {
                                                onNavigateToDetail(doc.id)
                                            }
                                        },
                                        onSelectToggle = { viewModel.toggleSelectDoc(doc.id) },
                                        onDelete = { viewModel.deleteDocument(doc) },
                                        onRename = { docToRename = doc }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> { // Tools Tab
                    ScannerToolsTab(
                        onScanIdCard = { onNavigateToCamera(-1L, "ID Card") },
                        onScanBook = { onNavigateToCamera(-1L, "Book") },
                        onImportImages = { imagePickerLauncher.launch("image/*") },
                        onImportPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        onSignTool = {
                            if (documents.isNotEmpty()) {
                                onNavigateToDetail(documents.first().id)
                            } else {
                                Toast.makeText(context, "Scan a document first to use tools", Toast.LENGTH_SHORT).show()
                                onNavigateToCamera(-1L, "Single")
                            }
                        }
                    )
                }
            }

            if (isImporting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = ScannerBrandGreen, strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Importing & Processing Pages...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // 1. New Folder Dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("Folder Name", color = DarkTextSecondary) },
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
                        if (folderName.isNotBlank()) {
                            viewModel.createFolder(folderName)
                        }
                        showNewFolderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerBrandGreen)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }

    // 2. Rename Folder Dialog
    folderToRename?.let { folder ->
        var updatedName by remember { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text("Rename Folder", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
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
                        if (updatedName.isNotBlank()) {
                            viewModel.renameFolder(folder.id, updatedName)
                        }
                        folderToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerBrandGreen)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }

    // 3. Rename Document Dialog
    docToRename?.let { doc ->
        var updatedDocName by remember { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { docToRename = null },
            title = { Text("Rename Document", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = updatedDocName,
                    onValueChange = { updatedDocName = it },
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
                        if (updatedDocName.isNotBlank()) {
                            viewModel.renameDocument(doc.id, updatedDocName)
                        }
                        docToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerBrandGreen)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToRename = null }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }

    // 4. Move to Folder Picker Dialog
    if (showMoveToFolderDialog) {
        AlertDialog(
            onDismissRequest = { showMoveToFolderDialog = false },
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
                                viewModel.batchMoveSelected(-1L)
                                showMoveToFolderDialog = false
                            }
                    ) {
                        Text("Root (All docs)", color = DarkTextPrimary, modifier = Modifier.padding(14.dp))
                    }

                    foldersWithCount.forEach { fwc ->
                        Surface(
                            color = DarkCardBorder,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.batchMoveSelected(fwc.folder.id)
                                    showMoveToFolderDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = ScannerFolderYellow)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(fwc.folder.name, color = DarkTextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveToFolderDialog = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }
}

// ---------------------------------------------------------------------------
// FOLDER CARD COMPONENTS
// ---------------------------------------------------------------------------

@Composable
fun ScannerFolderListItem(
    folderWithCount: FolderWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Manila Yellow Folder Icon
        ScannerFolderGraphic(modifier = Modifier.size(width = 66.dp, height = 52.dp))

        Spacer(modifier = Modifier.width(16.dp))

        // Folder Title and Count
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folderWithCount.folder.name,
                color = DarkTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = DarkTextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${folderWithCount.docCount}",
                    color = DarkTextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // 3-dots Menu
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Folder options", tint = DarkTextSecondary)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkCard)
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", color = DarkTextPrimary) },
                    onClick = {
                        expanded = false
                        onRename()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = DarkTextSecondary) }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFEE3F3F)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEE3F3F)) }
                )
            }
        }
    }
}

@Composable
fun ScannerFolderGridCard(
    folderWithCount: FolderWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                ScannerFolderGraphic(modifier = Modifier.size(width = 54.dp, height = 42.dp))
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = DarkTextSecondary)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(DarkCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = DarkTextPrimary) },
                            onClick = { expanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFEE3F3F)) },
                            onClick = { expanded = false; onDelete() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = folderWithCount.folder.name,
                color = DarkTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "${folderWithCount.docCount}", color = DarkTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Standard Manila folder vector drawn using Jetpack Compose Canvas
 */
@Composable
fun ScannerFolderGraphic(
    modifier: Modifier = Modifier,
    color: Color = ScannerFolderYellow
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = 6.dp.toPx()

        // 1. Back Folder Body with Tab
        val tabPath = Path().apply {
            moveTo(corner, 0f)
            lineTo(w * 0.44f, 0f)
            quadraticBezierTo(w * 0.50f, 0f, w * 0.54f, corner)
            lineTo(w - corner, corner)
            quadraticBezierTo(w, corner, w, corner * 2)
            lineTo(w, h - corner)
            quadraticBezierTo(w, h, w - corner, h)
            lineTo(corner, h)
            quadraticBezierTo(0f, h, 0f, h - corner)
            lineTo(0f, corner)
            quadraticBezierTo(0f, 0f, corner, 0f)
            close()
        }
        drawPath(tabPath, color = color.copy(alpha = 0.90f))

        // 2. White Sheet Peeking inside
        drawRoundRect(
            color = Color(0xFFF8F9FA),
            topLeft = Offset(w * 0.12f, h * 0.14f),
            size = Size(w * 0.76f, h * 0.48f),
            cornerRadius = CornerRadius(3.dp.toPx())
        )

        // 3. Front Flap
        val frontPath = Path().apply {
            moveTo(0f, h * 0.34f + corner)
            quadraticBezierTo(0f, h * 0.34f, corner, h * 0.34f)
            lineTo(w - corner, h * 0.34f)
            quadraticBezierTo(w, h * 0.34f, w, h * 0.34f + corner)
            lineTo(w, h - corner)
            quadraticBezierTo(w, h, w - corner, h)
            lineTo(corner, h)
            quadraticBezierTo(0f, h, 0f, h - corner)
            close()
        }
        drawPath(frontPath, color = color)
    }
}

// ---------------------------------------------------------------------------
// DOCUMENT ITEM COMPONENTS
// ---------------------------------------------------------------------------

@Composable
fun ScannerDocumentListItem(
    document: Document,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onClick: () -> Unit,
    onSelectToggle: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with Green Check Badge
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(86.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1C1C1E))
                .border(0.5.dp, DarkCardBorder, RoundedCornerShape(6.dp))
        ) {
            val thumbFile = remember(document.id) {
                File("/data/data/org.openscanner/files/thumbs").listFiles()
                    ?.firstOrNull { it.name.startsWith("thumb_${document.id}_") }
            }

            if (thumbFile != null && thumbFile.exists()) {
                AsyncImage(
                    model = thumbFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(5.dp).background(Color(0xFF333333), RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.95f).height(4.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.7f).height(4.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.85f).height(4.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(2.dp)))
                }
            }

            // Green Verified Badge in bottom right of thumbnail
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(15.dp),
                shape = CircleShape,
                color = ScannerBrandGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Scanned",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Center Column: Title, Date, Page count
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.title,
                color = DarkTextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = dateFormat.format(Date(document.modifiedTime)),
                color = DarkTextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = DarkTextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${document.pageCount}",
                    color = DarkTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Right side: Selection Checkbox in Select Mode or 3-dots menu in Regular Mode
        if (isSelectMode) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onSelectToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = ScannerBrandGreen,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, DarkTextSecondary),
                        modifier = Modifier.size(20.dp)
                    ) {}
                }
            }
        } else {
            var docMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { docMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = DarkTextSecondary)
                }
                DropdownMenu(
                    expanded = docMenuExpanded,
                    onDismissRequest = { docMenuExpanded = false },
                    modifier = Modifier.background(DarkCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = DarkTextPrimary) },
                        onClick = {
                            docMenuExpanded = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = DarkTextSecondary) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFEE3F3F)) },
                        onClick = {
                            docMenuExpanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEE3F3F)) }
                    )
                }
            }
        }
    }
}

@Composable
fun ScannerDocumentGridCard(
    document: Document,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = ScannerBrandGreen,
                    modifier = Modifier.size(44.dp)
                )

                // Green badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp),
                    shape = CircleShape,
                    color = ScannerBrandGreen
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }

                // Selection check indicator in grid mode
                if (isSelectMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp),
                        shape = CircleShape,
                        color = if (isSelected) ScannerBrandGreen else Color.Black.copy(alpha = 0.5f),
                        border = if (!isSelected) BorderStroke(1.5.dp, Color.White) else null
                    ) {
                        if (isSelected) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        color = DarkTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(dateFormat.format(Date(document.modifiedTime)), color = DarkTextSecondary, fontSize = 11.sp)
                        Text("${document.pageCount} P", color = ScannerBrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!isSelectMode) {
                    var gridDocMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { gridDocMenuExpanded = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = DarkTextSecondary)
                        }
                        DropdownMenu(
                            expanded = gridDocMenuExpanded,
                            onDismissRequest = { gridDocMenuExpanded = false },
                            modifier = Modifier.background(DarkCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename", color = DarkTextPrimary) },
                                onClick = {
                                    gridDocMenuExpanded = false
                                    onRename()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = DarkTextSecondary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFEE3F3F)) },
                                onClick = {
                                    gridDocMenuExpanded = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEE3F3F)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// TOOLS TAB
// ---------------------------------------------------------------------------

@Composable
fun ScannerToolsTab(
    onScanIdCard: () -> Unit,
    onScanBook: () -> Unit,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onSignTool: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Scanner Tools",
                color = DarkTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        item {
            ScannerToolCard(
                title = "ID Card & Passport Mode",
                subtitle = "Front & back auto-stitched onto A4 page",
                icon = Icons.Default.Badge,
                accentColor = Color(0xFF00BFA5),
                onClick = onScanIdCard
            )
        }

        item {
            ScannerToolCard(
                title = "Import Pictures to PDF",
                subtitle = "Batch convert gallery photos to clean scans",
                icon = Icons.Default.AddPhotoAlternate,
                accentColor = Color(0xFF2979FF),
                onClick = onImportImages
            )
        }

        item {
            ScannerToolCard(
                title = "Import & Split PDF",
                subtitle = "High-resolution PDF rasterization & editing",
                icon = Icons.Default.PictureAsPdf,
                accentColor = Color(0xFFFF5252),
                onClick = onImportPdf
            )
        }

        item {
            ScannerToolCard(
                title = "E-Signature & Paper Seal",
                subtitle = "Extract transparent ink signature from camera",
                icon = Icons.Default.Draw,
                accentColor = Color(0xFFFFAB00),
                onClick = onSignTool
            )
        }

        item {
            ScannerToolCard(
                title = "Book Curve Splitter",
                subtitle = "Dual-page capture with 3D unwarping",
                icon = Icons.Default.AutoStories,
                accentColor = Color(0xFFAA00FF),
                onClick = onScanBook
            )
        }
    }
}

@Composable
fun ScannerToolCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(0.5.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = DarkTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = DarkTextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DarkTextSecondary)
        }
    }
}
