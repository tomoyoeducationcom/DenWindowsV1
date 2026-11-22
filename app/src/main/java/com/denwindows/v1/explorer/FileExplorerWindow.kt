package com.denwindows.v1.explorer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.denwindows.v1.data.DesktopRepository
import com.denwindows.v1.system.StorageInfo
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.theme.MonoFontFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExplorerSortMode(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Date modified")
}

sealed interface ExplorerLocation {
    data class LocalDir(val dir: File, val title: String) : ExplorerLocation
    data class SafTree(val treeUri: Uri, val documentId: String, val title: String) : ExplorerLocation
}

data class ExplorerEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val localFile: File? = null,
    val safUri: Uri? = null,
    val safDocId: String? = null,
    val mimeType: String? = null
)

data class FileClipboard(
    val file: File,
    val isCut: Boolean
)

@Composable
fun FileExplorerWindow(
    initialPath: String?,
    desktopRepository: DesktopRepository,
    storageInfo: StorageInfo,
    safTreeUris: Set<String>,
    onAddSafTreeUri: (String) -> Unit,
    onOpenTextFileInNotepad: (File) -> Unit,
    onOpenCodeFile: (File) -> Unit,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val denColors = LocalDenColors.current

    val rootQuickDirs = remember(desktopRepository) {
        listOf(
            ExplorerLocation.LocalDir(desktopRepository.desktopDir, "Desktop"),
            ExplorerLocation.LocalDir(desktopRepository.projectsDir, "Projects"),
            ExplorerLocation.LocalDir(desktopRepository.documentsDir, "Documents"),
            ExplorerLocation.LocalDir(desktopRepository.downloadsDir, "Downloads"),
            ExplorerLocation.LocalDir(desktopRepository.picturesDir, "Pictures"),
            ExplorerLocation.LocalDir(desktopRepository.moviesDir, "Movies"),
            ExplorerLocation.LocalDir(desktopRepository.musicDir, "Music"),
            ExplorerLocation.LocalDir(desktopRepository.sandboxRootDir, "DenUser Home")
        )
    }

    val startDir = remember(initialPath) {
        initialPath?.let { File(it) }?.takeIf { it.exists() && it.isDirectory }
            ?: desktopRepository.desktopDir
    }

    var currentLocation by remember(startDir) {
        mutableStateOf<ExplorerLocation>(ExplorerLocation.LocalDir(startDir, startDir.name))
    }
    var refreshTick by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(ExplorerSortMode.NAME) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<ExplorerEntry?>(null) }
    var clipboard by remember { mutableStateOf<FileClipboard?>(null) }
    var showDetailsPane by remember { mutableStateOf(true) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderInput by remember { mutableStateOf("New Folder") }
    var renameTarget by remember { mutableStateOf<ExplorerEntry?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onAddSafTreeUri(uri.toString())
            val docId = DocumentsContract.getTreeDocumentId(uri)
            currentLocation = ExplorerLocation.SafTree(uri, docId, uri.lastPathSegment ?: "Mounted Folder")
            onStatusMessage("Mounted external folder via Storage Access Framework.")
        }
    }

    val entries = remember(currentLocation, refreshTick, searchQuery, sortMode) {
        val raw = when (val loc = currentLocation) {
            is ExplorerLocation.LocalDir -> listLocalEntries(loc.dir)
            is ExplorerLocation.SafTree -> listSafEntries(context, loc.treeUri, loc.documentId)
        }
        val filtered = if (searchQuery.isBlank()) raw else {
            val q = searchQuery.trim().lowercase()
            raw.filter { it.name.lowercase().contains(q) }
        }
        filtered.sortedWith(
            compareByDescending<ExplorerEntry> { it.isDirectory }.thenBy {
                when (sortMode) {
                    ExplorerSortMode.NAME -> it.name.lowercase()
                    ExplorerSortMode.SIZE -> "%020d".format(it.sizeBytes)
                    ExplorerSortMode.DATE -> "%020d".format(Long.MAX_VALUE - it.lastModified)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(denColors.windowSurface)) {
        // Command Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(denColors.secondarySurface.copy(alpha = 0.65f))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val localDir = (currentLocation as? ExplorerLocation.LocalDir)?.dir
            IconButton(
                onClick = {
                    val parent = localDir?.parentFile
                    if (parent != null && parent.canRead()) {
                        currentLocation = ExplorerLocation.LocalDir(parent, parent.name)
                        selectedEntry = null
                    }
                },
                enabled = localDir?.parentFile?.canRead() == true,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", tint = denColors.textPrimary, modifier = Modifier.size(17.dp))
            }

            IconButton(
                onClick = {
                    newFolderInput = "New Folder"
                    showNewFolderDialog = true
                },
                enabled = localDir != null,
                modifier = Modifier.size(32.dp).testTag("explorer_new_folder")
            ) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder", tint = denColors.accent, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = {
                    selectedEntry?.localFile?.let {
                        clipboard = FileClipboard(it, isCut = true)
                        onStatusMessage("Cut ${it.name}")
                    }
                },
                enabled = selectedEntry?.localFile != null,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.ContentCut, contentDescription = "Cut", tint = denColors.textPrimary, modifier = Modifier.size(17.dp))
            }

            IconButton(
                onClick = {
                    selectedEntry?.localFile?.let {
                        clipboard = FileClipboard(it, isCut = false)
                        onStatusMessage("Copied ${it.name}")
                    }
                },
                enabled = selectedEntry?.localFile != null,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = denColors.textPrimary, modifier = Modifier.size(17.dp))
            }

            IconButton(
                onClick = {
                    val clip = clipboard
                    if (clip != null && localDir != null) {
                        val dest = uniqueTargetFile(localDir, clip.file.name)
                        runCatching {
                            if (clip.isCut) {
                                clip.file.copyRecursively(dest, overwrite = true)
                                clip.file.deleteRecursively()
                                clipboard = null
                            } else {
                                clip.file.copyRecursively(dest, overwrite = true)
                            }
                            refreshTick++
                            onStatusMessage("Pasted ${dest.name}")
                        }.onFailure {
                            onStatusMessage("Paste failed: ${it.localizedMessage}")
                        }
                    }
                },
                enabled = clipboard != null && localDir != null,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = denColors.textPrimary, modifier = Modifier.size(17.dp))
            }

            IconButton(
                onClick = {
                    selectedEntry?.let { entry ->
                        renameTarget = entry
                        renameInput = entry.name
                    }
                },
                enabled = selectedEntry?.localFile != null,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename", tint = denColors.textPrimary, modifier = Modifier.size(17.dp))
            }

            IconButton(
                onClick = {
                    selectedEntry?.localFile?.let { f ->
                        if (f.deleteRecursively()) {
                            selectedEntry = null
                            refreshTick++
                            onStatusMessage("Deleted ${f.name}")
                        }
                    }
                },
                enabled = selectedEntry?.localFile != null,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = denColors.error, modifier = Modifier.size(17.dp))
            }

            IconButton(
                onClick = {
                    selectedEntry?.localFile?.let { shareLocalFile(context, it) }
                },
                enabled = selectedEntry?.localFile?.isFile == true,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = denColors.textPrimary, modifier = Modifier.size(17.dp))
            }

            Box {
                IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = denColors.textPrimary, modifier = Modifier.size(17.dp))
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    ExplorerSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text("Sort by ${mode.label}") },
                            onClick = {
                                sortMode = mode
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = { showDetailsPane = !showDetailsPane }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Info, contentDescription = "Toggle Details", tint = if (showDetailsPane) denColors.accent else denColors.textSecondary, modifier = Modifier.size(17.dp))
            }

            Spacer(Modifier.width(6.dp))

            // Inline Search Filter
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(denColors.windowSurface)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = denColors.textSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = denColors.textPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Breadcrumb Path Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(denColors.windowSurface)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val pathText = when (val loc = currentLocation) {
                is ExplorerLocation.LocalDir -> loc.dir.absolutePath
                is ExplorerLocation.SafTree -> "SAF://${loc.title}"
            }
            Text(
                text = pathText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                color = denColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { safPickerLauncher.launch(null) },
                modifier = Modifier.height(28.dp)
            ) {
                Text("+ Mount Storage Folder", fontSize = 11.sp)
            }
        }

        // Main Split Body: Left Navigation Rail + File List + Optional Details Pane
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Left Sidebar
            Column(
                modifier = Modifier
                    .width(154.dp)
                    .fillMaxHeight()
                    .background(denColors.secondarySurface.copy(alpha = 0.45f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Quick Access", style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary, modifier = Modifier.padding(4.dp))
                rootQuickDirs.forEach { loc ->
                    val active = (currentLocation as? ExplorerLocation.LocalDir)?.dir?.absolutePath == loc.dir.absolutePath
                    val icon = when (loc.title) {
                        "Downloads" -> Icons.Filled.Download
                        "Pictures" -> Icons.Filled.Image
                        "Movies" -> Icons.Filled.Movie
                        "Music" -> Icons.Filled.MusicNote
                        else -> Icons.Filled.Folder
                    }
                    SidebarRow(
                        label = loc.title,
                        icon = icon,
                        active = active,
                        onClick = {
                            currentLocation = loc
                            selectedEntry = null
                        }
                    )
                }

                if (safTreeUris.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Mounted Storage", style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary, modifier = Modifier.padding(4.dp))
                    safTreeUris.forEach { rawUri ->
                        val parsed = Uri.parse(rawUri)
                        val title = parsed.lastPathSegment?.substringAfterLast(':') ?: "External"
                        SidebarRow(
                            label = title,
                            icon = Icons.Filled.FolderOpen,
                            active = (currentLocation as? ExplorerLocation.SafTree)?.treeUri.toString() == rawUri,
                            onClick = {
                                val docId = DocumentsContract.getTreeDocumentId(parsed)
                                currentLocation = ExplorerLocation.SafTree(parsed, docId, title)
                                selectedEntry = null
                            }
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Storage Gauge
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(denColors.windowSurface.copy(alpha = 0.7f))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, null, tint = denColors.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Device Storage", style = MaterialTheme.typography.labelSmall, color = denColors.textPrimary)
                    }
                    Spacer(Modifier.height(5.dp))
                    val usedFrac = if (storageInfo.totalBytes > 0) {
                        (storageInfo.usedBytes.toFloat() / storageInfo.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = { usedFrac },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = denColors.accent
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${formatBytes(storageInfo.freeBytes)} free",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = denColors.textSecondary
                    )
                }
            }

            // Center File List
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                if (entries.isEmpty()) {
                    Text(
                        "This folder is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = denColors.textSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(entries, key = { it.localFile?.absolutePath ?: it.safUri?.toString() ?: it.name }) { entry ->
                            val isSelected = selectedEntry == entry
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (isSelected) denColors.accentContainer.copy(alpha = 0.75f) else Color.Transparent)
                                    .clickable {
                                        if (isSelected) {
                                            openExplorerEntry(
                                                context = context,
                                                entry = entry,
                                                currentLocation = currentLocation,
                                                onNavigate = {
                                                    currentLocation = it
                                                    selectedEntry = null
                                                },
                                                onOpenText = onOpenTextFileInNotepad,
                                                onError = onStatusMessage
                                            )
                                        } else {
                                            selectedEntry = entry
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = entryIcon(entry),
                                    contentDescription = null,
                                    tint = if (entry.isDirectory) Color(0xFFF3C15A) else denColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = denColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (entry.isDirectory) "Folder" else formatBytes(entry.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = denColors.textSecondary,
                                    modifier = Modifier.width(72.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Right File Details Pane
            if (showDetailsPane) {
                Column(
                    modifier = Modifier
                        .width(165.dp)
                        .fillMaxHeight()
                        .background(denColors.secondarySurface.copy(alpha = 0.35f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Details", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                    val sel = selectedEntry
                    if (sel == null) {
                        Text(
                            "${entries.size} item(s)\nTap to select, tap again to open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = denColors.textSecondary
                        )
                    } else {
                        Icon(
                            imageVector = entryIcon(sel),
                            contentDescription = null,
                            tint = if (sel.isDirectory) Color(0xFFF3C15A) else denColors.accent,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(sel.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = denColors.textPrimary)
                        Text(
                            if (sel.isDirectory) "Directory" else "Size: ${formatBytes(sel.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = denColors.textSecondary
                        )
                        if (sel.lastModified > 0L) {
                            val dateStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(sel.lastModified))
                            Text("Modified: $dateStr", style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                openExplorerEntry(
                                    context = context,
                                    entry = sel,
                                    currentLocation = currentLocation,
                                    onNavigate = {
                                        currentLocation = it
                                        selectedEntry = null
                                    },
                                    onOpenText = onOpenTextFileInNotepad,
                                    onError = onStatusMessage
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open", fontSize = 12.sp)
                        }
                        val localSel = sel.localFile
                        if (localSel != null) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { onOpenCodeFile(localSel) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open in DenCode", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        val dir = (currentLocation as? ExplorerLocation.LocalDir)?.dir
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderInput,
                    onValueChange = { newFolderInput = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    showNewFolderDialog = false
                    if (dir != null) {
                        val safe = newFolderInput.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "New Folder" }
                        uniqueTargetFile(dir, safe).mkdirs()
                        refreshTick++
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val file = target.localFile
                    val safe = renameInput.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    if (file != null && safe.isNotBlank() && file.parentFile != null) {
                        val dest = File(file.parentFile, safe)
                        if (file.renameTo(dest)) {
                            refreshTick++
                            selectedEntry = null
                        }
                    }
                    renameTarget = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SidebarRow(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    val denColors = LocalDenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) denColors.accentContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) denColors.accent else denColors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) denColors.textPrimary else denColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun entryIcon(entry: ExplorerEntry): ImageVector {
    if (entry.isDirectory) return Icons.Filled.Folder
    val ext = entry.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "png", "jpg", "jpeg", "webp", "gif" -> Icons.Filled.Image
        "mp4", "mkv", "webm", "mov" -> Icons.Filled.Movie
        "mp3", "wav", "ogg", "flac", "m4a" -> Icons.Filled.MusicNote
        else -> Icons.Filled.Description
    }
}

private fun listLocalEntries(dir: File): List<ExplorerEntry> {
    val files = dir.listFiles() ?: return emptyList()
    return files.map { f ->
        ExplorerEntry(
            name = f.name,
            isDirectory = f.isDirectory,
            sizeBytes = if (f.isDirectory) 0L else f.length(),
            lastModified = f.lastModified(),
            localFile = f
        )
    }
}

private fun listSafEntries(context: Context, treeUri: Uri, parentDocId: String): List<ExplorerEntry> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )
    val result = mutableListOf<ExplorerEntry>()
    runCatching {
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: "Unknown"
                val mime = cursor.getString(mimeIdx) ?: ""
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                val mod = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                result.add(
                    ExplorerEntry(
                        name = name,
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModified = mod,
                        safUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                        safDocId = docId,
                        mimeType = mime
                    )
                )
            }
        }
    }
    return result
}

private fun openExplorerEntry(
    context: Context,
    entry: ExplorerEntry,
    currentLocation: ExplorerLocation,
    onNavigate: (ExplorerLocation) -> Unit,
    onOpenText: (File) -> Unit,
    onError: (String) -> Unit
) {
    if (entry.isDirectory) {
        if (entry.localFile != null) {
            onNavigate(ExplorerLocation.LocalDir(entry.localFile, entry.name))
        } else if (entry.safDocId != null && currentLocation is ExplorerLocation.SafTree) {
            onNavigate(ExplorerLocation.SafTree(currentLocation.treeUri, entry.safDocId, entry.name))
        }
        return
    }

    val local = entry.localFile
    if (local != null) {
        val ext = local.extension.lowercase()
        if (ext in setOf("txt", "md", "json", "csv", "log", "xml", "kt", "ini", "conf")) {
            onOpenText(local)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", local)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { onError("No Android app installed to open .${ext.ifEmpty { "file" }}") }
    } else if (entry.safUri != null) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(entry.safUri, entry.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { onError("Unable to open SAF document.") }
    }
}

private fun shareLocalFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Share ${file.name}").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}

private fun uniqueTargetFile(dir: File, name: String): File {
    var candidate = File(dir, name)
    var index = 2
    val base = name.substringBeforeLast('.', name)
    val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
    while (candidate.exists()) {
        candidate = File(dir, "$base ($index)$ext")
        index++
    }
    return candidate
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
