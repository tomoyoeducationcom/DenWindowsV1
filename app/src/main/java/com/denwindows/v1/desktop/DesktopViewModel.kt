package com.denwindows.v1.desktop

import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.denwindows.v1.applications.InstalledAppInfo
import com.denwindows.v1.applications.InstalledAppsRepository
import com.denwindows.v1.data.BookmarkEntity
import com.denwindows.v1.data.CalendarEventEntity
import com.denwindows.v1.data.DenDatabase
import com.denwindows.v1.data.DesktopIconSize
import com.denwindows.v1.data.DesktopItemEntity
import com.denwindows.v1.data.DesktopItemKind
import com.denwindows.v1.data.DesktopRepository
import com.denwindows.v1.data.HistoryEntity
import com.denwindows.v1.data.PreferencesRepository
import com.denwindows.v1.data.RecentAppEntity
import com.denwindows.v1.data.UserPreferences
import com.denwindows.v1.system.BatteryTelemetry
import com.denwindows.v1.system.CompatibilityDiagnostics
import com.denwindows.v1.system.ConnectedInputInfo
import com.denwindows.v1.system.MemoryInfoSnapshot
import com.denwindows.v1.system.NetworkTelemetry
import com.denwindows.v1.system.StorageInfo
import com.denwindows.v1.system.SystemStatusRepository
import com.denwindows.v1.windows.DesktopAppType
import com.denwindows.v1.windows.DesktopWindowManager
import com.denwindows.v1.windows.WindowManagerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DesktopViewModel(application: Application) : AndroidViewModel(application) {

    val preferencesRepository = PreferencesRepository(application)
    private val database = DenDatabase.getInstance(application)
    val desktopRepository = DesktopRepository(application, database)
    val installedAppsRepository = InstalledAppsRepository(application)
    val systemStatusRepository = SystemStatusRepository(application)
    val windowManager = DesktopWindowManager()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val desktopItems: StateFlow<List<DesktopItemEntity>> = desktopRepository.desktopItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentApps: StateFlow<List<RecentAppEntity>> = desktopRepository.recentApps
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = desktopRepository.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = desktopRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calendarEvents: StateFlow<List<CalendarEventEntity>> = desktopRepository.calendarEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedApps: StateFlow<List<InstalledAppInfo>> = installedAppsRepository.installedApps
    val installedBrowsers: StateFlow<List<InstalledAppInfo>> = installedAppsRepository.installedBrowsers
    val windowState: StateFlow<WindowManagerState> = windowManager.state

    val clockDate: StateFlow<Pair<String, String>> = systemStatusRepository.observeClock()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "12:00 PM" to "Today")

    val battery: StateFlow<BatteryTelemetry> = systemStatusRepository.observeBattery()
        .stateIn(viewModelScope, SharingStarted.Eagerly, BatteryTelemetry())

    val network: StateFlow<NetworkTelemetry> = systemStatusRepository.observeNetwork()
        .stateIn(viewModelScope, SharingStarted.Eagerly, NetworkTelemetry())

    val bluetoothEnabled: StateFlow<Boolean> = systemStatusRepository.observeBluetoothEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val connectedInputs: StateFlow<ConnectedInputInfo> = systemStatusRepository.observeConnectedInputDevices()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectedInputInfo())

    private val _isStartMenuOpen = MutableStateFlow(false)
    val isStartMenuOpen: StateFlow<Boolean> = _isStartMenuOpen.asStateFlow()

    private val _startMenuSearchFocus = MutableStateFlow(false)
    val startMenuSearchFocus: StateFlow<Boolean> = _startMenuSearchFocus.asStateFlow()

    private val _isQuickSettingsOpen = MutableStateFlow(false)
    val isQuickSettingsOpen: StateFlow<Boolean> = _isQuickSettingsOpen.asStateFlow()

    private val _isRunDialogOpen = MutableStateFlow(false)
    val isRunDialogOpen: StateFlow<Boolean> = _isRunDialogOpen.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch {
            desktopRepository.ensureInitialized()
            installedAppsRepository.refreshInstalledApps()
        }
    }

    fun refreshDesktopAndApps() {
        viewModelScope.launch {
            installedAppsRepository.refreshInstalledApps()
            showBanner("Refreshed desktop and installed Android applications.")
        }
    }

    fun refreshInstalledAppsSilently() {
        viewModelScope.launch {
            installedAppsRepository.refreshInstalledApps()
        }
    }

    fun toggleStartMenu(focusSearch: Boolean = false) {
        val next = if (focusSearch && !_isStartMenuOpen.value) true else !_isStartMenuOpen.value
        _isStartMenuOpen.value = next
        _startMenuSearchFocus.value = focusSearch && next
        if (next) _isQuickSettingsOpen.value = false
    }

    fun toggleQuickSettings() {
        val next = !_isQuickSettingsOpen.value
        _isQuickSettingsOpen.value = next
        if (next) _isStartMenuOpen.value = false
    }

    fun dismissOverlays() {
        _isStartMenuOpen.value = false
        _isQuickSettingsOpen.value = false
    }

    fun setRunDialogOpen(open: Boolean) {
        _isRunDialogOpen.value = open
        if (open) dismissOverlays()
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun openInternalApp(appType: DesktopAppType, payload: String? = null) {
        dismissOverlays()
        windowManager.openApp(appType, payload)
        viewModelScope.launch(Dispatchers.IO) {
            desktopRepository.recordAppLaunch(
                key = "INTERNAL:${appType.name}",
                title = appType.defaultTitle,
                kind = "INTERNAL",
                target = appType.name
            )
        }
    }

    fun openDesktopItem(item: DesktopItemEntity) {
        when (item.kind) {
            DesktopItemKind.INTERNAL_APP.name -> {
                runCatching { DesktopAppType.valueOf(item.target) }.getOrNull()?.let {
                    openInternalApp(it)
                }
            }
            DesktopItemKind.FOLDER.name -> {
                openInternalApp(DesktopAppType.FILE_EXPLORER, item.target)
            }
            DesktopItemKind.ANDROID_APP.name -> {
                launchAndroidPackage(item.target, item.title)
            }
        }
    }

    fun launchAndroidApp(app: InstalledAppInfo) {
        launchAndroidPackage(app.packageName, app.label)
    }

    private fun launchAndroidPackage(packageName: String, label: String) {
        dismissOverlays()
        val result = installedAppsRepository.launchApp(
            packageName = packageName,
            tryFreeform = preferences.value.tryFreeformOnAppLaunch
        )
        if (!result.launched) {
            showBanner(result.message)
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                desktopRepository.recordAppLaunch(
                    key = "ANDROID:$packageName",
                    title = label,
                    kind = "ANDROID",
                    target = packageName
                )
            }
        }
    }

    fun openUrlExternally(url: String) {
        val ok = installedAppsRepository.openUrlInExternalBrowser(
            url = url,
            preferredPackage = preferences.value.defaultExternalBrowserPkg
        )
        if (!ok) showBanner("No external Android browser available to open URL.")
    }

    fun enqueueDownload(url: String, fileName: String) {
        runCatching {
            val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(getApplication(), Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            dm.enqueue(request)
            showBanner("Downloading $fileName via Android DownloadManager.")
        }.onFailure {
            showBanner("Download unavailable: ${it.localizedMessage}")
        }
    }

    fun executeRunCommand(raw: String) {
        val lower = raw.trim().lowercase()
        when {
            lower in setOf("cmd", "terminal", "sh", "shell") -> openInternalApp(DesktopAppType.TERMINAL)
            lower in setOf("code", "vscode", "dencode", "ide") -> openInternalApp(DesktopAppType.CODE_EDITOR)
            lower in setOf("explorer", "files") -> openInternalApp(DesktopAppType.FILE_EXPLORER)
            lower in setOf("calc", "calculator") -> openInternalApp(DesktopAppType.CALCULATOR)
            lower in setOf("notepad", "edit") -> openInternalApp(DesktopAppType.NOTEPAD)
            lower in setOf("browser", "web") -> openInternalApp(DesktopAppType.BROWSER)
            lower in setOf("clock", "timer") -> openInternalApp(DesktopAppType.CLOCK)
            lower in setOf("calendar", "cal") -> openInternalApp(DesktopAppType.CALENDAR)
            lower in setOf("taskmgr", "taskmanager") -> openInternalApp(DesktopAppType.TASK_MANAGER)
            lower in setOf("settings", "control") -> openInternalApp(DesktopAppType.SETTINGS)
            lower.startsWith("http://") || lower.startsWith("https://") || lower.contains(".") -> {
                openInternalApp(DesktopAppType.BROWSER, raw.trim())
            }
            else -> {
                val app = installedApps.value.firstOrNull {
                    it.label.lowercase().contains(lower) || it.packageName.lowercase() == lower
                }
                if (app != null) launchAndroidApp(app)
                else showBanner("Could not find '$raw'.")
            }
        }
    }

    fun openFileFromSearch(file: File) {
        if (file.isDirectory) {
            openInternalApp(DesktopAppType.FILE_EXPLORER, file.absolutePath)
        } else {
            val ext = file.extension.lowercase()
            when {
                ext in setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "html", "htm", "css", "scss", "json", "xml", "sh", "md") ->
                    openInternalApp(DesktopAppType.CODE_EDITOR, file.absolutePath)
                ext in setOf("txt", "csv", "log", "ini", "conf") ->
                    openInternalApp(DesktopAppType.NOTEPAD, file.absolutePath)
                else ->
                    openInternalApp(DesktopAppType.FILE_EXPLORER, file.parentFile?.absolutePath)
            }
        }
    }

    fun getSearchableFiles(): List<File> {
        val dirs = listOf(
            desktopRepository.desktopDir,
            desktopRepository.documentsDir,
            desktopRepository.downloadsDir,
            desktopRepository.picturesDir,
            desktopRepository.projectsDir
        )
        return dirs.flatMap { it.listFiles()?.toList().orEmpty() }.take(80)
    }

    fun moveDesktopItem(id: String, col: Int, row: Int) {
        viewModelScope.launch { desktopRepository.moveDesktopItem(id, col, row) }
    }

    fun createDesktopFolder(name: String, col: Int, row: Int) {
        viewModelScope.launch { desktopRepository.createDesktopFolder(name, col, row) }
    }

    fun togglePinItemToTaskbar(item: DesktopItemEntity, pinned: Boolean) {
        viewModelScope.launch { desktopRepository.togglePinToTaskbar(item.id, pinned) }
    }

    fun pinAndroidAppToTaskbar(app: InstalledAppInfo) {
        viewModelScope.launch {
            desktopRepository.togglePinToTaskbar(
                id = "android_${app.packageName}",
                pinned = true,
                title = app.label,
                kind = DesktopItemKind.ANDROID_APP.name,
                target = app.packageName
            )
            showBanner("Pinned ${app.label} to Taskbar.")
        }
    }

    fun addAndroidAppToDesktop(app: InstalledAppInfo) {
        viewModelScope.launch {
            val placedCount = desktopItems.value.count { it.gridCol >= 0 && it.gridRow >= 0 }
            desktopRepository.addAndroidAppToDesktop(
                packageName = app.packageName,
                title = app.label,
                col = (placedCount / 4).coerceAtMost(6),
                row = placedCount % 4
            )
            showBanner("Added ${app.label} shortcut to Desktop.")
        }
    }

    fun removeDesktopItem(item: DesktopItemEntity) {
        viewModelScope.launch { desktopRepository.removeDesktopItem(item.id) }
    }

    fun setDarkTheme(dark: Boolean) = viewModelScope.launch { preferencesRepository.setDarkTheme(dark) }
    fun setAccent(id: String) = viewModelScope.launch { preferencesRepository.setAccentId(id) }
    fun setBuiltInWallpaper(id: String) = viewModelScope.launch { preferencesRepository.setBuiltInWallpaper(id) }
    fun setCustomWallpaperUri(uri: String?) = viewModelScope.launch { preferencesRepository.setCustomWallpaperUri(uri) }
    fun setIconSize(size: DesktopIconSize) = viewModelScope.launch { preferencesRepository.setIconSize(size) }
    fun setTaskbarAutoHide(autoHide: Boolean) = viewModelScope.launch { preferencesRepository.setTaskbarAutoHide(autoHide) }
    fun setWindowAnimations(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setWindowAnimations(enabled) }
    fun setDefaultBrowser(pkg: String?) = viewModelScope.launch { preferencesRepository.setDefaultExternalBrowser(pkg) }
    fun setSearchEngine(id: String) = viewModelScope.launch { preferencesRepository.setSearchEngine(id) }
    fun setTryFreeform(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setTryFreeform(enabled) }
    fun setMouseHover(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setMouseHoverHighlight(enabled) }
    fun addSafTreeUri(uri: String) = viewModelScope.launch { preferencesRepository.addSafTreeUri(uri) }

    fun addBookmark(title: String, url: String) = viewModelScope.launch { desktopRepository.addBookmark(title, url) }
    fun removeBookmark(url: String) = viewModelScope.launch { desktopRepository.removeBookmark(url) }
    fun recordHistory(title: String, url: String) = viewModelScope.launch { desktopRepository.recordHistory(title, url) }
    fun clearHistory() = viewModelScope.launch { desktopRepository.clearHistory() }
    fun saveCalendarEvent(event: CalendarEventEntity) = viewModelScope.launch { desktopRepository.saveCalendarEvent(event) }
    fun deleteCalendarEvent(id: Long) = viewModelScope.launch { desktopRepository.deleteCalendarEvent(id) }

    fun getStorageInfo(): StorageInfo = systemStatusRepository.getStorageInfo()
    fun getMemorySnapshot(): MemoryInfoSnapshot = systemStatusRepository.getMemorySnapshot()
    fun getDiagnostics(activity: Activity?): CompatibilityDiagnostics =
        systemStatusRepository.getCompatibilityDiagnostics(activity)

    fun openAndroidSettings(action: String = Settings.ACTION_SETTINGS) {
        systemStatusRepository.openAndroidSystemSettings(action)
    }

    fun openHomeLauncherSettings() {
        systemStatusRepository.openAndroidSystemSettings(Settings.ACTION_HOME_SETTINGS)
    }
}
