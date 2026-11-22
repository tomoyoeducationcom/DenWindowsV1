package com.denwindows.v1.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class DesktopRepository(
    private val context: Context,
    private val database: DenDatabase
) {
    private val desktopItemDao = database.desktopItemDao()
    private val browserDao = database.browserDao()
    private val calendarDao = database.calendarDao()
    private val recentAppDao = database.recentAppDao()

    val desktopItems: Flow<List<DesktopItemEntity>> = desktopItemDao.observeAll()
    val bookmarks: Flow<List<BookmarkEntity>> = browserDao.observeBookmarks()
    val history: Flow<List<HistoryEntity>> = browserDao.observeHistory()
    val calendarEvents: Flow<List<CalendarEventEntity>> = calendarDao.observeAllEvents()
    val recentApps: Flow<List<RecentAppEntity>> = recentAppDao.observeRecent()

    val sandboxRootDir: File
        get() = File(context.filesDir, "DenUser").apply { if (!exists()) mkdirs() }

    val desktopDir: File
        get() = File(sandboxRootDir, "Desktop").apply { if (!exists()) mkdirs() }

    val documentsDir: File
        get() = File(sandboxRootDir, "Documents").apply { if (!exists()) mkdirs() }

    val downloadsDir: File
        get() = File(sandboxRootDir, "Downloads").apply { if (!exists()) mkdirs() }

    val picturesDir: File
        get() = File(sandboxRootDir, "Pictures").apply { if (!exists()) mkdirs() }

    val moviesDir: File
        get() = File(sandboxRootDir, "Movies").apply { if (!exists()) mkdirs() }

    val musicDir: File
        get() = File(sandboxRootDir, "Music").apply { if (!exists()) mkdirs() }

    val projectsDir: File
        get() = File(sandboxRootDir, "Projects").apply { if (!exists()) mkdirs() }

    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        // Ensure standard sandbox folders exist
        listOf(desktopDir, documentsDir, downloadsDir, picturesDir, moviesDir, musicDir, projectsDir)
            .forEach { if (!it.exists()) it.mkdirs() }

        // Seed a helpful welcome document if Documents is empty
        val welcomeNote = File(documentsDir, "Welcome_to_DenWindowsV1.txt")
        if (!welcomeNote.exists()) {
            welcomeNote.writeText(
                """
                DenWindowsV1 Desktop Environment
                =================================
                
                Keyboard Shortcuts:
                  Alt + Tab      Switch active internal windows
                  Alt + F4       Close focused internal window
                  Win / Super    Toggle Start Menu
                  Win + E        Open File Explorer
                  Win + R        Open Run Dialog
                  Win + D        Minimize / Restore all windows
                
                Tip: Right-click or long-press the desktop to create folders,
                change wallpaper, or open the Android Shell Terminal.
                """.trimIndent()
            )
        }

        if (desktopItemDao.count() == 0) {
            desktopItemDao.upsertAll(defaultDesktopItems())
        } else {
            // Ensure DenCode shortcut is available if upgrading from an existing local DB
            desktopItemDao.upsert(defaultCodeEditorItem())
        }
    }

    private fun defaultCodeEditorItem() = DesktopItemEntity(
        id = "internal_CODE_EDITOR",
        title = "DenCode",
        kind = DesktopItemKind.INTERNAL_APP.name,
        target = "CODE_EDITOR",
        gridCol = 2,
        gridRow = 0,
        isPinnedToTaskbar = true,
        isPinnedToStart = true,
        sortOrder = 3
    )

    private fun defaultDesktopItems(): List<DesktopItemEntity> = listOf(
        DesktopItemEntity(
            id = "internal_FILE_EXPLORER",
            title = "File Explorer",
            kind = DesktopItemKind.INTERNAL_APP.name,
            target = "FILE_EXPLORER",
            gridCol = 0,
            gridRow = 0,
            isPinnedToTaskbar = true,
            isPinnedToStart = true,
            sortOrder = 0
        ),
        DesktopItemEntity(
            id = "internal_BROWSER",
            title = "DenBrowser",
            kind = DesktopItemKind.INTERNAL_APP.name,
            target = "BROWSER",
            gridCol = 0,
            gridRow = 1,
            isPinnedToTaskbar = true,
            isPinnedToStart = true,
            sortOrder = 1
        ),
        DesktopItemEntity(
            id = "internal_TERMINAL",
            title = "Terminal",
            kind = DesktopItemKind.INTERNAL_APP.name,
            target = "TERMINAL",
            gridCol = 0,
            gridRow = 2,
            isPinnedToTaskbar = true,
            isPinnedToStart = true,
            sortOrder = 2
        ),
        DesktopItemEntity(
            id = "internal_NOTEPAD",
            title = "Notepad",
            kind = DesktopItemKind.INTERNAL_APP.name,
            target = "NOTEPAD",
            gridCol = 0,
            gridRow = 3,
            isPinnedToTaskbar = false,
            isPinnedToStart = true,
            sortOrder = 3
        ),
        DesktopItemEntity(
            id = "internal_CALCULATOR",
            title = "Calculator",
            kind = DesktopItemKind.INTERNAL_APP.name,
            target = "CALCULATOR",
            gridCol = 1,
            gridRow = 0,
            isPinnedToTaskbar = false,
            isPinnedToStart = true,
            sortOrder = 4
        ),
        DesktopItemEntity(
            id = "internal_CLOCK",
            title = "Clock",
            kind = DesktopItemKind.INTERNAL_APP.name,
            target = "CLOCK",
            gridCol = 1,
            gridRow = 1,
            isPinnedToTaskbar = false,
            isPinnedToStart = true,
            sortOrder = 5
        ),
        DesktopItemEntity(
            id = "internal_CALENDAR",
            title = "Calendar",
            kind = DesktopItemKind.INTERNAL_APP.name,
            target = "CALENDAR",
            gridCol = 1,
            gridRow = 2,
            isPinnedToTaskbar = false,
            isPinnedToStart = true,
            sortOrder = 6
        ),
        DesktopItemEntity(
            id = "internal_SETTINGS",
            title = "Settings",
            kind = DesktopItemKind.INTERNAL_APP.name,
            target = "SETTINGS",
            gridCol = 1,
            gridRow = 3,
            isPinnedToTaskbar = true,
            isPinnedToStart = true,
            sortOrder = 7
        ),
        defaultCodeEditorItem()
    )

    suspend fun moveDesktopItem(id: String, col: Int, row: Int) {
        desktopItemDao.updatePosition(id, col.coerceAtLeast(0), row.coerceAtLeast(0))
    }

    suspend fun createDesktopFolder(name: String, col: Int, row: Int) = withContext(Dispatchers.IO) {
        val safeName = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "New Folder" }
        var folder = File(desktopDir, safeName)
        var index = 2
        while (folder.exists()) {
            folder = File(desktopDir, "$safeName ($index)")
            index++
        }
        folder.mkdirs()
        desktopItemDao.upsert(
            DesktopItemEntity(
                id = "folder_${folder.name}_${System.currentTimeMillis()}",
                title = folder.name,
                kind = DesktopItemKind.FOLDER.name,
                target = folder.absolutePath,
                gridCol = col,
                gridRow = row,
                isPinnedToTaskbar = false,
                isPinnedToStart = false,
                sortOrder = 100
            )
        )
    }

    suspend fun addAndroidAppToDesktop(packageName: String, title: String, col: Int, row: Int) {
        desktopItemDao.upsert(
            DesktopItemEntity(
                id = "android_$packageName",
                title = title,
                kind = DesktopItemKind.ANDROID_APP.name,
                target = packageName,
                gridCol = col,
                gridRow = row,
                isPinnedToTaskbar = false,
                isPinnedToStart = false,
                sortOrder = 50
            )
        )
    }

    suspend fun togglePinToTaskbar(id: String, pinned: Boolean, title: String? = null, kind: String? = null, target: String? = null) {
        if (title != null && kind != null && target != null) {
            desktopItemDao.upsert(
                DesktopItemEntity(
                    id = id,
                    title = title,
                    kind = kind,
                    target = target,
                    gridCol = -1,
                    gridRow = -1,
                    isPinnedToTaskbar = pinned,
                    isPinnedToStart = true
                )
            )
        } else {
            desktopItemDao.setPinnedToTaskbar(id, pinned)
        }
    }

    suspend fun setPinnedToStart(id: String, pinned: Boolean) {
        desktopItemDao.setPinnedToStart(id, pinned)
    }

    suspend fun removeDesktopItem(id: String) {
        desktopItemDao.deleteById(id)
    }

    suspend fun addBookmark(title: String, url: String) {
        browserDao.addBookmark(BookmarkEntity(title = title.ifBlank { url }, url = url))
    }

    suspend fun removeBookmark(url: String) {
        browserDao.removeBookmarkByUrl(url)
    }

    suspend fun recordHistory(title: String, url: String) {
        browserDao.addHistory(HistoryEntity(title = title.ifBlank { url }, url = url))
    }

    suspend fun clearHistory() {
        browserDao.clearHistory()
    }

    suspend fun saveCalendarEvent(event: CalendarEventEntity) {
        calendarDao.upsertEvent(event)
    }

    suspend fun deleteCalendarEvent(id: Long) {
        calendarDao.deleteEvent(id)
    }

    suspend fun recordAppLaunch(key: String, title: String, kind: String, target: String) {
        recentAppDao.recordLaunch(
            RecentAppEntity(
                key = key,
                title = title,
                kind = kind,
                target = target,
                lastLaunchedAt = System.currentTimeMillis()
            )
        )
    }
}
