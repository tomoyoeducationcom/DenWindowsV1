package com.denwindows.v1

import com.denwindows.v1.code.CodeLanguage
import com.denwindows.v1.code.CodeSyntaxEngine
import com.denwindows.v1.terminal.TerminalCommandParser
import com.denwindows.v1.utilities.CalculatorEngine
import com.denwindows.v1.windows.DesktopAppType
import com.denwindows.v1.windows.DesktopWindowManager
import com.denwindows.v1.windows.WindowStateMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DenWindowsCoreTest {

    @Test
    fun `window manager opens focuses snaps minimizes and closes windows`() {
        val wm = DesktopWindowManager()
        wm.updateWorkspaceBounds(1000f, 520f)

        val calcId = wm.openApp(DesktopAppType.CALCULATOR)
        val termId = wm.openApp(DesktopAppType.TERMINAL)

        assertEquals(2, wm.state.value.windows.size)
        assertEquals(termId, wm.state.value.focusedWindowId)

        wm.focusWindow(calcId)
        assertEquals(calcId, wm.state.value.focusedWindowId)

        wm.snapLeft(calcId)
        assertEquals(WindowStateMode.SNAPPED_LEFT, wm.state.value.windows.first { it.id == calcId }.stateMode)

        wm.minimizeWindow(calcId)
        assertEquals(WindowStateMode.MINIMIZED, wm.state.value.windows.first { it.id == calcId }.stateMode)
        assertEquals(termId, wm.state.value.focusedWindowId)

        wm.toggleShowDesktop()
        assertTrue(wm.state.value.windows.all { it.stateMode == WindowStateMode.MINIMIZED })
        assertNull(wm.state.value.focusedWindowId)

        wm.toggleShowDesktop()
        assertNotNull(wm.state.value.focusedWindowId)

        wm.closeWindow(calcId)
        assertEquals(1, wm.state.value.windows.size)
    }

    @Test
    fun `calculator engine performs arithmetic and percent accurately`() {
        val calc = CalculatorEngine()
        calc.inputDigit("2")
        calc.inputDigit("5")
        calc.applyOperator("×")
        calc.inputDigit("4")
        calc.evaluateEquals()
        assertEquals("100", calc.display)
        assertEquals(1, calc.history.size)

        calc.applyOperator("÷")
        calc.inputDigit("0")
        calc.evaluateEquals()
        assertEquals("Error", calc.display)
    }

    @Test
    fun `terminal command parser tokenizes quotes safely and checks allowlist`() {
        val tokens = TerminalCommandParser.tokenize("echo \"hello world\" 'test arg'")
        assertEquals(listOf("echo", "hello world", "test arg"), tokens)
        assertTrue(TerminalCommandParser.isPermittedExternalCommand("ls"))
        assertTrue(TerminalCommandParser.isPermittedExternalCommand("getprop"))
        assertFalse(TerminalCommandParser.isPermittedExternalCommand("su"))
    }

    @Test
    fun `code syntax engine highlights extracts symbols and validates json`() {
        val kt = """
            class AppController {
                fun start() {
                    val name = "DenCode"
                }
            }
        """.trimIndent()
        val highlighted = CodeSyntaxEngine.highlight(kt, CodeLanguage.KOTLIN, isDark = true)
        assertTrue(highlighted.spanStyles.isNotEmpty())

        val symbols = CodeSyntaxEngine.extractSymbols(kt, CodeLanguage.KOTLIN)
        assertEquals(listOf("AppController", "start"), symbols.map { it.name })

        val validJson = CodeSyntaxEngine.validateDiagnostics("""{"ok": true}""", CodeLanguage.JSON)
        assertTrue(validJson.isEmpty())

        val badJson = CodeSyntaxEngine.validateDiagnostics("""{"ok": }""", CodeLanguage.JSON)
        assertEquals(1, badJson.size)
    }
}
