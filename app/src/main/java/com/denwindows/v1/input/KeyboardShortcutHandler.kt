package com.denwindows.v1.input

import android.view.KeyEvent

data class ShortcutCallbacks(
    val onToggleStartMenu: () -> Unit,
    val onOpenFileExplorer: () -> Unit,
    val onOpenRunDialog: () -> Unit,
    val onToggleShowDesktop: () -> Unit,
    val onCycleWindows: () -> Unit,
    val onCloseFocusedWindow: () -> Unit,
    val onEscapePressed: () -> Unit
)

object KeyboardShortcutHandler {
    fun handleKeyDown(keyCode: Int, event: KeyEvent, callbacks: ShortcutCallbacks): Boolean {
        val isMeta = event.isMetaPressed
        val isAlt = event.isAltPressed

        return when {
            keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT -> {
                callbacks.onToggleStartMenu()
                true
            }
            isMeta && keyCode == KeyEvent.KEYCODE_E -> {
                callbacks.onOpenFileExplorer()
                true
            }
            isMeta && keyCode == KeyEvent.KEYCODE_R -> {
                callbacks.onOpenRunDialog()
                true
            }
            isMeta && keyCode == KeyEvent.KEYCODE_D -> {
                callbacks.onToggleShowDesktop()
                true
            }
            isAlt && keyCode == KeyEvent.KEYCODE_TAB -> {
                callbacks.onCycleWindows()
                true
            }
            isAlt && keyCode == KeyEvent.KEYCODE_F4 -> {
                callbacks.onCloseFocusedWindow()
                true
            }
            keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                callbacks.onEscapePressed()
                true
            }
            else -> false
        }
    }
}
