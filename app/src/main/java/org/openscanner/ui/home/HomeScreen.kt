package org.openscanner.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openscanner.R
import org.openscanner.data.model.Document
import org.openscanner.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCamera: (docId: Long, mode: String) -> Unit,
    onNavigateToDetail: (docId: Long) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var isGridView by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf("All Docs") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Docs, 1: Tools, 2: Me
    var showRenameDialog by remember { mutableStateOf<Document?>(null) }
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

    val tags = listOf("All Docs", "ID Card", "Certificates", "Receipts", "Notes")

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                // Main Header Row with OKEN Scanner Logo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = OkenGreen
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.DocumentScanner,
                                    contentDescription = "Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "OpenScanner",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = OkenTextPrimary,
                                fontSize = 19.sp
                            )
                            Text(
                                text = "Clean & Free Edition",
                                fontSize = 10.sp,
                                color = OkenGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (selectedTab == 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isGridView = !isGridView }) {
                                Icon(
                                    if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                                    contentDescription = "Toggle View",
                                    tint = OkenTextPrimary
                                )
                            }
                            IconButton(onClick = { /* Menu */ }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = OkenTextPrimary)
                            }
                        }
                    }
                }

                if (selectedTab == 0) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Search document name or OCR text...", color = OkenTextMuted, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = OkenTextSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OkenGreen,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = OkenBgLight,
                            unfocusedContainerColor = OkenBgLight
                        )
                    )

                    // Tags Carousel
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(tags) { tag ->
                            val isSelected = tag == selectedTag
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) OkenGreenContainer else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) OkenGreen else OkenBorderLight
                                ),
                                modifier = Modifier.clickable { selectedTag = tag }
                            ) {
                                Text(
                                    text = if (tag == "All Docs") "All Docs (${documents.size})" else tag,
                                    color = if (isSelected) OkenGreenDark else OkenTextSecondary,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(if (selectedTab == 0) Icons.Filled.Folder else Icons.Outlined.Folder, contentDescription = "Docs") },
                    label = { Text("Documents") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OkenGreen,
                        selectedTextColor = OkenGreen,
                        indicatorColor = OkenGreenContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(if (selectedTab == 1) Icons.Filled.Build else Icons.Outlined.Build, contentDescription = "Tools") },
                    label = { Text("PDF Tools") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OkenGreen,
                        selectedTextColor = OkenGreen,
                        indicatorColor = OkenGreenContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(if (selectedTab == 2) Icons.Filled.Person else Icons.Outlined.Person, contentDescription = "Me") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OkenGreen,
                        selectedTextColor = OkenGreen,
                        indicatorColor = OkenGreenContainer
                    )
                )
            }
        },
        floatingActionButton = {
            // Signature OKEN Scanner emerald green shutter FAB
            FloatingActionButton(
                onClick = { onNavigateToCamera(-1L, "Single") },
                shape = CircleShape,
                containerColor = OkenGreen,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
                modifier = Modifier
                    .size(64.dp)
                    .offset(y = (-10).dp)
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Scan Document",
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(OkenBgLight)
        ) {
            when (selectedTab) {
                0 -> { // Documents tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            KingKongFeatureBar(
                                onIdCardClick = { onNavigateToCamera(-1L, "ID Card") },
                                onImportPicClick = { imagePickerLauncher.launch("image/*") },
                                onImportPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                onSignDocClick = {
                                    if (documents.isNotEmpty()) {
                                        onNavigateToDetail(documents.first().id)
                                    } else {
                                        Toast.makeText(context, "Scan or import a document to sign", Toast.LENGTH_SHORT).show()
                                        onNavigateToCamera(-1L, "Single")
                                    }
                                }
                            )
                        }

                        item {
                            QuickToolsRow(
                                onOcrClick = { onNavigateToCamera(-1L, "Single") },
                                onCompressClick = {
                                    if (documents.isNotEmpty()) {
                                        onNavigateToDetail(documents.first().id)
                                    } else {
                                        Toast.makeText(context, "Scan a document to compress", Toast.LENGTH_SHORT).show()
                                        onNavigateToCamera(-1L, "Single")
                                    }
                                },
                                onBookClick = { onNavigateToCamera(-1L, "Book") },
                                onWatermarkClick = {
                                    if (documents.isNotEmpty()) {
                                        onNavigateToDetail(documents.first().id)
                                    } else {
                                        Toast.makeText(context, "Scan a document to watermark", Toast.LENGTH_SHORT).show()
                                        onNavigateToCamera(-1L, "Single")
                                    }
                                },
                                onPasswordClick = {
                                    if (documents.isNotEmpty()) {
                                        onNavigateToDetail(documents.first().id)
                                    } else {
                                        Toast.makeText(context, "Scan a document to protect", Toast.LENGTH_SHORT).show()
                                        onNavigateToCamera(-1L, "Single")
                                    }
                                }
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedTag == "All Docs") "Recent Documents (${documents.size})" else "$selectedTag (${documents.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = OkenTextPrimary
                                )
                            }
                        }

                        if (documents.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Surface(
                                            modifier = Modifier.size(68.dp),
                                            shape = CircleShape,
                                            color = OkenGreenContainer
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Outlined.DocumentScanner,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(34.dp),
                                                    tint = OkenGreen
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No documents scanned yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = OkenTextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Use the quick tools above or tap the green shutter button to scan",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OkenTextSecondary
                                        )
                                    }
                                }
                            }
                        } else {
                            items(documents, key = { it.id }) { doc ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    DocumentListCard(
                                        document = doc,
                                        onClick = { onNavigateToDetail(doc.id) },
                                        onDelete = { viewModel.deleteDocument(doc) },
                                        onRename = { showRenameDialog = doc }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> { // PDF Tools Tab
                    PdfToolsTab(
                        documents = documents,
                        onScanToolClick = { mode -> onNavigateToCamera(-1L, mode) },
                        onOpenDocument = onNavigateToDetail
                    )
                }
                2 -> { // Settings / Me Tab
                    SettingsTab(docCount = documents.size)
                }
            }

            if (isImporting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = OkenGreen, strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Importing & Processing Pages...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OkenTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Enhancing document contrast",
                                    fontSize = 11.sp,
                                    color = OkenTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    showRenameDialog?.let { doc ->
        var newTitle by remember { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Document", color = OkenTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OkenGreen,
                        unfocusedBorderColor = OkenBorderLight
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            viewModel.renameDocument(doc.id, newTitle)
                        }
                        showRenameDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OkenGreen)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel", color = OkenTextSecondary)
                }
            }
        )
    }
}

