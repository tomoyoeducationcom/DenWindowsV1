package com.denwindows.v1.desktop

import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denwindows.v1.browser.DenBrowserWindow
import com.denwindows.v1.code.CodeEditorWindow
import com.denwindows.v1.data.SearchEngineOption
import com.denwindows.v1.explorer.FileExplorerWindow
import com.denwindows.v1.settings.SettingsWindow
import com.denwindows.v1.startmenu.StartMenu
import com.denwindows.v1.taskbar.QuickSettingsPanel
import com.denwindows.v1.taskbar.Taskbar
import com.denwindows.v1.terminal.TerminalWindow
import com.denwindows.v1.theme.AccentOption
import com.denwindows.v1.theme.DenWindowsTheme
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.utilities.AppCompatibilityApp
import com.denwindows.v1.utilities.CalculatorApp
import com.denwindows.v1.utilities.CalendarApp
import com.denwindows.v1.utilities.ClockApp
import com.denwindows.v1.utilities.NotepadApp
import com.denwindows.v1.utilities.RunDialog
import com.denwindows.v1.utilities.TaskManagerApp
import com.denwindows.v1.windows.DesktopAppType
import com.denwindows.v1.windows.DesktopWindowFrame
import com.denwindows.v1.windows.WindowStateMode
import kotlinx.coroutines.delay

