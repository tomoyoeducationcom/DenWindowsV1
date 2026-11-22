package com.denwindows.v1.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.theme.MonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

data class TerminalLine(
    val text: String,
    val isError: Boolean = false,
    val isPrompt: Boolean = false
)

data class TerminalTabSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Android Shell",
    val workingDir: File,
    val lines: List<TerminalLine>,
    val history: List<String> = emptyList(),
    val isRunning: Boolean = false
)

object TerminalCommandParser {
    private val permittedCommands = setOf(
        "ls", "cat", "mkdir", "rmdir", "touch", "echo", "whoami",
        "date", "df", "du", "id", "uname", "getprop", "ps", "env",
        "printenv", "head", "tail", "wc", "grep", "find", "stat",
        "cp", "mv", "rm", "which", "basename", "dirname", "realpath"
    )

    fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escaping = false

        for (ch in input.trim()) {
            when {
                escaping -> {
                    current.append(ch)
                    escaping = false
                }
                ch == '\\' && !inSingleQuote -> escaping = true
                ch == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
                ch == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                ch.isWhitespace() && !inSingleQuote && !inDoubleQuote -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }

    fun isPermittedExternalCommand(command: String): Boolean = permittedCommands.contains(command)
}

@Composable
fun TerminalWindow(
    initialWorkingDir: File
) {
    val denColors = LocalDenColors.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun initialBanner(dir: File) = listOf(
        TerminalLine("DenWindowsV1 Android Shell [User-Space Toybox/Toolbox]"),
        TerminalLine("Note: Executes permitted Android shell binaries (not root / not full GNU Linux PRoot)."),
        TerminalLine("Working directory: ${dir.absolutePath}"),
        TerminalLine("Type 'help' for built-ins and supported commands.")
    )

    val tabs = remember {
        mutableStateListOf(
            TerminalTabSession(
                title = "Shell 1",
                workingDir = initialWorkingDir,
                lines = initialBanner(initialWorkingDir)
            )
        )
    }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    var commandInput by remember { mutableStateOf("") }
    var historyCursor by remember { mutableIntStateOf(-1) }
    var runningProcess by remember { mutableStateOf<Process?>(null) }
    var runningJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()

    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    fun updateTab(id: String, transform: (TerminalTabSession) -> TerminalTabSession) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx >= 0) tabs[idx] = transform(tabs[idx])
    }

    LaunchedEffect(activeTab.lines.size) {
        if (activeTab.lines.isNotEmpty()) {
            listState.animateScrollToItem(activeTab.lines.lastIndex)
        }
    }

    fun appendLines(tabId: String, newLines: List<TerminalLine>) {
        updateTab(tabId) { tab ->
            tab.copy(lines = (tab.lines + newLines).takeLast(500))
        }
    }

    fun runCommand(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val tabId = activeTab.id
        val cwd = activeTab.workingDir

        commandInput = ""
        historyCursor = -1
        updateTab(tabId) { tab ->
            tab.copy(
                lines = (tab.lines + TerminalLine("${cwd.name}$ $trimmed", isPrompt = true)).takeLast(500),
                history = (listOf(trimmed) + tab.history.filterNot { it == trimmed }).take(40)
            )
        }

        val tokens = TerminalCommandParser.tokenize(trimmed)
        if (tokens.isEmpty()) return
        val cmd = tokens.first()
        val args = tokens.drop(1)

        when (cmd) {
            "clear" -> updateTab(tabId) { it.copy(lines = emptyList()) }
            "pwd" -> appendLines(tabId, listOf(TerminalLine(cwd.absolutePath)))
            "help" -> appendLines(
                tabId,
                listOf(
                    TerminalLine("Built-ins: pwd, cd <dir>, clear, history, help"),
                    TerminalLine("Android shell commands: ls, cat, mkdir, rmdir, touch, echo, whoami, date, df, du, id, uname, getprop, ps, env, head, tail, wc, grep, find, stat, cp, mv, rm"),
                    TerminalLine("PRoot / full Linux userspace: optional external integration (not bundled in V1).")
                )
            )
            "history" -> {
                val lines = activeTab.history.reversed().mapIndexed { i, h -> TerminalLine(" ${i + 1}  $h") }
                appendLines(tabId, lines)
            }
            "cd" -> {
                val targetArg = args.firstOrNull() ?: initialWorkingDir.absolutePath
                val resolved = when {
                    targetArg == "~" -> initialWorkingDir
                    targetArg.startsWith("/") -> File(targetArg)
                    else -> File(cwd, targetArg)
                }.canonicalFile
                if (resolved.exists() && resolved.isDirectory && resolved.canRead()) {
                    updateTab(tabId) { it.copy(workingDir = resolved) }
                } else {
                    appendLines(tabId, listOf(TerminalLine("cd: $targetArg: No such readable directory", isError = true)))
                }
            }
            else -> {
                if (!TerminalCommandParser.isPermittedExternalCommand(cmd)) {
                    appendLines(
                        tabId,
                        listOf(
                            TerminalLine(
                                "Command '$cmd' is not in the permitted user-space command list. Type 'help' to view supported commands.",
                                isError = true
                            )
                        )
                    )
                    return
                }
                updateTab(tabId) { it.copy(isRunning = true) }
                runningJob = scope.launch {
                    val outputLines = withContext(Dispatchers.IO) {
                        runCatching {
                            val pb = ProcessBuilder(tokens).apply {
                                directory(cwd)
                            }
                            val process = pb.start()
                            runningProcess = process

                            val result = withTimeoutOrNull(10_000L) {
                                val out = process.inputStream.bufferedReader().readLines().map { TerminalLine(it) }
                                val err = process.errorStream.bufferedReader().readLines().map { TerminalLine(it, isError = true) }
                                process.waitFor()
                                out + err
                            }
                            if (result == null) {
                                process.destroyForcibly()
                                listOf(TerminalLine("Process terminated after 10s timeout.", isError = true))
                            } else {
                                result
                            }
                        }.getOrElse { ex ->
                            listOf(TerminalLine("Execution error: ${ex.localizedMessage ?: "Unknown"}", isError = true))
                        }
                    }
                    runningProcess = null
                    updateTab(tabId) { it.copy(isRunning = false) }
                    if (outputLines.isNotEmpty()) appendLines(tabId, outputLines)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C1017))
    ) {
        // Tab Strip & Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(Color(0xFF151B26))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { tab ->
                    val selected = tab.id == activeTab.id
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF0C1017) else Color.Transparent)
                            .clickable { activeTabId = tab.id }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tab.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) Color(0xFFF5F7FA) else Color(0xFF8C97A8)
                        )
                        if (tabs.size > 1) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close tab",
                                tint = Color(0xFF8C97A8),
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable {
                                        val idx = tabs.indexOfFirst { it.id == tab.id }
                                        tabs.removeAll { it.id == tab.id }
                                        if (activeTabId == tab.id && tabs.isNotEmpty()) {
                                            activeTabId = tabs[(idx - 1).coerceAtLeast(0)].id
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    val num = tabs.size + 1
                    val newTab = TerminalTabSession(
                        title = "Shell $num",
                        workingDir = initialWorkingDir,
                        lines = initialBanner(initialWorkingDir)
                    )
                    tabs.add(newTab)
                    activeTabId = newTab.id
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New Terminal Tab", tint = Color(0xFFF5F7FA), modifier = Modifier.size(16.dp))
            }

            IconButton(
                onClick = {
                    val allText = activeTab.lines.joinToString("\n") { it.text }
                    clipboardManager.setText(AnnotatedString(allText))
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Output", tint = Color(0xFFA7B0BE), modifier = Modifier.size(15.dp))
            }

            if (activeTab.isRunning) {
                IconButton(
                    onClick = {
                        runningProcess?.destroyForcibly()
                        runningJob?.cancel()
                        updateTab(activeTab.id) { it.copy(isRunning = false) }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Terminate Process", tint = denColors.error, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Scrollable Output Area
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(activeTab.lines) { line ->
                    Text(
                        text = line.text,
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = when {
                                line.isError -> Color(0xFFFF7A85)
                                line.isPrompt -> Color(0xFF7BB0FF)
                                else -> Color(0xFFE3EAF4)
                            }
                        )
                    )
                }
            }
        }

        // Quick Command Chips + Prompt Input Bar
        if (activeTab.history.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeTab.history.take(6).forEach { hist ->
                    Text(
                        text = hist,
                        style = TextStyle(fontFamily = MonoFontFamily, fontSize = 11.sp, color = Color(0xFFA7B0BE)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF1A2230))
                            .clickable { commandInput = hist }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF131923))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "den:${activeTab.workingDir.name}$ ",
                style = TextStyle(fontFamily = MonoFontFamily, fontSize = 12.sp, color = Color(0xFF4C8DFF))
            )
            BasicTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = MonoFontFamily, fontSize = 12.5.sp, color = Color(0xFFF5F7FA)),
                cursorBrush = SolidColor(Color(0xFF4C8DFF)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { runCommand(commandInput) }),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.Enter, Key.NumPadEnter -> {
                                    runCommand(commandInput)
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (activeTab.history.isNotEmpty()) {
                                        val next = (historyCursor + 1).coerceAtMost(activeTab.history.lastIndex)
                                        historyCursor = next
                                        commandInput = activeTab.history[next]
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (historyCursor > 0) {
                                        historyCursor--
                                        commandInput = activeTab.history[historyCursor]
                                    } else {
                                        historyCursor = -1
                                        commandInput = ""
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .testTag("terminal_command_input")
            )
        }
    }
}
