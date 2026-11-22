package com.denwindows.v1.code

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.denwindows.v1.terminal.TerminalCommandParser
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.theme.MonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private enum class IdeActivityTab { EXPLORER, SEARCH, OUTLINE, PROBLEMS }
private enum class IdeBottomTab { PREVIEW, TERMINAL, PROBLEMS }

data class CodeEditorTab(
    val id: String = UUID.randomUUID().toString(),
    val file: File?,
    val title: String,
    val language: CodeLanguage,
    val value: TextFieldValue,
    val isDirty: Boolean = false
)

data class WorkspaceSearchMatch(
    val file: File,
    val line: Int,
    val snippet: String
)

@Composable
fun CodeEditorWindow(
    initialPath: String?,
    projectsRootDir: File,
    onTitleUpdate: (String) -> Unit,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val denColors = LocalDenColors.current
    val scope = rememberCoroutineScope()

    val starterDir = remember(projectsRootDir) {
        File(projectsRootDir, "StarterWeb").apply { if (!exists()) mkdirs() }
    }

    var workspaceDir by remember(initialPath, starterDir) {
        val candidate = initialPath?.let { File(it) }
        mutableStateOf(
            when {
                candidate != null && candidate.isDirectory -> candidate
                candidate != null && candidate.isFile && candidate.parentFile != null -> candidate.parentFile!!
                else -> starterDir
            }
        )
    }

    val openTabs = remember { mutableStateListOf<CodeEditorTab>() }
    var activeTabId by remember { mutableStateOf<String?>(null) }

    var sidebarTab by remember { mutableStateOf(IdeActivityTab.EXPLORER) }
    var showSidebar by remember { mutableStateOf(true) }
    var bottomTab by remember { mutableStateOf(IdeBottomTab.PREVIEW) }
    var showBottomPanel by remember { mutableStateOf(true) }
    var wordWrap by remember { mutableStateOf(false) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showCommandPalette by remember { mutableStateOf(false) }
    var commandPaletteQuery by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("script.js") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("src") }
    var workspaceSearchQuery by remember { mutableStateOf("") }
    var refreshTick by remember { mutableIntStateOf(0) }
    var previewVersion by remember { mutableIntStateOf(0) }

    // Integrated terminal output
    val terminalLines = remember {
        mutableStateListOf(
            "DenCode Integrated Shell — workspace: ${workspaceDir.name}",
            "Run permitted Android shell commands or click 'Run' on .sh / .html files."
        )
    }
    var terminalInput by remember { mutableStateOf("") }

    fun openFileInTab(file: File) {
        if (!file.exists() || !file.isFile) return
        val existing = openTabs.firstOrNull { it.file?.absolutePath == file.absolutePath }
        if (existing != null) {
            activeTabId = existing.id
            onTitleUpdate("DenCode - ${existing.title}")
            return
        }
        val text = runCatching { file.readText() }.getOrDefault("")
        val lang = CodeLanguage.fromFileName(file.name)
        val tab = CodeEditorTab(
            file = file,
            title = file.name,
            language = lang,
            value = TextFieldValue(text)
        )
        openTabs.add(tab)
        activeTabId = tab.id
        onTitleUpdate("DenCode - ${file.name}")
    }

    // Seed starter project once and open initial file
    remember(starterDir, initialPath) {
        seedStarterProjectIfEmpty(starterDir)
        if (openTabs.isEmpty()) {
            val target = initialPath?.let { File(it) }?.takeIf { it.isFile }
                ?: File(starterDir, "index.html").takeIf { it.exists() }
                ?: workspaceDir.listFiles()?.firstOrNull { it.isFile }
            if (target != null) openFileInTab(target)
        }
        true
    }

    val openSafDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "external.txt"
                val tab = CodeEditorTab(
                    file = null,
                    title = name,
                    language = CodeLanguage.fromFileName(name),
                    value = TextFieldValue(text),
                    isDirty = false
                )
                openTabs.add(tab)
                activeTabId = tab.id
                onTitleUpdate("DenCode - $name")
            }.onFailure { onStatusMessage("Unable to open file.") }
        }
    }

    val activeTab = openTabs.firstOrNull { it.id == activeTabId }

    fun updateActiveTab(transform: (CodeEditorTab) -> CodeEditorTab) {
        val idx = openTabs.indexOfFirst { it.id == activeTabId }
        if (idx >= 0) openTabs[idx] = transform(openTabs[idx])
    }

    fun saveActiveTab() {
        val tab = activeTab ?: return
        val file = tab.file
        if (file != null) {
            runCatching {
                file.writeText(tab.value.text)
                updateActiveTab { it.copy(isDirty = false) }
                previewVersion++
                onStatusMessage("Saved ${file.name}")
            }.onFailure {
                onStatusMessage("Failed to save ${file.name}: ${it.localizedMessage}")
            }
        } else {
            newFileName = tab.title
            showNewFileDialog = true
        }
    }

    fun insertTextAtCursor(snippet: String) {
        val tab = activeTab ?: return
        val tv = tab.value
        val start = tv.selection.min.coerceIn(0, tv.text.length)
        val end = tv.selection.max.coerceIn(0, tv.text.length)
        val newText = tv.text.replaceRange(start, end, snippet)
        val cursor = start + snippet.length
        updateActiveTab {
            it.copy(
                value = TextFieldValue(newText, selection = TextRange(cursor)),
                isDirty = true
            )
        }
    }

    fun formatActiveJson() {
        val tab = activeTab ?: return
        if (tab.language != CodeLanguage.JSON) {
            onStatusMessage("Format currently supports JSON files.")
            return
        }
        runCatching {
            val trimmed = tab.value.text.trim()
            val formatted = if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toString(2)
            } else {
                JSONObject(trimmed).toString(2)
            }
            updateActiveTab {
                it.copy(value = TextFieldValue(formatted, TextRange(0)), isDirty = true)
            }
            onStatusMessage("Formatted JSON.")
        }.onFailure {
            onStatusMessage("Cannot format invalid JSON: ${it.localizedMessage}")
        }
    }

    fun runIntegratedShellCommand(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        terminalLines.add("${workspaceDir.name}$ $trimmed")
        val tokens = TerminalCommandParser.tokenize(trimmed)
        if (tokens.isEmpty()) return
        val cmd = tokens.first()

        when (cmd) {
            "clear" -> terminalLines.clear()
            "pwd" -> terminalLines.add(workspaceDir.absolutePath)
            else -> {
                val allowed = TerminalCommandParser.isPermittedExternalCommand(cmd) || cmd == "sh"
                if (!allowed) {
                    terminalLines.add("Unsupported command '$cmd'. Allowed: ls, cat, grep, wc, find, sh <file.sh>, getprop, uname, df, date")
                    return
                }
                scope.launch {
                    val output = withContext(Dispatchers.IO) {
                        runCatching {
                            val execTokens = if (cmd == "sh") listOf("/system/bin/sh") + tokens.drop(1) else tokens
                            val p = ProcessBuilder(execTokens).directory(workspaceDir).start()
                            withTimeoutOrNull(8000L) {
                                val out = p.inputStream.bufferedReader().readLines()
                                val err = p.errorStream.bufferedReader().readLines()
                                p.waitFor()
                                out + err
                            } ?: run {
                                p.destroyForcibly()
                                listOf("Timed out after 8s.")
                            }
                        }.getOrElse { listOf("Error: ${it.localizedMessage}") }
                    }
                    terminalLines.addAll(output.ifEmpty { listOf("(done)") })
                }
            }
        }
    }

    fun runActiveFile() {
        val tab = activeTab ?: return
        saveActiveTab()
        when (tab.language) {
            CodeLanguage.HTML, CodeLanguage.CSS, CodeLanguage.JAVASCRIPT -> {
                bottomTab = IdeBottomTab.PREVIEW
                showBottomPanel = true
                previewVersion++
                onStatusMessage("Updated Live Web Preview.")
            }
            CodeLanguage.SHELL -> {
                val target = tab.file
                if (target != null) {
                    bottomTab = IdeBottomTab.TERMINAL
                    showBottomPanel = true
                    runIntegratedShellCommand("sh ${target.name}")
                }
            }
            CodeLanguage.JSON -> {
                bottomTab = IdeBottomTab.PROBLEMS
                showBottomPanel = true
                val probs = CodeSyntaxEngine.validateDiagnostics(tab.value.text, tab.language)
                onStatusMessage(if (probs.isEmpty()) "JSON valid: 0 problems." else "Found ${probs.size} JSON error(s).")
            }
            else -> {
                bottomTab = IdeBottomTab.TERMINAL
                showBottomPanel = true
                terminalLines.add("Saved ${tab.title} (${tab.language.label}). Note: Native Kotlin/Python compilers are not bundled on-device.")
            }
        }
    }

    val workspaceFiles = remember(workspaceDir, refreshTick) {
        collectWorkspaceFiles(workspaceDir)
    }

    val activeProblems = remember(activeTab?.value?.text, activeTab?.language) {
        val t = activeTab ?: return@remember emptyList()
        CodeSyntaxEngine.validateDiagnostics(t.value.text, t.language)
    }

    val activeSymbols = remember(activeTab?.value?.text, activeTab?.language) {
        val t = activeTab ?: return@remember emptyList()
        CodeSyntaxEngine.extractSymbols(t.value.text, t.language)
    }

    val searchMatches = remember(workspaceSearchQuery, workspaceFiles, refreshTick) {
        val q = workspaceSearchQuery.trim()
        if (q.length < 2) emptyList()
        else {
            workspaceFiles.filter { it.isFile && it.length() < 120_000L }.flatMap { file ->
                runCatching {
                    file.readLines().mapIndexedNotNull { idx, line ->
                        if (line.contains(q, ignoreCase = true)) {
                            WorkspaceSearchMatch(file, idx + 1, line.trim())
                        } else null
                    }
                }.getOrDefault(emptyList())
            }.take(50)
        }
    }

    val editorBg = if (denColors.isDark) Color(0xFF141820) else Color(0xFFFAFBFD)
    val sidebarBg = if (denColors.isDark) Color(0xFF181D26) else Color(0xFFF0F3F8)
    val activityBg = if (denColors.isDark) Color(0xFF10141B) else Color(0xFFE4E9F2)

    Column(modifier = Modifier.fillMaxSize().background(editorBg)) {
        // Main IDE Body: Activity Bar + Primary Side Bar + Editor/Bottom Split
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 1. VS Code-style Activity Bar
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .background(activityBg)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ActivityIconButton(
                    icon = Icons.Filled.Description,
                    label = "Explorer",
                    selected = showSidebar && sidebarTab == IdeActivityTab.EXPLORER,
                    onClick = {
                        if (showSidebar && sidebarTab == IdeActivityTab.EXPLORER) showSidebar = false
                        else {
                            sidebarTab = IdeActivityTab.EXPLORER
                            showSidebar = true
                        }
                    }
                )
                ActivityIconButton(
                    icon = Icons.Filled.Search,
                    label = "Search Workspace",
                    selected = showSidebar && sidebarTab == IdeActivityTab.SEARCH,
                    onClick = {
                        if (showSidebar && sidebarTab == IdeActivityTab.SEARCH) showSidebar = false
                        else {
                            sidebarTab = IdeActivityTab.SEARCH
                            showSidebar = true
                        }
                    }
                )
                ActivityIconButton(
                    icon = Icons.Filled.AccountTree,
                    label = "Outline Symbols",
                    selected = showSidebar && sidebarTab == IdeActivityTab.OUTLINE,
                    onClick = {
                        if (showSidebar && sidebarTab == IdeActivityTab.OUTLINE) showSidebar = false
                        else {
                            sidebarTab = IdeActivityTab.OUTLINE
                            showSidebar = true
                        }
                    }
                )
                ActivityIconButton(
                    icon = Icons.Filled.BugReport,
                    label = "Problems",
                    selected = showSidebar && sidebarTab == IdeActivityTab.PROBLEMS,
                    onClick = {
                        if (showSidebar && sidebarTab == IdeActivityTab.PROBLEMS) showSidebar = false
                        else {
                            sidebarTab = IdeActivityTab.PROBLEMS
                            showSidebar = true
                        }
                    }
                )
                Spacer(Modifier.weight(1f))
                ActivityIconButton(
                    icon = Icons.Filled.Code,
                    label = "Command Palette",
                    selected = showCommandPalette,
                    onClick = { showCommandPalette = true }
                )
            }

            // 2. Primary Side Bar
            if (showSidebar) {
                Column(
                    modifier = Modifier
                        .width(196.dp)
                        .fillMaxHeight()
                        .background(sidebarBg)
                        .padding(8.dp)
                ) {
                    when (sidebarTab) {
                        IdeActivityTab.EXPLORER -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    workspaceDir.name.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = denColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            newFileName = "untitled.js"
                                            showNewFileDialog = true
                                        },
                                        modifier = Modifier.size(24.dp).testTag("dencode_new_file")
                                    ) {
                                        Icon(Icons.Filled.Add, "New File", tint = denColors.textPrimary, modifier = Modifier.size(15.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            newFolderName = "folder"
                                            showNewFolderDialog = true
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.CreateNewFolder, "New Folder", tint = denColors.textPrimary, modifier = Modifier.size(14.dp))
                                    }
                                    IconButton(
                                        onClick = { openSafDocLauncher.launch(arrayOf("*/*")) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.FolderOpen, "Open External File", tint = denColors.accent, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Project Switcher Pills
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "StarterWeb" to starterDir,
                                    "Projects" to projectsRootDir,
                                    "Documents" to File(projectsRootDir.parentFile, "Documents")
                                ).forEach { (label, dir) ->
                                    if (dir.exists()) {
                                        val isCur = workspaceDir.absolutePath == dir.absolutePath
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = if (isCur) Color.White else denColors.textSecondary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isCur) denColors.accent else denColors.secondarySurface)
                                                .clickable {
                                                    workspaceDir = dir
                                                    refreshTick++
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(workspaceFiles, key = { it.absolutePath }) { file ->
                                    val isActive = activeTab?.file?.absolutePath == file.absolutePath
                                    val relDepth = file.relativeTo(workspaceDir).path.count { it == File.separatorChar }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(if (isActive) denColors.accentContainer else Color.Transparent)
                                            .clickable {
                                                if (file.isDirectory) {
                                                    workspaceDir = file
                                                    refreshTick++
                                                } else {
                                                    openFileInTab(file)
                                                }
                                            }
                                            .padding(start = (6 + relDepth * 10).dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                                            contentDescription = null,
                                            tint = if (file.isDirectory) Color(0xFFF3C15A) else languageBadgeColor(CodeLanguage.fromFileName(file.name)),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = denColors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (file.isFile) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete file",
                                                tint = denColors.textSecondary.copy(alpha = 0.55f),
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clickable {
                                                        if (file.delete()) {
                                                            openTabs.removeAll { it.file?.absolutePath == file.absolutePath }
                                                            if (activeTabId !in openTabs.map { it.id }) {
                                                                activeTabId = openTabs.firstOrNull()?.id
                                                            }
                                                            refreshTick++
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IdeActivityTab.SEARCH -> {
                            Text("SEARCH IN WORKSPACE", style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = workspaceSearchQuery,
                                onValueChange = { workspaceSearchQuery = it },
                                placeholder = { Text("Find in files...", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(searchMatches) { m ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(denColors.windowSurface)
                                            .clickable { openFileInTab(m.file) }
                                            .padding(6.dp)
                                    ) {
                                        Text("${m.file.name}:${m.line}", style = MaterialTheme.typography.labelSmall, color = denColors.accent)
                                        Text(m.snippet, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily, fontSize = 10.sp), color = denColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        IdeActivityTab.OUTLINE -> {
                            Text("SYMBOL OUTLINE", style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary)
                            Spacer(Modifier.height(6.dp))
                            if (activeSymbols.isEmpty()) {
                                Text("No symbols found in active file.", style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(activeSymbols) { sym ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(denColors.windowSurface)
                                                .clickable {
                                                    val tab = activeTab ?: return@clickable
                                                    val lines = tab.value.text.lines()
                                                    val offset = lines.take((sym.line - 1).coerceAtLeast(0)).sumOf { it.length + 1 }
                                                        .coerceIn(0, tab.value.text.length)
                                                    updateActiveTab {
                                                        it.copy(value = it.value.copy(selection = TextRange(offset)))
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(sym.name, style = MaterialTheme.typography.bodySmall, color = denColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Text("${sym.kind} :${sym.line}", style = MaterialTheme.typography.labelSmall, color = denColors.accent)
                                        }
                                    }
                                }
                            }
                        }

                        IdeActivityTab.PROBLEMS -> {
                            Text("PROBLEMS (${activeProblems.size})", style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary)
                            Spacer(Modifier.height(6.dp))
                            if (activeProblems.isEmpty()) {
                                Text("No syntax problems detected.", style = MaterialTheme.typography.bodySmall, color = denColors.success)
                            } else {
                                activeProblems.forEach { p ->
                                    Text(
                                        "Line ${p.line}: ${p.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = denColors.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Center Column: Editor Tabs + Toolbar + Code Canvas + Bottom Panel
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Editor Tab Bar & Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(35.dp)
                        .background(sidebarBg)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        openTabs.forEach { tab ->
                            val selected = tab.id == activeTabId
                            Row(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(if (selected) editorBg else Color.Transparent)
                                    .clickable {
                                        activeTabId = tab.id
                                        onTitleUpdate("DenCode - ${tab.title}")
                                    }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(languageBadgeColor(tab.language))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (tab.isDirty) "${tab.title} ●" else tab.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selected) denColors.textPrimary else denColors.textSecondary
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Close tab",
                                    tint = denColors.textSecondary,
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clickable {
                                            val idx = openTabs.indexOfFirst { it.id == tab.id }
                                            openTabs.removeAll { it.id == tab.id }
                                            if (activeTabId == tab.id) {
                                                activeTabId = openTabs.getOrNull((idx - 1).coerceAtLeast(0))?.id
                                            }
                                        }
                                )
                            }
                        }
                    }

                    // Editor Actions
                    IconButton(onClick = { runActiveFile() }, modifier = Modifier.size(28.dp).testTag("dencode_run_button")) {
                        Icon(Icons.Filled.PlayArrow, "Run / Preview", tint = denColors.success, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { saveActiveTab() }, modifier = Modifier.size(28.dp).testTag("dencode_save_button")) {
                        Icon(Icons.Filled.Save, "Save", tint = denColors.accent, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { showFindBar = !showFindBar }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.FindReplace, "Find & Replace", tint = if (showFindBar) denColors.accent else denColors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { wordWrap = !wordWrap }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.WrapText, "Toggle Word Wrap", tint = if (wordWrap) denColors.accent else denColors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { showBottomPanel = !showBottomPanel }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Terminal, "Toggle Bottom Panel", tint = if (showBottomPanel) denColors.accent else denColors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                // Optional Find & Replace Bar
                if (showFindBar && activeTab != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(denColors.secondarySurface)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmallInlineInput(findQuery, { findQuery = it }, "Find...", Modifier.weight(1f), denColors)
                        SmallInlineInput(replaceQuery, { replaceQuery = it }, "Replace...", Modifier.weight(1f), denColors)
                        val matchCount = remember(findQuery, activeTab.value.text) {
                            if (findQuery.isEmpty()) 0
                            else Regex(Regex.escape(findQuery), RegexOption.IGNORE_CASE).findAll(activeTab.value.text).count()
                        }
                        Text("$matchCount", style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary)
                        TextButton(
                            onClick = {
                                if (findQuery.isNotEmpty()) {
                                    val updated = activeTab.value.text.replace(findQuery, replaceQuery, ignoreCase = true)
                                    updateActiveTab { it.copy(value = TextFieldValue(updated), isDirty = true) }
                                }
                            },
                            modifier = Modifier.height(28.dp)
                        ) { Text("Replace All", fontSize = 11.sp) }
                    }
                }

                // Quick Coding Symbol Toolbar
                if (activeTab != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(editorBg)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("  " to "Tab", "{" to "{", "}" to "}", "(" to "(", ")" to ")", "[" to "[", "]" to "]", " => " to "=>", ";" to ";", "// " to "//", "\"\"" to "\"\"").forEach { (ins, label) ->
                            Text(
                                text = label,
                                style = TextStyle(fontFamily = MonoFontFamily, fontSize = 11.sp, color = denColors.textSecondary),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(denColors.secondarySurface.copy(alpha = 0.7f))
                                    .clickable { insertTextAtCursor(ins) }
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        if (activeTab.language == CodeLanguage.JSON) {
                            Text(
                                text = "Format JSON",
                                style = MaterialTheme.typography.labelSmall,
                                color = denColors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(denColors.accentContainer)
                                    .clickable { formatActiveJson() }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Code Editor Canvas (Line Numbers + Highlighted Code Field)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(editorBg)
                ) {
                    if (activeTab == null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Code, null, tint = denColors.accent.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("DenCode Studio", style = MaterialTheme.typography.headlineMedium, color = denColors.textPrimary)
                            Text("Select a file in the Explorer or create a new file to start coding.", style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
                        }
                    } else {
                        val vScroll = rememberScrollState()
                        val hScroll = rememberScrollState()
                        val lines = remember(activeTab.value.text) {
                            activeTab.value.text.split('\n').size.coerceAtLeast(1)
                        }
                        val visualTransform = remember(activeTab.language, denColors.isDark) {
                            VisualTransformation { text ->
                                TransformedText(
                                    CodeSyntaxEngine.highlight(text.text, activeTab.language, denColors.isDark),
                                    OffsetMapping.Identity
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(vScroll)
                                .padding(vertical = 8.dp)
                        ) {
                            // Line Numbers Gutter
                            Column(
                                modifier = Modifier
                                    .width(44.dp)
                                    .padding(end = 8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                for (lineNum in 1..lines) {
                                    Text(
                                        text = lineNum.toString(),
                                        style = TextStyle(
                                            fontFamily = MonoFontFamily,
                                            fontSize = 12.5.sp,
                                            lineHeight = 19.sp,
                                            color = denColors.textSecondary.copy(alpha = 0.55f),
                                            textAlign = TextAlign.End
                                        )
                                    )
                                }
                            }

                            // Highlighted Code Editor Field
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (!wordWrap) Modifier.horizontalScroll(hScroll) else Modifier)
                                    .padding(end = 12.dp)
                            ) {
                                BasicTextField(
                                    value = activeTab.value,
                                    onValueChange = { newVal ->
                                        updateActiveTab { it.copy(value = newVal, isDirty = true) }
                                    },
                                    textStyle = TextStyle(
                                        fontFamily = MonoFontFamily,
                                        fontSize = 12.5.sp,
                                        lineHeight = 19.sp,
                                        color = denColors.textPrimary
                                    ),
                                    visualTransformation = visualTransform,
                                    cursorBrush = SolidColor(denColors.accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dencode_editor_field")
                                )
                            }
                        }
                    }
                }

                // Bottom Panel (Live Web Preview / Terminal / Problems)
                if (showBottomPanel) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(155.dp)
                            .background(sidebarBg)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(denColors.border))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BottomTabLabel("LIVE PREVIEW", bottomTab == IdeBottomTab.PREVIEW) { bottomTab = IdeBottomTab.PREVIEW }
                            BottomTabLabel("TERMINAL", bottomTab == IdeBottomTab.TERMINAL) { bottomTab = IdeBottomTab.TERMINAL }
                            BottomTabLabel("PROBLEMS (${activeProblems.size})", bottomTab == IdeBottomTab.PROBLEMS) { bottomTab = IdeBottomTab.PROBLEMS }
                            Spacer(Modifier.weight(1f))
                            if (bottomTab == IdeBottomTab.PREVIEW) {
                                Icon(
                                    Icons.Filled.Visibility,
                                    contentDescription = "Refresh Preview",
                                    tint = denColors.accent,
                                    modifier = Modifier.size(15.dp).clickable {
                                        saveActiveTab()
                                        previewVersion++
                                    }
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (bottomTab) {
                                IdeBottomTab.PREVIEW -> {
                                    val htmlBundle = remember(activeTab?.file, activeTab?.value?.text, previewVersion, workspaceDir) {
                                        buildLiveHtmlBundle(activeTab, workspaceDir)
                                    }
                                    key(previewVersion, htmlBundle.hashCode()) {
                                        AndroidView(
                                            modifier = Modifier.fillMaxSize(),
                                            factory = { ctx ->
                                                WebView(ctx).apply {
                                                    layoutParams = ViewGroup.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT
                                                    )
                                                    configurePreviewWebSettings(settings)
                                                    webViewClient = WebViewClient()
                                                    loadDataWithBaseURL("https://local.dencode/", htmlBundle, "text/html", "UTF-8", null)
                                                }
                                            },
                                            update = { wv ->
                                                wv.loadDataWithBaseURL("https://local.dencode/", htmlBundle, "text/html", "UTF-8", null)
                                            }
                                        )
                                    }
                                }
                                IdeBottomTab.TERMINAL -> {
                                    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0C1017)).padding(6.dp)) {
                                        LazyColumn(modifier = Modifier.weight(1f)) {
                                            items(terminalLines) { line ->
                                                Text(
                                                    text = line,
                                                    style = TextStyle(fontFamily = MonoFontFamily, fontSize = 11.sp, color = Color(0xFFE3EAF4))
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${workspaceDir.name}$ ", style = TextStyle(fontFamily = MonoFontFamily, fontSize = 11.sp, color = denColors.accent))
                                            BasicTextField(
                                                value = terminalInput,
                                                onValueChange = { terminalInput = it },
                                                singleLine = true,
                                                textStyle = TextStyle(fontFamily = MonoFontFamily, fontSize = 11.sp, color = Color.White),
                                                cursorBrush = SolidColor(denColors.accent),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                keyboardActions = KeyboardActions(onSend = {
                                                    val cmd = terminalInput
                                                    terminalInput = ""
                                                    runIntegratedShellCommand(cmd)
                                                }),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                                IdeBottomTab.PROBLEMS -> {
                                    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                        if (activeProblems.isEmpty()) {
                                            item {
                                                Text("No problems detected in ${activeTab?.title ?: "workspace"}.", style = MaterialTheme.typography.bodySmall, color = denColors.success)
                                            }
                                        } else {
                                            items(activeProblems) { prob ->
                                                Text("Ln ${prob.line}: ${prob.message}", style = MaterialTheme.typography.bodySmall, color = denColors.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. VS Code-Style Status Bar
        val cursorLineCol = remember(activeTab?.value) {
            val tab = activeTab ?: return@remember 1 to 1
            val pos = tab.value.selection.start.coerceIn(0, tab.value.text.length)
            val before = tab.value.text.substring(0, pos)
            val line = before.count { it == '\n' } + 1
            val col = pos - (before.lastIndexOf('\n') + 1) + 1
            line to col
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(denColors.accent)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("DenCode", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text("Workspace: ${workspaceDir.name}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                Text("Problems: ${activeProblems.size}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (activeTab != null) {
                    Text("Ln ${cursorLineCol.first}, Col ${cursorLineCol.second}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Text("UTF-8", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                    Text(activeTab.language.label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                }
            }
        }
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New Code File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File name (e.g. app.js, page.html, main.kt)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    showNewFileDialog = false
                    val safe = newFileName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "untitled.txt" }
                    val file = File(workspaceDir, safe)
                    if (!file.exists()) {
                        file.writeText(defaultTemplateForFile(safe))
                    }
                    refreshTick++
                    openFileInTab(file)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Workspace Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    showNewFolderDialog = false
                    val safe = newFolderName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "src" }
                    File(workspaceDir, safe).mkdirs()
                    refreshTick++
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCommandPalette) {
        val commands = listOf(
            "File: New File" to { showNewFileDialog = true },
            "File: Save Active File" to { saveActiveTab() },
            "Run: Live Preview / Execute" to { runActiveFile() },
            "Format: Format JSON Document" to { formatActiveJson() },
            "Edit: Find & Replace" to { showFindBar = !showFindBar },
            "View: Toggle Explorer Sidebar" to { showSidebar = !showSidebar },
            "View: Toggle Bottom Panel" to { showBottomPanel = !showBottomPanel },
            "View: Toggle Word Wrap" to { wordWrap = !wordWrap }
        ).filter {
            commandPaletteQuery.isBlank() || it.first.contains(commandPaletteQuery, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { showCommandPalette = false },
            title = { Text("Command Palette") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = commandPaletteQuery,
                        onValueChange = { commandPaletteQuery = it },
                        placeholder = { Text("Type a command...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        commands.forEach { (title, action) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    showCommandPalette = false
                                    commandPaletteQuery = ""
                                    action()
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = denColors.secondarySurface
                            ) {
                                Text(title, style = MaterialTheme.typography.bodySmall, color = denColors.textPrimary, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCommandPalette = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ActivityIconButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val denColors = LocalDenColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) denColors.accentContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) denColors.accent else denColors.textSecondary,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun BottomTabLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val denColors = LocalDenColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        ),
        color = if (selected) denColors.accent else denColors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun SmallInlineInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
    denColors: com.denwindows.v1.theme.DenDesktopColors
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(denColors.windowSurface)
            .border(1.dp, denColors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = denColors.textPrimary),
            cursorBrush = SolidColor(denColors.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun languageBadgeColor(language: CodeLanguage): Color = when (language) {
    CodeLanguage.KOTLIN -> Color(0xFF9C88FF)
    CodeLanguage.JAVASCRIPT -> Color(0xFFF0DB4F)
    CodeLanguage.PYTHON -> Color(0xFF3776AB)
    CodeLanguage.HTML -> Color(0xFFE34C26)
    CodeLanguage.CSS -> Color(0xFF2965F1)
    CodeLanguage.JSON -> Color(0xFF38C976)
    CodeLanguage.MARKDOWN -> Color(0xFF7BB0FF)
    CodeLanguage.SHELL -> Color(0xFF38C976)
    CodeLanguage.PLAIN -> Color(0xFFA7B0BE)
}

private fun collectWorkspaceFiles(dir: File): List<File> {
    val result = mutableListOf<File>()
    fun walk(current: File, depth: Int) {
        if (depth > 3) return
        val children = current.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }) ?: return
        for (child in children) {
            result.add(child)
            if (child.isDirectory) walk(child, depth + 1)
        }
    }
    walk(dir, 0)
    return result.take(150)
}

private fun buildLiveHtmlBundle(activeTab: CodeEditorTab?, workspaceDir: File): String {
    val htmlFile = if (activeTab?.language == CodeLanguage.HTML) {
        activeTab.value.text
    } else {
        File(workspaceDir, "index.html").takeIf { it.exists() }?.readText()
            ?: "<html><body style='background:#101318;color:#f5f7fa;font-family:sans-serif;padding:16px;'><h3>DenCode Live Preview</h3><p>Open or create an <code>index.html</code> file to preview HTML/CSS/JS live.</p></body></html>"
    }

    val cssInline = if (activeTab?.language == CodeLanguage.CSS) {
        activeTab.value.text
    } else {
        File(workspaceDir, "styles.css").takeIf { it.exists() }?.readText().orEmpty()
    }

    val jsInline = if (activeTab?.language == CodeLanguage.JAVASCRIPT) {
        activeTab.value.text
    } else {
        File(workspaceDir, "app.js").takeIf { it.exists() }?.readText().orEmpty()
    }

    var bundled = htmlFile
    if (cssInline.isNotBlank()) {
        bundled = bundled.replace(
            Regex("<link[^>]*href=[\"']\\.?/?styles\\.css[\"'][^>]*>", RegexOption.IGNORE_CASE),
            "<style>\n$cssInline\n</style>"
        )
    }
    if (jsInline.isNotBlank()) {
        bundled = bundled.replace(
            Regex("<script[^>]*src=[\"']\\.?/?app\\.js[\"'][^>]*>\\s*</script>", RegexOption.IGNORE_CASE),
            "<script>\n$jsInline\n</script>"
        )
    }
    return bundled
}

@SuppressLint("SetJavaScriptEnabled")
private fun configurePreviewWebSettings(settings: WebSettings) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
}

private fun defaultTemplateForFile(name: String): String = when (CodeLanguage.fromFileName(name)) {
    CodeLanguage.HTML -> "<!DOCTYPE html>\n<html>\n<head>\n  <meta charset=\"UTF-8\">\n  <title>Page</title>\n</head>\n<body>\n  <h1>Hello from DenCode</h1>\n</body>\n</html>\n"
    CodeLanguage.JAVASCRIPT -> "// JavaScript file\nconsole.log('Hello DenCode');\n"
    CodeLanguage.KOTLIN -> "package app\n\nfun main() {\n    println(\"Hello Kotlin\")\n}\n"
    CodeLanguage.PYTHON -> "def main():\n    print(\"Hello Python\")\n\nif __name__ == \"__main__\":\n    main()\n"
    CodeLanguage.JSON -> "{\n  \"name\": \"project\",\n  \"version\": \"1.0.0\"\n}\n"
    CodeLanguage.SHELL -> "#!/system/bin/sh\necho \"Hello from Android Shell\"\nuname -a\n"
    else -> ""
}

private fun seedStarterProjectIfEmpty(starterDir: File) {
    val indexHtml = File(starterDir, "index.html")
    if (indexHtml.exists()) return

    indexHtml.writeText(
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <title>StarterWeb — DenCode</title>
          <link rel="stylesheet" href="styles.css" />
        </head>
        <body>
          <main class="card">
            <h1>DenCode Live Workspace</h1>
            <p>Edit <code>index.html</code>, <code>styles.css</code>, or <code>app.js</code> and click Run.</p>
            <button id="counterBtn">Clicked 0 times</button>
          </main>
          <script src="app.js"></script>
        </body>
        </html>
        """.trimIndent()
    )

    File(starterDir, "styles.css").writeText(
        """
        body {
          margin: 0;
          padding: 20px;
          background: #101318;
          color: #f5f7fa;
          font-family: system-ui, sans-serif;
        }
        .card {
          background: #1a2029;
          border: 1px solid #354052;
          border-radius: 12px;
          padding: 18px;
        }
        button {
          background: #4c8dff;
          color: #ffffff;
          border: none;
          border-radius: 8px;
          padding: 8px 14px;
          font-weight: 600;
          cursor: pointer;
        }
        """.trimIndent()
    )

    File(starterDir, "app.js").writeText(
        """
        let count = 0;
        const btn = document.getElementById("counterBtn");
        if (btn) {
          btn.addEventListener("click", () => {
            count += 1;
            btn.textContent = "Clicked " + count + " times";
          });
        }
        """.trimIndent()
    )

    File(starterDir, "hello.sh").writeText(
        """
        #!/system/bin/sh
        echo "Workspace files:"
        ls -la
        """.trimIndent()
    )

    File(starterDir, "config.json").writeText(
        """
        {
          "project": "StarterWeb",
          "ide": "DenCode Studio",
          "livePreview": true
        }
        """.trimIndent()
    )
}
