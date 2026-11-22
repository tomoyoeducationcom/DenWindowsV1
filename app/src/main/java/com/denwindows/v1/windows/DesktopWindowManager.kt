package com.denwindows.v1.windows

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class WindowManagerState(
    val windows: List<DesktopWindow> = emptyList(),
    val focusedWindowId: String? = null,
    val desktopAllMinimized: Boolean = false
)

class DesktopWindowManager {

    private val _state = MutableStateFlow(WindowManagerState())
    val state: StateFlow<WindowManagerState> = _state.asStateFlow()

    private var workspaceWidthDp: Float = 860f
    private var workspaceHeightDp: Float = 440f
    private var nextZIndex: Int = 1

    fun updateWorkspaceBounds(widthDp: Float, heightDp: Float) {
        if (widthDp <= 0f || heightDp <= 0f) return
        workspaceWidthDp = widthDp
        workspaceHeightDp = heightDp
        _state.update { current ->
            current.copy(
                windows = current.windows.map { win ->
                    val maxW = widthDp.coerceAtLeast(win.appType.minWidthDp)
                    val maxH = heightDp.coerceAtLeast(win.appType.minHeightDp)
                    val clampedW = win.widthDp.coerceAtMost(maxW)
                    val clampedH = win.heightDp.coerceAtMost(maxH)
                    val clampedX = win.xDp.coerceIn(0f, (widthDp - clampedW).coerceAtLeast(0f))
                    val clampedY = win.yDp.coerceIn(0f, (heightDp - clampedH).coerceAtLeast(0f))
                    win.copy(xDp = clampedX, yDp = clampedY, widthDp = clampedW, heightDp = clampedH)
                }
            )
        }
    }

    fun openApp(
        appType: DesktopAppType,
        payload: String? = null,
        allowMultiple: Boolean = false
    ): String {
        val existing = if (!allowMultiple) {
            _state.value.windows.firstOrNull { it.appType == appType }
        } else {
            null
        }
        if (existing != null) {
            val updatedTitle = if (!payload.isNullOrBlank() && appType == DesktopAppType.FILE_EXPLORER) {
                appType.defaultTitle
            } else {
                existing.title
            }
            val restoredMode = if (existing.stateMode == WindowStateMode.MINIMIZED) {
                existing.previousStateMode.takeIf { it != WindowStateMode.MINIMIZED } ?: WindowStateMode.NORMAL
            } else {
                existing.stateMode
            }
            val z = nextZIndex++
            _state.update { current ->
                current.copy(
                    windows = current.windows.map { w ->
                        if (w.id == existing.id) {
                            w.copy(
                                title = updatedTitle,
                                stateMode = restoredMode,
                                zIndex = z,
                                payload = payload ?: w.payload
                            )
                        } else w
                    },
                    focusedWindowId = existing.id,
                    desktopAllMinimized = false
                )
            }
            return existing.id
        }

        val count = _state.value.windows.size
        val w = appType.defaultWidthDp.coerceAtMost((workspaceWidthDp - 16f).coerceAtLeast(appType.minWidthDp))
        val h = appType.defaultHeightDp.coerceAtMost((workspaceHeightDp - 16f).coerceAtLeast(appType.minHeightDp))
        val cascadeOffset = (count % 5) * 22f
        val x = ((workspaceWidthDp - w) / 2f + cascadeOffset).coerceIn(8f, (workspaceWidthDp - w - 8f).coerceAtLeast(8f))
        val y = ((workspaceHeightDp - h) / 2f + cascadeOffset * 0.6f).coerceIn(8f, (workspaceHeightDp - h - 8f).coerceAtLeast(8f))

        val newId = "${appType.name}_${UUID.randomUUID().toString().take(6)}"
        val window = DesktopWindow(
            id = newId,
            appType = appType,
            title = appType.defaultTitle,
            xDp = x,
            yDp = y,
            widthDp = w,
            heightDp = h,
            zIndex = nextZIndex++,
            stateMode = WindowStateMode.NORMAL,
            payload = payload
        )

        _state.update { current ->
            current.copy(
                windows = current.windows + window,
                focusedWindowId = newId,
                desktopAllMinimized = false
            )
        }
        return newId
    }

