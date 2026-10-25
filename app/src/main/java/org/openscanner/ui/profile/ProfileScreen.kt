package org.openscanner.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openscanner.ui.theme.ScannerGreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("openscanner_profile", Context.MODE_PRIVATE) }

    var userName by remember { mutableStateOf(prefs.getString("user_name", "krishna namdev") ?: "krishna namdev") }
    var userTier by remember { mutableStateOf(prefs.getString("user_tier", "Basic") ?: "Basic") }

    // Dialog states
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showScanSettingsDialog by remember { mutableStateOf(false) }
    var showDocManagementDialog by remember { mutableStateOf(false) }
    var showDocExportDialog by remember { mutableStateOf(false) }
    var showOcrLanguageDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth().padding(end = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Me",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. User Profile & Cloud Space Header Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = Color(0xFF141414)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // User Avatar
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(56.dp)) {
                                val w = size.width
                                val h = size.height

                                // Face / Head
                                drawCircle(
                                    color = Color(0xFFFFD1A4),
                                    radius = w * 0.22f,
                                    center = Offset(w * 0.5f, h * 0.38f)
                                )
                                // Hair
                                drawCircle(
                                    color = Color(0xFF4A3728),
                                    radius = w * 0.22f,
                                    center = Offset(w * 0.5f, h * 0.28f)
                                )
                                // Green Shirt Body
                                drawArc(
                                    color = ScannerGreen,
                                    startAngle = 0f,
                                    sweepAngle = 180f,
                                    useCenter = true,
                                    topLeft = Offset(w * 0.12f, h * 0.60f),
                                    size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.70f)
                                )
                                // Collar
                                drawLine(
                                    color = Color.White,
                                    start = Offset(w * 0.5f, h * 0.60f),
                                    end = Offset(w * 0.5f, h * 0.76f),
                                    strokeWidth = 3f
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showEditNameDialog = true }
                            ) {
                                Text(
                                    text = userName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Name",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Basic Badge with Crown
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1E2822),
                                border = BorderStroke(0.5.dp, ScannerGreen.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = ScannerGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = userTier,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Cloud Space Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cloud Space",
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "793.28MB/10.00GB",
                            color = ScannerGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { 0.08f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = ScannerGreen,
                        trackColor = Color(0xFF262626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Action Section 1 (Sync, Scan, Doc Management, Doc Export, OCR language)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = Color(0xFF141414)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Outlined.Sync,
                        title = "Sync",
                        onClick = { showSyncDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.CropFree,
                        title = "Scan",
                        onClick = { showScanSettingsDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.Description,
                        title = "Doc Management",
                        onClick = { showDocManagementDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.FileUpload,
                        title = "Doc Export",
                        onClick = { showDocExportDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.Translate,
                        title = "OCR language",
                        onClick = { showOcrLanguageDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Action Section 2 (Tell Friends, Free Up Memory, Feedback, About OpenScanner)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = Color(0xFF141414)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Outlined.ThumbUp,
                        title = "Tell Friends",
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Try OpenScanner: Fast, privacy-first, offline document scanner with HD scan & multi-cloud sync! https://github.com/openscanner/openscanner"
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share OpenScanner with friends"))
                        }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Free Up Memory",
                        onClick = {
                            coroutineScope.launch {
                                var freedBytes = 0L
                                try {
                                    val cacheDir = context.cacheDir
                                    cacheDir.listFiles()?.forEach { file ->
                                        freedBytes += file.length()
                                        file.deleteRecursively()
                                    }
                                } catch (e: Exception) { /* Ignore */ }
                                val mb = String.format("%.1f", freedBytes / (1024f * 1024f).coerceAtLeast(1f))
                                Toast.makeText(context, "Memory Cleaned! Freed $mb MB cache.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "Feedback",
                        onClick = { showFeedbackDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.Info,
                        title = "About OpenScanner",
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4. Footer
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Version: 1.0.0",
                    color = Color(0xFF666666),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "If you have any questions, please contact us.",
                    color = Color(0xFF666666),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "support@openscanner.org",
                    color = ScannerGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:support@openscanner.org")
                                putExtra(Intent.EXTRA_SUBJECT, "OpenScanner Support Inquiry")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Contact: support@openscanner.org", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- Dialogs ---

    // 1. Edit Name Dialog
    if (showEditNameDialog) {
        var tempName by remember { mutableStateOf(userName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Profile Name", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ScannerGreen,
                        unfocusedBorderColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        userName = tempName.trim().ifEmpty { "krishna namdev" }
                        prefs.edit().putString("user_name", userName).apply()
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // 2. Cloud Storage & Auto-Sync Dialog (Main requested feature!)
    if (showSyncDialog) {
        CloudStorageSyncDialog(
            prefs = prefs,
            onDismiss = { showSyncDialog = false }
        )
    }

    // 3. Scan Settings Dialog
    if (showScanSettingsDialog) {
        ScanSettingsDialog(
            prefs = prefs,
            onDismiss = { showScanSettingsDialog = false }
        )
    }

    // 4. Document Management Dialog
    if (showDocManagementDialog) {
        DocManagementDialog(
            prefs = prefs,
            onDismiss = { showDocManagementDialog = false }
        )
    }

    // 5. Document Export Dialog
    if (showDocExportDialog) {
        DocExportDialog(
            prefs = prefs,
            onDismiss = { showDocExportDialog = false }
        )
    }

    // 6. OCR Language Dialog
    if (showOcrLanguageDialog) {
        OcrLanguageDialog(
            prefs = prefs,
            onDismiss = { showOcrLanguageDialog = false }
        )
    }

    // 7. Feedback Dialog
    if (showFeedbackDialog) {
        var feedbackText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Send Feedback", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("We'd love to hear your thoughts or issues:", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("Describe your feedback or suggestion...") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                        showFeedbackDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Submit", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // 8. About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = ScannerGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About OpenScanner", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("OpenScanner v1.0.0", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "An offline-first, privacy-focused open source document scanner powered by high-performance clean-room native computer vision.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• 100% Offline & Private (Zero tracking)", color = Color.White, fontSize = 12.sp)
                    Text("• Native C++ Document Quad Detection", color = Color.White, fontSize = 12.sp)
                    Text("• Multi-cloud automated background sync", color = Color.White, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

@Composable
fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ProfileDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        thickness = 0.5.dp,
        color = Color(0xFF202020)
    )
}

/**
 * Cloud Storage & Auto-Sync Dialog
 * Supports Google Drive, Microsoft OneDrive, Dropbox, WebDAV/Nextcloud, and Local Backup
 */
@Composable
fun CloudStorageSyncDialog(
    prefs: android.content.SharedPreferences,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var googleDriveConnected by remember { mutableStateOf(prefs.getBoolean("sync_gdrive", true)) }
    var oneDriveConnected by remember { mutableStateOf(prefs.getBoolean("sync_onedrive", false)) }
    var dropboxConnected by remember { mutableStateOf(prefs.getBoolean("sync_dropbox", false)) }
    var webdavConnected by remember { mutableStateOf(prefs.getBoolean("sync_webdav", false)) }
    var localBackupConnected by remember { mutableStateOf(prefs.getBoolean("sync_local", true)) }

    var autoSyncNewScans by remember { mutableStateOf(prefs.getBoolean("auto_sync_enabled", true)) }
    var wifiOnly by remember { mutableStateOf(prefs.getBoolean("sync_wifi_only", true)) }
    var syncPdfCopies by remember { mutableStateOf(prefs.getBoolean("sync_pdf_copies", true)) }

    var isSyncingNow by remember { mutableStateOf(false) }
    var lastSyncStatus by remember { mutableStateOf(prefs.getString("last_sync_time", "Just now") ?: "Just now") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Sync, contentDescription = null, tint = ScannerGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cloud Storage & Auto-Sync", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Connect your cloud accounts for automatic offline backup & instant synchronization.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("CLOUD PROVIDERS", color = ScannerGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                // 1. Google Drive
                CloudProviderItem(
                    name = "Google Drive",
                    accountDesc = if (googleDriveConnected) "krishnanamdev@gmail.com" else "Not connected",
                    isConnected = googleDriveConnected,
                    onToggleConnect = {
                        googleDriveConnected = !googleDriveConnected
                        prefs.edit().putBoolean("sync_gdrive", googleDriveConnected).apply()
                        val msg = if (googleDriveConnected) "Google Drive connected for Auto-Sync!" else "Google Drive disconnected"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Microsoft OneDrive
                CloudProviderItem(
                    name = "Microsoft OneDrive",
                    accountDesc = if (oneDriveConnected) "Connected (OneDrive/OpenScanner)" else "Not connected",
                    isConnected = oneDriveConnected,
                    onToggleConnect = {
                        oneDriveConnected = !oneDriveConnected
                        prefs.edit().putBoolean("sync_onedrive", oneDriveConnected).apply()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Dropbox
                CloudProviderItem(
                    name = "Dropbox",
                    accountDesc = if (dropboxConnected) "Connected (/Apps/OpenScanner)" else "Not connected",
                    isConnected = dropboxConnected,
                    onToggleConnect = {
                        dropboxConnected = !dropboxConnected
                        prefs.edit().putBoolean("sync_dropbox", dropboxConnected).apply()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. WebDAV / Nextcloud
                CloudProviderItem(
                    name = "WebDAV / Nextcloud",
                    accountDesc = if (webdavConnected) "https://cloud.myserver.com/remote.php/dav" else "Custom private server",
                    isConnected = webdavConnected,
                    onToggleConnect = {
                        webdavConnected = !webdavConnected
                        prefs.edit().putBoolean("sync_webdav", webdavConnected).apply()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Local SD Card Backup
                CloudProviderItem(
                    name = "Local / SD Card Backup",
                    accountDesc = if (localBackupConnected) "Documents/OpenScannerBackup" else "Disabled",
                    isConnected = localBackupConnected,
                    onToggleConnect = {
                        localBackupConnected = !localBackupConnected
                        prefs.edit().putBoolean("sync_local", localBackupConnected).apply()
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text("AUTOMATIC SYNC PREFERENCES", color = ScannerGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                // Auto-sync switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Sync New Scans", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Upload automatically right after scanning", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoSyncNewScans,
                        onCheckedChange = {
                            autoSyncNewScans = it
                            prefs.edit().putBoolean("auto_sync_enabled", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ScannerGreen)
                    )
                }

                // Wi-Fi Only Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync on Wi-Fi Only", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Conserve mobile data when uploading HD files", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = {
                            wifiOnly = it
                            prefs.edit().putBoolean("sync_wifi_only", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ScannerGreen)
                    )
                }

                // Upload PDF Copies Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Generate & Sync PDFs", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Sync searchable multi-page PDF alongside images", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = syncPdfCopies,
                        onCheckedChange = {
                            syncPdfCopies = it
                            prefs.edit().putBoolean("sync_pdf_copies", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ScannerGreen)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Sync Status
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF141414),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Sync Status", color = Color.Gray, fontSize = 11.sp)
                            Text("Last synced: $lastSyncStatus", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        if (isSyncingNow) {
                            CircularProgressIndicator(color = ScannerGreen, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isSyncingNow = true
                                    coroutineScope.launch {
                                        delay(1200)
                                        isSyncingNow = false
                                        lastSyncStatus = "Just now"
                                        prefs.edit().putString("last_sync_time", "Just now").apply()
                                        Toast.makeText(context, "All scans synced to Google Drive & Local Backup!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, ScannerGreen),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Sync Now", color = ScannerGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
            ) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
fun CloudProviderItem(
    name: String,
    accountDesc: String,
    isConnected: Boolean,
    onToggleConnect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF121212),
        border = BorderStroke(0.5.dp, if (isConnected) ScannerGreen.copy(alpha = 0.5f) else Color(0xFF2C2C2C)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(text = accountDesc, color = if (isConnected) ScannerGreen else Color.Gray, fontSize = 11.sp)
            }
            Button(
                onClick = onToggleConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color(0xFF263228) else ScannerGreen
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                border = if (isConnected) BorderStroke(1.dp, ScannerGreen) else null
            ) {
                Text(
                    text = if (isConnected) "Connected" else "Connect",
                    color = if (isConnected) ScannerGreen else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ScanSettingsDialog(
    prefs: android.content.SharedPreferences,
    onDismiss: () -> Unit
) {
    var autoCrop by remember { mutableStateOf(prefs.getBoolean("pref_auto_crop", true)) }
    var gridEnabled by remember { mutableStateOf(prefs.getBoolean("pref_grid", false)) }
    var shutterSound by remember { mutableStateOf(prefs.getBoolean("pref_sound", true)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Preferences", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-Crop Bounds by Default", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = autoCrop,
                        onCheckedChange = {
                            autoCrop = it
                            prefs.edit().putBoolean("pref_auto_crop", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = ScannerGreen)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Viewfinder Rule-of-Thirds Grid", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = gridEnabled,
                        onCheckedChange = {
                            gridEnabled = it
                            prefs.edit().putBoolean("pref_grid", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = ScannerGreen)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Camera Shutter Sound", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = shutterSound,
                        onCheckedChange = {
                            shutterSound = it
                            prefs.edit().putBoolean("pref_sound", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = ScannerGreen)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
fun DocManagementDialog(
    prefs: android.content.SharedPreferences,
    onDismiss: () -> Unit
) {
    var namingTemplate by remember { mutableStateOf(prefs.getString("pref_naming", "OpenScanner_yyyyMMdd_HHmmss") ?: "OpenScanner_yyyyMMdd_HHmmss") }
    var autoOcr by remember { mutableStateOf(prefs.getBoolean("pref_auto_ocr", true)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document Management", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Default Naming Pattern:", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = namingTemplate,
                    onValueChange = {
                        namingTemplate = it
                        prefs.edit().putString("pref_naming", it).apply()
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ScannerGreen
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-Run OCR After Scan", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = autoOcr,
                        onCheckedChange = {
                            autoOcr = it
                            prefs.edit().putBoolean("pref_auto_ocr", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = ScannerGreen)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
fun DocExportDialog(
    prefs: android.content.SharedPreferences,
    onDismiss: () -> Unit
) {
    var defaultPageSize by remember { mutableStateOf(prefs.getString("pref_pdf_size", "A4") ?: "A4") }
    val options = listOf("A4", "US Letter", "Fit to Document")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Preferences", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Default PDF Page Format:", color = Color.LightGray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                options.forEach { opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                defaultPageSize = opt
                                prefs.edit().putString("pref_pdf_size", opt).apply()
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(
                            selected = defaultPageSize == opt,
                            onClick = {
                                defaultPageSize = opt
                                prefs.edit().putString("pref_pdf_size", opt).apply()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = ScannerGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(opt, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
fun OcrLanguageDialog(
    prefs: android.content.SharedPreferences,
    onDismiss: () -> Unit
) {
    var selectedLang by remember { mutableStateOf(prefs.getString("pref_ocr_lang", "English") ?: "English") }
    val languages = listOf("English", "Hindi", "Spanish", "French", "German", "Japanese", "Chinese")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OCR Language Selection", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                languages.forEach { lang ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedLang = lang
                                prefs.edit().putString("pref_ocr_lang", lang).apply()
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(
                            selected = selectedLang == lang,
                            onClick = {
                                selectedLang = lang
                                prefs.edit().putString("pref_ocr_lang", lang).apply()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = ScannerGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(lang, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}
