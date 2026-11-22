package com.example

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.denwindows.v1.desktop.DenDesktopRoot
import com.denwindows.v1.desktop.DesktopViewModel
import com.denwindows.v1.input.KeyboardShortcutHandler
import com.denwindows.v1.input.ShortcutCallbacks
import com.denwindows.v1.windows.DesktopAppType

class MainActivity : ComponentActivity() {

    private val desktopViewModel: DesktopViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            DenDesktopRoot(
                viewModel = desktopViewModel,
                onExitApp = { finish() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        desktopViewModel.refreshInstalledAppsSilently()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            val handled = KeyboardShortcutHandler.handleKeyDown(
                keyCode = keyCode,
                event = event,
                callbacks = ShortcutCallbacks(
                    onToggleStartMenu = { desktopViewModel.toggleStartMenu() },
                    onOpenFileExplorer = { desktopViewModel.openInternalApp(DesktopAppType.FILE_EXPLORER) },
                    onOpenRunDialog = { desktopViewModel.setRunDialogOpen(true) },
                    onToggleShowDesktop = { desktopViewModel.windowManager.toggleShowDesktop() },
                    onCycleWindows = { desktopViewModel.windowManager.cycleNextWindow() },
                    onCloseFocusedWindow = { desktopViewModel.windowManager.closeFocusedWindow() },
                    onEscapePressed = {
                        desktopViewModel.dismissOverlays()
                        desktopViewModel.setRunDialogOpen(false)
                    }
                )
            )
            if (handled) return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