@Composable
fun DocumentListCard(
    document: Document,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = OkenBgLight,
                border = BorderStroke(1.dp, OkenBorderLight)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = OkenGreen,
                        modifier = Modifier.size(32.dp)
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = "${document.pageCount}P",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OkenTextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormat.format(Date(document.modifiedTime)),
                        fontSize = 12.sp,
                        color = OkenTextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = OkenGreenContainer
                    ) {
                        Text(
                            text = "PDF",
                            color = OkenGreenDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = OkenTextSecondary)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            expanded = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = OkenTextSecondary) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = OkenRed) },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = OkenRed) }
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentGridCard(
    document: Document,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(OkenBgLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = OkenGreen,
                    modifier = Modifier.size(48.dp)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = "${document.pageCount} Pages",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
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
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = OkenTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = dateFormat.format(Date(document.modifiedTime)),
                        fontSize = 11.sp,
                        color = OkenTextSecondary
                    )
                }
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = OkenTextSecondary)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { expanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = OkenRed) },
                            onClick = { expanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(onScanClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = OkenGreenContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.DocumentScanner,
                        contentDescription = "Empty",
                        modifier = Modifier.size(54.dp),
                        tint = OkenGreen
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No documents scanned yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OkenTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Capture receipts, notes, ID cards, and books instantly",
                style = MaterialTheme.typography.bodyMedium,
                color = OkenTextSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onScanClick,
                colors = ButtonDefaults.buttonColors(containerColor = OkenGreen),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Scanning", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PdfToolsTab(
    documents: List<Document>,
    onScanToolClick: (mode: String) -> Unit,
    onOpenDocument: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Document Tools", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OkenTextPrimary)
        }
        item {
            ToolBannerCard(
                title = "ID Card / Passport Mode",
                subtitle = "Scan front & back onto single A4 page",
                icon = Icons.Default.Badge,
                iconBg = Color(0xFFE0F2F1),
                iconTint = Color(0xFF00796B),
                onClick = { onScanToolClick("ID Card") }
            )
        }
        item {
            ToolBannerCard(
                title = "Compress PDF",
                subtitle = "Reduce document size while keeping text sharp",
                icon = Icons.Default.Compress,
                iconBg = Color(0xFFFFF3E0),
                iconTint = Color(0xFFE65100),
                onClick = {
                    if (documents.isNotEmpty()) {
                        onOpenDocument(documents.first().id)
                    } else {
                        onScanToolClick("Single")
                    }
                }
            )
        }
        item {
            ToolBannerCard(
                title = "E-Signature & Seal",
                subtitle = "Extract transparent signatures from paper",
                icon = Icons.Default.Draw,
                iconBg = Color(0xFFF3E5F5),
                iconTint = Color(0xFF7B1FA2),
                onClick = {
                    if (documents.isNotEmpty()) {
                        onOpenDocument(documents.first().id)
                    } else {
                        onScanToolClick("Single")
                    }
                }
            )
        }
        item {
            ToolBannerCard(
                title = "PDF Password Protection",
                subtitle = "Lock sensitive documents with AES-128 encryption",
                icon = Icons.Default.Lock,
                iconBg = Color(0xFFFFEBEE),
                iconTint = Color(0xFFC62828),
                onClick = {
                    if (documents.isNotEmpty()) {
                        onOpenDocument(documents.first().id)
                    } else {
                        onScanToolClick("Single")
                    }
                }
            )
        }
        item {
            ToolBannerCard(
                title = "Convert Image to PDF",
                subtitle = "Batch merge photos into high-res PDF",
                icon = Icons.Default.PictureAsPdf,
                iconBg = Color(0xFFE8F5E9),
                iconTint = Color(0xFF2E7D32),
                onClick = { onScanToolClick("Batch") }
            )
        }
    }
}

