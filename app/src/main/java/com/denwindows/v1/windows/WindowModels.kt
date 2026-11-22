package com.denwindows.v1.windows

enum class DesktopAppType(
    val defaultTitle: String,
    val defaultWidthDp: Float,
    val defaultHeightDp: Float,
    val minWidthDp: Float = 320f,
    val minHeightDp: Float = 240f
) {
    FILE_EXPLORER("File Explorer", 680f, 430f, 420f, 280f),
    BROWSER("DenBrowser", 720f, 450f, 420f, 280f),
    TERMINAL("Terminal", 640f, 400f, 380f, 250f),
    CODE_EDITOR("DenCode", 760f, 460f, 460f, 300f),
    NOTEPAD("Notepad", 560f, 390f, 340f, 240f),
    CALCULATOR("Calculator", 330f, 420f, 290f, 360f),
    CLOCK("Clock & Timers", 480f, 360f, 360f, 280f),
    CALENDAR("Calendar", 580f, 400f, 380f, 300f),
    TASK_MANAGER("Task Manager", 580f, 400f, 400f, 280f),
    SETTINGS("Settings", 680f, 440f, 440f, 300f),
    APP_COMPATIBILITY("Window & App Diagnostics", 560f, 390f, 380f, 280f)
}

enum class WindowStateMode {
    NORMAL,
    MINIMIZED,
    MAXIMIZED,
    SNAPPED_LEFT,
    SNAPPED_RIGHT
}

data class DesktopWindow(
    val id: String,
    val appType: DesktopAppType,
    val title: String,
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val zIndex: Int,
    val stateMode: WindowStateMode = WindowStateMode.NORMAL,
    val previousStateMode: WindowStateMode = WindowStateMode.NORMAL,
    val payload: String? = null
)
