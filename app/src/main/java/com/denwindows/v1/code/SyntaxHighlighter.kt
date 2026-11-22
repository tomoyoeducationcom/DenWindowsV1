package com.denwindows.v1.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

enum class CodeLanguage(val label: String, val extensions: Set<String>) {
    KOTLIN("Kotlin", setOf("kt", "kts", "java")),
    JAVASCRIPT("JavaScript", setOf("js", "jsx", "ts", "tsx", "mjs")),
    PYTHON("Python", setOf("py")),
    HTML("HTML", setOf("html", "htm", "xml", "svg")),
    CSS("CSS", setOf("css", "scss")),
    JSON("JSON", setOf("json")),
    MARKDOWN("Markdown", setOf("md", "markdown")),
    SHELL("Shell", setOf("sh", "bash", "zsh")),
    PLAIN("Plain Text", setOf("txt", "log", "ini", "conf", "csv"));

    companion object {
        fun fromFileName(name: String): CodeLanguage {
            val ext = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions } ?: PLAIN
        }
    }
}

data class CodeProblem(
    val line: Int,
    val message: String,
    val isError: Boolean = true
)

data class CodeSymbol(
    val name: String,
    val kind: String,
    val line: Int
)

object CodeSyntaxEngine {

    private val kotlinKeywords = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var",
        "if", "else", "when", "for", "while", "do", "return", "break", "continue",
        "true", "false", "null", "private", "public", "internal", "protected",
        "override", "open", "abstract", "sealed", "data", "enum", "suspend", "companion",
        "try", "catch", "finally", "throw", "in", "is", "as", "this", "super"
    )

    private val jsKeywords = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while",
        "switch", "case", "break", "continue", "class", "extends", "import", "export",
        "from", "default", "async", "await", "try", "catch", "throw", "new", "this",
        "true", "false", "null", "undefined", "typeof", "of", "in"
    )

    private val pythonKeywords = setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "break",
        "continue", "import", "from", "as", "try", "except", "finally", "raise",
        "with", "pass", "yield", "lambda", "True", "False", "None", "and", "or", "not", "in", "is", "async", "await"
    )

    private val shellKeywords = setOf(
        "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while",
        "case", "esac", "function", "return", "export", "local", "echo", "cd", "pwd", "exit"
    )

    fun highlight(code: String, language: CodeLanguage, isDark: Boolean): AnnotatedString {
        if (code.isEmpty() || language == CodeLanguage.PLAIN) return AnnotatedString(code)
        // Guard extremely large files (>80k chars) for zero UI lag
        if (code.length > 80_000) return AnnotatedString(code)

        val keywordColor = if (isDark) Color(0xFFC586C0) else Color(0xFF8B2FC9)
        val typeColor = if (isDark) Color(0xFF4EC9B0) else Color(0xFF007A68)
        val stringColor = if (isDark) Color(0xFFCE9178) else Color(0xFFA31515)
        val numberColor = if (isDark) Color(0xFFB5CEA8) else Color(0xFF098658)
        val commentColor = if (isDark) Color(0xFF6A9955) else Color(0xFF008000)
        val tagColor = if (isDark) Color(0xFF569CD6) else Color(0xFF0451A5)
        val attrColor = if (isDark) Color(0xFF9CDCFE) else Color(0xFF001080)

        return buildAnnotatedString {
            append(code)

            when (language) {
                CodeLanguage.HTML -> {
                    Regex("<(/?[A-Za-z0-9:_-]+)").findAll(code).forEach {
                        addStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1)
                    }
                    Regex("\\b([A-Za-z0-9_-]+)(?=\\s*=)").findAll(code).forEach {
                        addStyle(SpanStyle(color = attrColor), it.range.first, it.range.last + 1)
                    }
                    highlightQuotedStrings(code, stringColor)
                    Regex("<!--[\\s\\S]*?-->").findAll(code).forEach {
                        addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), it.range.first, it.range.last + 1)
                    }
                }
                CodeLanguage.CSS -> {
                    Regex("([.#]?[A-Za-z0-9_-]+)\\s*(?=\\{)").findAll(code).forEach {
                        addStyle(SpanStyle(color = typeColor), it.range.first, it.range.last + 1)
                    }
                    Regex("([a-z-]+)\\s*(?=:)").findAll(code).forEach {
                        addStyle(SpanStyle(color = attrColor), it.range.first, it.range.last + 1)
                    }
                    highlightNumbers(code, numberColor)
                    highlightQuotedStrings(code, stringColor)
                    Regex("/\\*[\\s\\S]*?\\*/").findAll(code).forEach {
                        addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), it.range.first, it.range.last + 1)
                    }
                }
                CodeLanguage.JSON -> {
                    highlightNumbers(code, numberColor)
                    Regex("\\b(true|false|null)\\b").findAll(code).forEach {
                        addStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Medium), it.range.first, it.range.last + 1)
                    }
                    Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(code).forEach { m ->
                        val after = code.drop(m.range.last + 1).trimStart()
                        val isKey = after.startsWith(":")
                        addStyle(SpanStyle(color = if (isKey) attrColor else stringColor), m.range.first, m.range.last + 1)
                    }
                }
                CodeLanguage.MARKDOWN -> {
                    Regex("(?m)^#{1,6}\\s+.*$").findAll(code).forEach {
                        addStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
                    }
                    Regex("`[^`]+`").findAll(code).forEach {
                        addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
                    }
                    Regex("\\*\\*[^*]+\\*\\*").findAll(code).forEach {
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
                    }
                }
                else -> {
                    val keywords = when (language) {
                        CodeLanguage.KOTLIN -> kotlinKeywords
                        CodeLanguage.JAVASCRIPT -> jsKeywords
                        CodeLanguage.PYTHON -> pythonKeywords
                        CodeLanguage.SHELL -> shellKeywords
                        else -> emptySet()
                    }
                    highlightNumbers(code, numberColor)
                    Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b").findAll(code).forEach { m ->
                        val word = m.value
                        when {
                            word in keywords -> addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
                            word.first().isUpperCase() -> addStyle(SpanStyle(color = typeColor), m.range.first, m.range.last + 1)
                        }
                    }
                    if (language == CodeLanguage.KOTLIN) {
                        Regex("@[A-Za-z0-9_]+").findAll(code).forEach {
                            addStyle(SpanStyle(color = tagColor), it.range.first, it.range.last + 1)
                        }
                    }
                    highlightQuotedStrings(code, stringColor)
                    if (language == CodeLanguage.PYTHON || language == CodeLanguage.SHELL) {
                        Regex("(?m)#.*$").findAll(code).forEach {
                            addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), it.range.first, it.range.last + 1)
                        }
                    } else {
                        Regex("(?m)//.*$").findAll(code).forEach {
                            addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), it.range.first, it.range.last + 1)
                        }
                        Regex("/\\*[\\s\\S]*?\\*/").findAll(code).forEach {
                            addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), it.range.first, it.range.last + 1)
                        }
                    }
                }
            }
        }
    }

    private fun AnnotatedString.Builder.highlightQuotedStrings(code: String, color: Color) {
        Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'").findAll(code).forEach {
            addStyle(SpanStyle(color = color), it.range.first, it.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightNumbers(code: String, color: Color) {
        Regex("\\b\\d+(\\.\\d+)?\\b").findAll(code).forEach {
            addStyle(SpanStyle(color = color), it.range.first, it.range.last + 1)
        }
    }

    fun validateDiagnostics(code: String, language: CodeLanguage): List<CodeProblem> {
        if (language != CodeLanguage.JSON || code.isBlank()) return emptyList()
        return validateJsonSyntax(code)
    }

    private fun validateJsonSyntax(input: String): List<CodeProblem> {
        var idx = 0
        var line = 1
        val n = input.length

        fun skipWs() {
            while (idx < n && input[idx].isWhitespace()) {
                if (input[idx] == '\n') line++
                idx++
            }
        }

        fun fail(msg: String): List<CodeProblem> = listOf(CodeProblem(line = line, message = msg))

        fun parseString(): Boolean {
            if (idx >= n || input[idx] != '"') return false
            idx++
            while (idx < n) {
                val c = input[idx]
                when {
                    c == '"' -> { idx++; return true }
                    c == '\\' -> {
                        idx++
                        if (idx >= n) return false
                        idx++
                    }
                    c == '\n' || c == '\r' -> return false
                    else -> idx++
                }
            }
            return false
        }

        lateinit var parseValue: () -> String?

        fun parseObject(): String? {
            idx++
            skipWs()
            if (idx < n && input[idx] == '}') { idx++; return null }
            while (true) {
                skipWs()
                if (!parseString()) return "Expected string property key"
                skipWs()
                if (idx >= n || input[idx] != ':') return "Expected ':' after property key"
                idx++
                parseValue()?.let { return it }
                skipWs()
                if (idx >= n) return "Unterminated object"
                when (input[idx]) {
                    ',' -> idx++
                    '}' -> { idx++; return null }
                    else -> return "Expected ',' or '}' in object"
                }
            }
        }

        fun parseArray(): String? {
            idx++
            skipWs()
            if (idx < n && input[idx] == ']') { idx++; return null }
            while (true) {
                parseValue()?.let { return it }
                skipWs()
                if (idx >= n) return "Unterminated array"
                when (input[idx]) {
                    ',' -> idx++
                    ']' -> { idx++; return null }
                    else -> return "Expected ',' or ']' in array"
                }
            }
        }

        parseValue = {
            skipWs()
            if (idx >= n) "Unexpected end of JSON"
            else when (input[idx]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> if (parseString()) null else "Unterminated string literal"
                't' -> if (input.startsWith("true", idx)) { idx += 4; null } else "Invalid literal"
                'f' -> if (input.startsWith("false", idx)) { idx += 5; null } else "Invalid literal"
                'n' -> if (input.startsWith("null", idx)) { idx += 4; null } else "Invalid literal"
                '-', in '0'..'9' -> {
                    val m = Regex("^-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?").find(input.substring(idx))
                    if (m != null) { idx += m.value.length; null } else "Invalid number"
                }
                else -> "Unexpected token '${input[idx]}'"
            }
        }

        val err = parseValue()
        if (err != null) return fail(err)
        skipWs()
        if (idx < n) return fail("Unexpected trailing characters")
        return emptyList()
    }

    fun extractSymbols(code: String, language: CodeLanguage): List<CodeSymbol> {
        if (code.isBlank()) return emptyList()
        val symbols = mutableListOf<CodeSymbol>()
        code.lines().forEachIndexed { idx, line ->
            val lineNum = idx + 1
            val trimmed = line.trim()
            when (language) {
                CodeLanguage.KOTLIN -> {
                    Regex("(?:fun|class|interface|object|enum class)\\s+([A-Za-z0-9_]+)").find(trimmed)?.let { m ->
                        val kind = trimmed.substringBefore(" ")
                        symbols.add(CodeSymbol(m.groupValues[1], kind, lineNum))
                    }
                }
                CodeLanguage.JAVASCRIPT -> {
                    Regex("(?:function|class)\\s+([A-Za-z0-9_]+)").find(trimmed)?.let { m ->
                        symbols.add(CodeSymbol(m.groupValues[1], "fn/class", lineNum))
                    }
                    Regex("(?:const|let)\\s+([A-Za-z0-9_]+)\\s*=\\s*(?:async\\s*)?\\(").find(trimmed)?.let { m ->
                        symbols.add(CodeSymbol(m.groupValues[1], "fn", lineNum))
                    }
                }
                CodeLanguage.PYTHON -> {
                    Regex("(?:def|class)\\s+([A-Za-z0-9_]+)").find(trimmed)?.let { m ->
                        symbols.add(CodeSymbol(m.groupValues[1], if (trimmed.startsWith("class")) "class" else "def", lineNum))
                    }
                }
                CodeLanguage.MARKDOWN -> {
                    if (trimmed.startsWith("#")) {
                        symbols.add(CodeSymbol(trimmed.trimStart('#', ' '), "heading", lineNum))
                    }
                }
                CodeLanguage.HTML -> {
                    Regex("id=[\"']([^\"']+)[\"']").find(trimmed)?.let { m ->
                        symbols.add(CodeSymbol("#${m.groupValues[1]}", "id", lineNum))
                    }
                }
                CodeLanguage.CSS -> {
                    if (trimmed.endsWith("{") && !trimmed.startsWith("@")) {
                        symbols.add(CodeSymbol(trimmed.removeSuffix("{").trim(), "rule", lineNum))
                    }
                }
                else -> Unit
            }
        }
        return symbols
    }
}