    fun focusWindow(windowId: String) {
        val target = _state.value.windows.firstOrNull { it.id == windowId } ?: return
        val z = nextZIndex++
        val targetMode = if (target.stateMode == WindowStateMode.MINIMIZED) {
            target.previousStateMode.takeIf { it != WindowStateMode.MINIMIZED } ?: WindowStateMode.NORMAL
        } else {
            target.stateMode
        }
        _state.update { current ->
            current.copy(
                windows = current.windows.map {
                    if (it.id == windowId) it.copy(zIndex = z, stateMode = targetMode) else it
                },
                focusedWindowId = windowId,
                desktopAllMinimized = false
            )
        }
    }

    fun toggleTaskbarWindow(windowId: String) {
        val current = _state.value
        val target = current.windows.firstOrNull { it.id == windowId } ?: return
        if (target.stateMode == WindowStateMode.MINIMIZED || current.focusedWindowId != windowId) {
            focusWindow(windowId)
        } else {
            minimizeWindow(windowId)
        }
    }

    fun minimizeWindow(windowId: String) {
        _state.update { current ->
            val updated = current.windows.map { w ->
                if (w.id == windowId && w.stateMode != WindowStateMode.MINIMIZED) {
                    w.copy(previousStateMode = w.stateMode, stateMode = WindowStateMode.MINIMIZED)
                } else w
            }
            val nextFocus = updated
                .filter { it.stateMode != WindowStateMode.MINIMIZED }
                .maxByOrNull { it.zIndex }
                ?.id
            current.copy(windows = updated, focusedWindowId = nextFocus)
        }
    }

    fun toggleMaximizeRestore(windowId: String) {
        val z = nextZIndex++
        _state.update { current ->
            current.copy(
                windows = current.windows.map { w ->
                    if (w.id == windowId) {
                        val nextMode = if (w.stateMode == WindowStateMode.MAXIMIZED) {
                            WindowStateMode.NORMAL
                        } else {
                            WindowStateMode.MAXIMIZED
                        }
                        w.copy(stateMode = nextMode, zIndex = z)
                    } else w
                },
                focusedWindowId = windowId
            )
        }
    }

    fun snapLeft(windowId: String) {
        val z = nextZIndex++
        _state.update { current ->
            current.copy(
                windows = current.windows.map { w ->
                    if (w.id == windowId) {
                        val next = if (w.stateMode == WindowStateMode.SNAPPED_LEFT) WindowStateMode.NORMAL else WindowStateMode.SNAPPED_LEFT
                        w.copy(stateMode = next, zIndex = z)
                    } else w
                },
                focusedWindowId = windowId
            )
        }
    }

    fun snapRight(windowId: String) {
        val z = nextZIndex++
        _state.update { current ->
            current.copy(
                windows = current.windows.map { w ->
                    if (w.id == windowId) {
                        val next = if (w.stateMode == WindowStateMode.SNAPPED_RIGHT) WindowStateMode.NORMAL else WindowStateMode.SNAPPED_RIGHT
                        w.copy(stateMode = next, zIndex = z)
                    } else w
                },
                focusedWindowId = windowId
            )
        }
    }

    fun centerWindow(windowId: String) {
        val z = nextZIndex++
        _state.update { current ->
            current.copy(
                windows = current.windows.map { w ->
                    if (w.id == windowId) {
                        val cx = ((workspaceWidthDp - w.widthDp) / 2f).coerceAtLeast(4f)
                        val cy = ((workspaceHeightDp - w.heightDp) / 2f).coerceAtLeast(4f)
                        w.copy(stateMode = WindowStateMode.NORMAL, xDp = cx, yDp = cy, zIndex = z)
                    } else w
                },
                focusedWindowId = windowId
            )
        }
    }

