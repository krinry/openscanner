package org.openscanner.ui.home.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.openscanner.data.model.FolderWithCount
import org.openscanner.ui.theme.ScannerGreen
import org.openscanner.ui.theme.ScannerTheme

enum class MoveCopyAction {
    MOVE,
    COPY
}

@Composable
fun MoveCopyDialog(
    selectedDocCount: Int,
    foldersWithCount: List<FolderWithCount>,
    initialAction: MoveCopyAction = MoveCopyAction.MOVE,
    onDismiss: () -> Unit,
    onCreateFolder: (name: String) -> Unit,
    onConfirm: (action: MoveCopyAction, targetFolderId: Long, folderName: String) -> Unit
) {
    val colors = ScannerTheme.colors
    var currentAction by remember { mutableStateOf(initialAction) }
    var selectedTargetFolderId by remember { mutableStateOf(-1L) } // -1 = Root / All docs
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    val selectedFolderName = remember(selectedTargetFolderId, foldersWithCount) {
        if (selectedTargetFolderId == -1L) {
            "Root (All docs)"
        } else {
            foldersWithCount.find { it.folder.id == selectedTargetFolderId }?.folder?.name ?: "Folder"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.78f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (currentAction == MoveCopyAction.MOVE) "Move to Folder" else "Copy to Folder",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "$selectedDocCount document(s) selected",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Switcher: [ Move ]  |  [ Copy ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2A))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { currentAction = MoveCopyAction.MOVE },
                        shape = RoundedCornerShape(6.dp),
                        color = if (currentAction == MoveCopyAction.MOVE) ScannerGreen else Color.Transparent
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                                contentDescription = null,
                                tint = if (currentAction == MoveCopyAction.MOVE) Color.White else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Move",
                                color = if (currentAction == MoveCopyAction.MOVE) Color.White else Color.Gray,
                                fontWeight = if (currentAction == MoveCopyAction.MOVE) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { currentAction = MoveCopyAction.COPY },
                        shape = RoundedCornerShape(6.dp),
                        color = if (currentAction == MoveCopyAction.COPY) ScannerGreen else Color.Transparent
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = if (currentAction == MoveCopyAction.COPY) Color.White else Color.Gray,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Copy",
                                color = if (currentAction == MoveCopyAction.COPY) Color.White else Color.Gray,
                                fontWeight = if (currentAction == MoveCopyAction.COPY) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Select Destination Label & Create Folder Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT DESTINATION FOLDER",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    TextButton(
                        onClick = { showCreateFolderDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = ScannerGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Folder",
                            color = ScannerGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Folders List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Root / All Docs
                    item {
                        val isRootSelected = selectedTargetFolderId == -1L
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetFolderId = -1L },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isRootSelected) Color(0xFF0D251C) else Color(0xFF262626),
                            border = BorderStroke(
                                width = if (isRootSelected) 1.5.dp else 0.5.dp,
                                color = if (isRootSelected) ScannerGreen else Color(0xFF333333)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isRootSelected) ScannerGreen.copy(alpha = 0.2f) else Color(0xFF333333),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Home,
                                            contentDescription = null,
                                            tint = if (isRootSelected) ScannerGreen else Color.LightGray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Root (All docs)",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Main directory",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                                RadioButton(
                                    selected = isRootSelected,
                                    onClick = { selectedTargetFolderId = -1L },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = ScannerGreen,
                                        unselectedColor = Color.Gray
                                    )
                                )
                            }
                        }
                    }

                    // Existing Folders
                    items(foldersWithCount, key = { it.folder.id }) { fwc ->
                        val isThisSelected = selectedTargetFolderId == fwc.folder.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetFolderId = fwc.folder.id },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isThisSelected) Color(0xFF0D251C) else Color(0xFF262626),
                            border = BorderStroke(
                                width = if (isThisSelected) 1.5.dp else 0.5.dp,
                                color = if (isThisSelected) ScannerGreen else Color(0xFF333333)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isThisSelected) ScannerGreen.copy(alpha = 0.2f) else Color(0xFF333333),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (isThisSelected) ScannerGreen else colors.folderYellow,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fwc.folder.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${fwc.docCount} documents",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                                RadioButton(
                                    selected = isThisSelected,
                                    onClick = { selectedTargetFolderId = fwc.folder.id },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = ScannerGreen,
                                        unselectedColor = Color.Gray
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons
                Button(
                    onClick = {
                        onConfirm(currentAction, selectedTargetFolderId, selectedFolderName)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (currentAction == MoveCopyAction.MOVE) {
                            Icons.AutoMirrored.Outlined.DriveFileMove
                        } else {
                            Icons.Outlined.ContentCopy
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentAction == MoveCopyAction.MOVE) {
                            "Move Here ($selectedFolderName)"
                        } else {
                            "Copy Here ($selectedFolderName)"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // Inline Create New Folder Dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                onCreateFolder(folderName)
                showCreateFolderDialog = false
            }
        )
    }
}