@Composable
fun ToolBannerCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = iconBg
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = OkenTextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = OkenTextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OkenBorderLight)
        }
    }
}

@Composable
fun SettingsTab(docCount: Int) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("OpenScanner Pro Clean", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OkenGreen)
                    Text("100% Offline & Open Source • Zero Ads • No Subscriptions", fontSize = 12.sp, color = OkenTextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Total Documents Stored: $docCount", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }
        }
        item {
            Text("Settings & Features", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OkenTextPrimary)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Auto-Crop Documents") },
                        supportingContent = { Text("Automatically detect corners on camera capture") },
                        trailingContent = { Switch(checked = true, onCheckedChange = {}) }
                    )
                    HorizontalDivider(color = OkenBorderLight)
                    ListItem(
                        headlineContent = { Text("High-Resolution Mode") },
                        supportingContent = { Text("Process images in maximum camera resolution") },
                        trailingContent = { Switch(checked = true, onCheckedChange = {}) }
                    )
                    HorizontalDivider(color = OkenBorderLight)
                    ListItem(
                        headlineContent = { Text("Default Filter") },
                        supportingContent = { Text("Magic Color (Vivid Contrast)") },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
fun KingKongFeatureBar(
    onIdCardClick: () -> Unit,
    onImportPicClick: () -> Unit,
    onImportPdfClick: () -> Unit,
    onSignDocClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KingKongCard(
            modifier = Modifier.weight(1f),
            title = "ID Card",
            subtitle = "2-Sided Stitch",
            icon = Icons.Default.Badge,
            iconTint = Color(0xFF00897B),
            containerColor = Color(0xFFE0F2F1),
            onClick = onIdCardClick
        )
        KingKongCard(
            modifier = Modifier.weight(1f),
            title = "Import Pic",
            subtitle = "Batch Images",
            icon = Icons.Default.PhotoLibrary,
            iconTint = Color(0xFF1E88E5),
            containerColor = Color(0xFFE3F2FD),
            onClick = onImportPicClick
        )
        KingKongCard(
            modifier = Modifier.weight(1f),
            title = "Import PDF",
            subtitle = "View & Edit",
            icon = Icons.Default.PictureAsPdf,
            iconTint = Color(0xFFE65100),
            containerColor = Color(0xFFFFF3E0),
            onClick = onImportPdfClick
        )
        KingKongCard(
            modifier = Modifier.weight(1f),
            title = "PDF Sign",
            subtitle = "E-Signature",
            icon = Icons.Default.Draw,
            iconTint = Color(0xFF8E24AA),
            containerColor = Color(0xFFF3E5F5),
            onClick = onSignDocClick
        )
    }
}

@Composable
fun KingKongCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    containerColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = containerColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OkenTextPrimary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = OkenTextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun QuickToolsRow(
    onOcrClick: () -> Unit,
    onCompressClick: () -> Unit,
    onBookClick: () -> Unit,
    onWatermarkClick: () -> Unit,
    onPasswordClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        item {
            QuickToolPill(
                title = "Extract Text (OCR)",
                icon = Icons.Default.DocumentScanner,
                tint = Color(0xFF43A047),
                onClick = onOcrClick
            )
        }
        item {
            QuickToolPill(
                title = "Compress PDF",
                icon = Icons.Default.Compress,
                tint = Color(0xFFFB8C00),
                onClick = onCompressClick
            )
        }
        item {
            QuickToolPill(
                title = "Book (2-Page Split)",
                icon = Icons.Default.AutoStories,
                tint = Color(0xFF00ACC1),
                onClick = onBookClick
            )
        }
        item {
            QuickToolPill(
                title = "Anti-Theft Watermark",
                icon = Icons.Default.BrandingWatermark,
                tint = Color(0xFF5E35B1),
                onClick = onWatermarkClick
            )
        }
        item {
            QuickToolPill(
                title = "Password Protect",
                icon = Icons.Default.Lock,
                tint = Color(0xFFE53935),
                onClick = onPasswordClick
            )
        }
    }
}

@Composable
fun QuickToolPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = OkenTextPrimary
            )
        }
    }
}