    fun dragWindow(windowId: String, deltaXDp: Float, deltaYDp: Float) {
        _state.update { current ->
            current.copy(
                windows = current.windows.map { w ->
                    if (w.id == windowId) {
                        val baseW = if (w.stateMode == WindowStateMode.NORMAL) w else w.copy(stateMode = WindowStateMode.NORMAL)
                        val maxX = (workspaceWidthDp - 80f).coerceAtLeast(0f)
                        val maxY = (workspaceHeightDp - 44f).coerceAtLeast(0f)
                        baseW.copy(
                            xDp = (baseW.xDp + deltaXDp).coerceIn(-baseW.widthDp + 100f, maxX),
                            yDp = (baseW.yDp + deltaYDp).coerceIn(0f, maxY)
                        )
                    } else w
                }
            )
        }
    }

    fun resizeWindow(windowId: String, deltaWidthDp: Float, deltaHeightDp: Float) {
        _state.update { current ->
            current.copy(
                windows = current.windows.map { w ->
                    if (w.id == windowId) {
                        val newW = (w.widthDp + deltaWidthDp)
                            .coerceIn(w.appType.minWidthDp, (workspaceWidthDp - w.xDp).coerceAtLeast(w.appType.minWidthDp))
                        val newH = (w.heightDp + deltaHeightDp)
                            .coerceIn(w.appType.minHeightDp, (workspaceHeightDp - w.yDp).coerceAtLeast(w.appType.minHeightDp))
                        w.copy(
                            stateMode = WindowStateMode.NORMAL,
                            widthDp = newW,
                            heightDp = newH
                        )
                    } else w
                }
            )
        }
    }

    fun updateWindowTitle(windowId: String, title: String) {
        _state.update { current ->
            current.copy(
                windows = current.windows.map { if (it.id == windowId) it.copy(title = title) else it }
            )
        }
    }

    fun closeWindow(windowId: String) {
        _state.update { current ->
            val remaining = current.windows.filterNot { it.id == windowId }
            val nextFocus = remaining
                .filter { it.stateMode != WindowStateMode.MINIMIZED }
                .maxByOrNull { it.zIndex }
                ?.id
            current.copy(windows = remaining, focusedWindowId = nextFocus)
        }
    }

    fun closeFocusedWindow() {
        _state.value.focusedWindowId?.let { closeWindow(it) }
    }

    fun closeAllWindows() {
        _state.update { WindowManagerState() }
    }

    fun cycleNextWindow() {
        val current = _state.value
        if (current.windows.isEmpty()) return
        val sorted = current.windows.sortedByDescending { it.zIndex }
        val next = if (sorted.size == 1) sorted.first() else sorted[1]
        focusWindow(next.id)
    }

    fun toggleShowDesktop() {
        _state.update { current ->
            val hasVisible = current.windows.any { it.stateMode != WindowStateMode.MINIMIZED }
            if (hasVisible) {
                current.copy(
                    windows = current.windows.map { w ->
                        if (w.stateMode != WindowStateMode.MINIMIZED) {
                            w.copy(previousStateMode = w.stateMode, stateMode = WindowStateMode.MINIMIZED)
                        } else w
                    },
                    focusedWindowId = null,
                    desktopAllMinimized = true
                )
            } else {
                val restored = current.windows.map { w ->
                    val prev = w.previousStateMode.takeIf { it != WindowStateMode.MINIMIZED } ?: WindowStateMode.NORMAL
                    w.copy(stateMode = prev)
                }
                current.copy(
                    windows = restored,
                    focusedWindowId = restored.maxByOrNull { it.zIndex }?.id,
                    desktopAllMinimized = false
                )
            }
        }
    }
}