@Composable
fun DenDesktopRoot(
    viewModel: DesktopViewModel,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val desktopItems by viewModel.desktopItems.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val installedBrowsers by viewModel.installedBrowsers.collectAsStateWithLifecycle()
    val recentApps by viewModel.recentApps.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()
    val windowState by viewModel.windowState.collectAsStateWithLifecycle()
    val clockDate by viewModel.clockDate.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val network by viewModel.network.collectAsStateWithLifecycle()
    val bluetoothEnabled by viewModel.bluetoothEnabled.collectAsStateWithLifecycle()
    val connectedInputs by viewModel.connectedInputs.collectAsStateWithLifecycle()
    val isStartMenuOpen by viewModel.isStartMenuOpen.collectAsStateWithLifecycle()
    val startMenuSearchFocus by viewModel.startMenuSearchFocus.collectAsStateWithLifecycle()
    val isQuickSettingsOpen by viewModel.isQuickSettingsOpen.collectAsStateWithLifecycle()
    val isRunDialogOpen by viewModel.isRunDialogOpen.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var taskbarRevealedWhenAutoHidden by remember { mutableStateOf(false) }

    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            delay(3500L)
            viewModel.clearBanner()
        }
    }

    val hasVisibleWindow = windowState.windows.any { it.stateMode != WindowStateMode.MINIMIZED }
    val showTaskbar = !prefs.taskbarAutoHide ||
        !hasVisibleWindow ||
        isStartMenuOpen ||
        isQuickSettingsOpen ||
        taskbarRevealedWhenAutoHidden

    // Back gesture / key closes overlays first, then minimizes focused window
    BackHandler(
        enabled = isStartMenuOpen || isQuickSettingsOpen || isRunDialogOpen || hasVisibleWindow
    ) {
        when {
            isRunDialogOpen -> viewModel.setRunDialogOpen(false)
            isStartMenuOpen || isQuickSettingsOpen -> viewModel.dismissOverlays()
            windowState.focusedWindowId != null -> viewModel.windowManager.minimizeWindow(windowState.focusedWindowId!!)
        }
    }

    DenWindowsTheme(
        isDarkTheme = prefs.isDarkTheme,
        accentOption = AccentOption.fromId(prefs.accentId)
    ) {
        val denColors = LocalDenColors.current

        Box(modifier = Modifier.fillMaxSize()) {
            // Full-Bleed Desktop Wallpaper
            WallpaperRenderer(
                wallpaperId = prefs.wallpaperId,
                customWallpaperUri = prefs.customWallpaperUri
            )

            // Main Column: Safe Workspace + Taskbar
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // Desktop Workspace & Floating Internal Windows Layer
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val wsWidthDp = maxWidth.value
                    val wsHeightDp = maxHeight.value

                    LaunchedEffect(wsWidthDp, wsHeightDp) {
                        viewModel.windowManager.updateWorkspaceBounds(wsWidthDp, wsHeightDp)
                    }

                    // Layer 1: Desktop Icons & Context Menu
                    DesktopWorkspace(
                        desktopItems = desktopItems,
                        iconSize = prefs.iconSize,
                        getAndroidAppIcon = { pkg -> viewModel.installedAppsRepository.getIconForPackage(pkg) },
                        onOpenItem = { viewModel.openDesktopItem(it) },
                        onMoveItem = { id, c, r -> viewModel.moveDesktopItem(id, c, r) },
                        onCreateFolder = { name, c, r -> viewModel.createDesktopFolder(name, c, r) },
                        onPinToTaskbar = { item, pinned -> viewModel.togglePinItemToTaskbar(item, pinned) },
                        onRemoveDesktopItem = { viewModel.removeDesktopItem(it) },
                        onOpenAppInfo = { pkg -> viewModel.installedAppsRepository.openAppInfo(pkg) },
                        onRefreshDesktop = { viewModel.refreshDesktopAndApps() },
                        onOpenSettingsSection = { sec -> viewModel.openInternalApp(DesktopAppType.SETTINGS, sec) },
                        onOpenTerminal = { viewModel.openInternalApp(DesktopAppType.TERMINAL) },
                        onEmptyAreaTap = {
                            viewModel.dismissOverlays()
                            taskbarRevealedWhenAutoHidden = false
                        }
                    )

                    // Layer 2: Internal Floating / Snapped / Maximized Windows
                    windowState.windows.forEach { win ->
                        val isFocused = win.id == windowState.focusedWindowId
                        DesktopWindowFrame(
                            window = win,
                            isFocused = isFocused,
                            workspaceWidthDp = wsWidthDp,
                            workspaceHeightDp = wsHeightDp,
                            animationsEnabled = prefs.windowAnimations,
                            onFocus = {
                                viewModel.dismissOverlays()
                                viewModel.windowManager.focusWindow(win.id)
                            },
                            onMinimize = { viewModel.windowManager.minimizeWindow(win.id) },
                            onToggleMaximize = { viewModel.windowManager.toggleMaximizeRestore(win.id) },
                            onSnapLeft = { viewModel.windowManager.snapLeft(win.id) },
                            onSnapRight = { viewModel.windowManager.snapRight(win.id) },
                            onClose = { viewModel.windowManager.closeWindow(win.id) },
                            onDrag = { dx, dy -> viewModel.windowManager.dragWindow(win.id, dx, dy) },
                            onResize = { dw, dh -> viewModel.windowManager.resizeWindow(win.id, dw, dh) }
                        ) {
                            when (win.appType) {
                                DesktopAppType.FILE_EXPLORER -> FileExplorerWindow(
                                    initialPath = win.payload,
                                    desktopRepository = viewModel.desktopRepository,
                                    storageInfo = viewModel.getStorageInfo(),
                                    safTreeUris = prefs.safTreeUris,
                                    onAddSafTreeUri = { viewModel.addSafTreeUri(it) },
                                    onOpenTextFileInNotepad = { file ->
                                        viewModel.openInternalApp(DesktopAppType.NOTEPAD, file.absolutePath)
                                    },
                                    onOpenCodeFile = { file ->
                                        viewModel.openInternalApp(DesktopAppType.CODE_EDITOR, file.absolutePath)
                                    },
                                    onStatusMessage = { viewModel.showBanner(it) }
                                )

                                DesktopAppType.CODE_EDITOR -> CodeEditorWindow(
                                    initialPath = win.payload,
                                    projectsRootDir = viewModel.desktopRepository.projectsDir,
                                    onTitleUpdate = { t -> viewModel.windowManager.updateWindowTitle(win.id, t) },
                                    onStatusMessage = { viewModel.showBanner(it) }
                                )

                                DesktopAppType.BROWSER -> DenBrowserWindow(
                                    initialUrl = win.payload,
                                    searchEngine = SearchEngineOption.fromId(prefs.searchEngineId),
                                    bookmarks = bookmarks,
                                    history = history,
                                    onAddBookmark = { t, u -> viewModel.addBookmark(t, u) },
                                    onRemoveBookmark = { u -> viewModel.removeBookmark(u) },
                                    onRecordHistory = { t, u -> viewModel.recordHistory(t, u) },
                                    onClearHistory = { viewModel.clearHistory() },
                                    onOpenInExternalBrowser = { u -> viewModel.openUrlExternally(u) },
                                    onEnqueueSystemDownload = { u, n -> viewModel.enqueueDownload(u, n) }
                                )

                                DesktopAppType.TERMINAL -> TerminalWindow(
                                    initialWorkingDir = viewModel.desktopRepository.desktopDir
                                )

                                DesktopAppType.NOTEPAD -> NotepadApp(
                                    initialFilePath = win.payload,
                                    documentsDir = viewModel.desktopRepository.documentsDir,
                                    onTitleUpdate = { t -> viewModel.windowManager.updateWindowTitle(win.id, t) },
                                    onStatusMessage = { viewModel.showBanner(it) }
                                )

                                DesktopAppType.CALCULATOR -> CalculatorApp()

                                DesktopAppType.CLOCK -> ClockApp()

                                DesktopAppType.CALENDAR -> CalendarApp(
                                    events = calendarEvents,
                                    onSaveEvent = { viewModel.saveCalendarEvent(it) },
                                    onDeleteEvent = { viewModel.deleteCalendarEvent(it) }
                                )

                                DesktopAppType.TASK_MANAGER -> TaskManagerApp(
                                    openWindows = windowState.windows,
                                    battery = battery,
                                    getMemorySnapshot = { viewModel.getMemorySnapshot() },
                                    getStorageInfo = { viewModel.getStorageInfo() },
                                    onFocusWindow = { viewModel.windowManager.focusWindow(it) },
                                    onCloseWindow = { viewModel.windowManager.closeWindow(it) }
                                )

                                DesktopAppType.SETTINGS -> SettingsWindow(
                                    initialSection = win.payload,
                                    preferences = prefs,
                                    installedBrowsers = installedBrowsers,
                                    inputInfo = connectedInputs,
                                    diagnostics = viewModel.getDiagnostics(activity),
                                    onSetDarkTheme = { viewModel.setDarkTheme(it) },
                                    onSetAccent = { viewModel.setAccent(it) },
                                    onSetBuiltInWallpaper = { viewModel.setBuiltInWallpaper(it) },
                                    onSetCustomWallpaperUri = { viewModel.setCustomWallpaperUri(it) },
                                    onSetIconSize = { viewModel.setIconSize(it) },
                                    onSetTaskbarAutoHide = { viewModel.setTaskbarAutoHide(it) },
                                    onSetWindowAnimations = { viewModel.setWindowAnimations(it) },
                                    onSetDefaultBrowser = { viewModel.setDefaultBrowser(it) },
                                    onSetSearchEngine = { viewModel.setSearchEngine(it) },
                                    onSetTryFreeform = { viewModel.setTryFreeform(it) },
                                    onSetMouseHover = { viewModel.setMouseHover(it) },
                                    onOpenHomeSettings = { viewModel.openHomeLauncherSettings() },
                                    onOpenDiagnosticsWindow = { viewModel.openInternalApp(DesktopAppType.APP_COMPATIBILITY) }
                                )

                                DesktopAppType.APP_COMPATIBILITY -> AppCompatibilityApp(
                                    diagnostics = viewModel.getDiagnostics(activity),
                                    installedAppsCount = installedApps.size,
                                    installedBrowsers = installedBrowsers,
                                    onOpenHomeSettings = { viewModel.openHomeLauncherSettings() },
                                    onOpenDeveloperSettings = { viewModel.openAndroidSettings(Settings.ACTION_SETTINGS) }
                                )
                            }
                        }
                    }

                    // Layer 3: Start Menu Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isStartMenuOpen,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            StartMenu(
                                installedApps = installedApps,
                                recentApps = recentApps,
                                searchableFiles = viewModel.getSearchableFiles(),
                                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                                initialSearchFocus = startMenuSearchFocus,
                                onDismiss = { viewModel.dismissOverlays() },
                                onLaunchInternalApp = { type, payload -> viewModel.openInternalApp(type, payload) },
                                onLaunchAndroidApp = { viewModel.launchAndroidApp(it) },
                                onPinAndroidAppToTaskbar = { viewModel.pinAndroidAppToTaskbar(it) },
                                onAddAndroidAppToDesktop = { viewModel.addAndroidAppToDesktop(it) },
                                onOpenFile = { viewModel.openFileFromSearch(it) },
                                onMinimizeAll = { viewModel.windowManager.toggleShowDesktop() },
                                onCloseAllWindows = { viewModel.windowManager.closeAllWindows() },
                                onOpenHomeChooser = { viewModel.openHomeLauncherSettings() },
                                onExitApp = onExitApp
                            )
                        }
                    }

                    // Layer 4: Quick Settings & System Tray Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 10.dp)
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isQuickSettingsOpen,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            QuickSettingsPanel(
                                isDarkTheme = prefs.isDarkTheme,
                                battery = battery,
                                network = network,
                                bluetoothEnabled = bluetoothEnabled,
                                inputInfo = connectedInputs,
                                onToggleTheme = { viewModel.setDarkTheme(!prefs.isDarkTheme) },
                                onOpenAndroidSettings = { action ->
                                    viewModel.dismissOverlays()
                                    viewModel.openAndroidSettings(action)
                                },
                                onOpenDesktopSettings = {
                                    viewModel.openInternalApp(DesktopAppType.SETTINGS, "desktop")
                                },
                                onOpenDiagnostics = {
                                    viewModel.openInternalApp(DesktopAppType.APP_COMPATIBILITY)
                                }
                            )
                        }
                    }

                    // Layer 5: Desktop Status Toast Banner
                    statusBanner?.let { msg ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = denColors.glassSurface,
                            shadowElevation = 8.dp
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = denColors.textPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Taskbar (or Auto-Hide Reveal Trigger Strip)
                if (showTaskbar) {
                    Taskbar(
                        isStartMenuOpen = isStartMenuOpen,
                        isSearchOpen = isStartMenuOpen && startMenuSearchFocus,
                        isQuickSettingsOpen = isQuickSettingsOpen,
                        pinnedItems = desktopItems,
                        openWindows = windowState.windows,
                        focusedWindowId = windowState.focusedWindowId,
                        clockText = clockDate.first,
                        dateText = clockDate.second,
                        battery = battery,
                        network = network,
                        bluetoothEnabled = bluetoothEnabled,
                        inputInfo = connectedInputs,
                        getAndroidAppIcon = { pkg -> viewModel.installedAppsRepository.getIconForPackage(pkg) },
                        onToggleStartMenu = { viewModel.toggleStartMenu(focusSearch = false) },
                        onToggleSearch = { viewModel.toggleStartMenu(focusSearch = true) },
                        onClickPinnedItem = { viewModel.openDesktopItem(it) },
                        onClickWindowItem = { win ->
                            viewModel.dismissOverlays()
                            viewModel.windowManager.toggleTaskbarWindow(win.id)
                        },
                        onToggleQuickSettings = { viewModel.toggleQuickSettings() },
                        onShowDesktop = {
                            viewModel.dismissOverlays()
                            viewModel.windowManager.toggleShowDesktop()
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .background(denColors.accent.copy(alpha = 0.25f))
                            .clickable { taskbarRevealedWhenAutoHidden = true }
                    )
                }
            }

            if (isRunDialogOpen) {
                RunDialog(
                    onDismiss = { viewModel.setRunDialogOpen(false) },
                    onExecuteCommand = { viewModel.executeRunCommand(it) }
                )
            }
        }
    }
}
