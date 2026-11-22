package com.denwindows.v1.utilities

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.theme.MonoFontFamily
import java.io.File

@Composable
fun NotepadApp(
    initialFilePath: String?,
    documentsDir: File,
    onTitleUpdate: (String) -> Unit,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val denColors = LocalDenColors.current
    val clipboard = LocalClipboardManager.current

    var currentFile by remember { mutableStateOf<File?>(null) }
    var currentSafUri by remember { mutableStateOf<Uri?>(null) }
    var content by remember { mutableStateOf("") }
    var isDirty by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("note.txt") }

    LaunchedEffect(initialFilePath) {
        if (!initialFilePath.isNullOrBlank()) {
            val file = File(initialFilePath)
            if (file.exists() && file.isFile) {
                runCatching {
                    content = file.readText()
                    currentFile = file
                    currentSafUri = null
                    isDirty = false
                    onTitleUpdate("Notepad - ${file.name}")
                }
            }
        }
    }

    val openSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                content = text
                currentSafUri = uri
                currentFile = null
                isDirty = false
                onTitleUpdate("Notepad - External File")
            }.onFailure {
                onStatusMessage("Unable to open selected file.")
            }
        }
    }

    val createSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                currentSafUri = uri
                currentFile = null
                isDirty = false
                onStatusMessage("Saved document via Android Storage Access Framework.")
            }.onFailure {
                onStatusMessage("Failed to save document.")
            }
        }
    }

    fun saveCurrent() {
        val file = currentFile
        val uri = currentSafUri
        when {
            file != null -> {
                runCatching {
                    file.writeText(content)
                    isDirty = false
                    onTitleUpdate("Notepad - ${file.name}")
                    onStatusMessage("Saved ${file.name}")
                }.onFailure { onStatusMessage("Save failed: ${it.localizedMessage}") }
            }
            uri != null -> {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(content) }
                    isDirty = false
                    onStatusMessage("Saved external document.")
                }.onFailure { onStatusMessage("Save failed: ${it.localizedMessage}") }
            }
            else -> {
                saveAsName = "note_${System.currentTimeMillis() % 10000}.txt"
                showSaveAsDialog = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(denColors.windowSurface)) {
        // Menu Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(denColors.secondarySurface.copy(alpha = 0.65f))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(onClick = {
                currentFile = null
                currentSafUri = null
                content = ""
                isDirty = false
                onTitleUpdate("Notepad - Untitled")
            }) { Text("New", fontSize = 12.sp) }

            TextButton(onClick = {
                openSafLauncher.launch(arrayOf("text/*", "application/json", "application/xml"))
            }) { Text("Open", fontSize = 12.sp) }

            TextButton(
                onClick = { saveCurrent() },
                modifier = Modifier.testTag("notepad_save_button")
            ) { Text("Save", fontSize = 12.sp) }

            TextButton(onClick = {
                saveAsName = currentFile?.name ?: "document.txt"
                showSaveAsDialog = true
            }) { Text("Save As", fontSize = 12.sp) }

            TextButton(onClick = { createSafLauncher.launch("note.txt") }) {
                Text("Export", fontSize = 12.sp)
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = {
                clipboard.setText(AnnotatedString(content))
                onStatusMessage("Copied document text to clipboard.")
            }) { Text("Copy All", fontSize = 12.sp) }

            TextButton(onClick = {
                val clip = clipboard.getText()?.text
                if (!clip.isNullOrEmpty()) {
                    content += clip
                    isDirty = true
                }
            }) { Text("Paste", fontSize = 12.sp) }
        }

        // Editor Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (content.isEmpty()) {
                Text(
                    "Start typing a note or document...",
                    style = TextStyle(fontFamily = MonoFontFamily, fontSize = 13.sp, color = denColors.textSecondary)
                )
            }
            BasicTextField(
                value = content,
                onValueChange = {
                    content = it
                    isDirty = true
                },
                textStyle = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = denColors.textPrimary
                ),
                cursorBrush = SolidColor(denColors.accent),
                modifier = Modifier.fillMaxSize().testTag("notepad_editor")
            )
        }

        // Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(denColors.secondarySurface.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val fileLabel = currentFile?.name ?: if (currentSafUri != null) "External SAF Document" else "Untitled"
            Text(
                text = if (isDirty) "$fileLabel • Unsaved changes" else fileLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDirty) denColors.warning else denColors.textSecondary
            )
            val lines = if (content.isEmpty()) 1 else content.count { it == '\n' } + 1
            Text(
                text = "Ln $lines • ${content.length} chars",
                style = MaterialTheme.typography.labelSmall,
                color = denColors.textSecondary
            )
        }
    }

    if (showSaveAsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text("Save to Documents") },
            text = {
                OutlinedTextField(
                    value = saveAsName,
                    onValueChange = { saveAsName = it },
                    label = { Text("File name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    showSaveAsDialog = false
                    val safe = saveAsName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "note.txt" }
                    val target = File(documentsDir, safe)
                    runCatching {
                        target.writeText(content)
                        currentFile = target
                        currentSafUri = null
                        isDirty = false
                        onTitleUpdate("Notepad - ${target.name}")
                        onStatusMessage("Saved to Documents/${target.name}")
                    }.onFailure {
                        onStatusMessage("Save failed: ${it.localizedMessage}")
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) { Text("Cancel") }
            }
        )
    }
}
