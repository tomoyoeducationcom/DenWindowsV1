package com.denwindows.v1.windows

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.denwindows.v1.theme.LocalDenColors

fun DesktopAppType.iconVector(): ImageVector = when (this) {
    DesktopAppType.FILE_EXPLORER -> Icons.Filled.Folder
    DesktopAppType.BROWSER -> Icons.Filled.Language
    DesktopAppType.TERMINAL -> Icons.Filled.Terminal
    DesktopAppType.CODE_EDITOR -> Icons.Filled.Code
    DesktopAppType.NOTEPAD -> Icons.Filled.Description
    DesktopAppType.CALCULATOR -> Icons.Filled.Calculate
    DesktopAppType.CLOCK -> Icons.Filled.Schedule
    DesktopAppType.CALENDAR -> Icons.Filled.CalendarMonth
    DesktopAppType.TASK_MANAGER -> Icons.Filled.Memory
    DesktopAppType.SETTINGS -> Icons.Filled.Settings
    DesktopAppType.APP_COMPATIBILITY -> Icons.Filled.VerifiedUser
}

@Composable
fun DesktopWindowFrame(
    window: DesktopWindow,
    isFocused: Boolean,
    workspaceWidthDp: Float,
    workspaceHeightDp: Float,
    animationsEnabled: Boolean,
    onFocus: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onSnapLeft: () -> Unit,
    onSnapRight: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    content: @Composable () -> Unit
) {
    if (window.stateMode == WindowStateMode.MINIMIZED) return

    val denColors = LocalDenColors.current
    val density = LocalDensity.current

    val targetX: Dp
    val targetY: Dp
    val targetW: Dp
    val targetH: Dp

    when (window.stateMode) {
        WindowStateMode.MAXIMIZED -> {
            targetX = 0.dp
            targetY = 0.dp
            targetW = workspaceWidthDp.dp
            targetH = workspaceHeightDp.dp
        }
        WindowStateMode.SNAPPED_LEFT -> {
            targetX = 4.dp
            targetY = 4.dp
            targetW = ((workspaceWidthDp / 2f) - 6f).coerceAtLeast(240f).dp
            targetH = (workspaceHeightDp - 8f).coerceAtLeast(200f).dp
        }
        WindowStateMode.SNAPPED_RIGHT -> {
            targetX = (workspaceWidthDp / 2f + 2f).dp
            targetY = 4.dp
            targetW = ((workspaceWidthDp / 2f) - 6f).coerceAtLeast(240f).dp
            targetH = (workspaceHeightDp - 8f).coerceAtLeast(200f).dp
        }
        else -> {
            targetX = window.xDp.dp
            targetY = window.yDp.dp
            targetW = window.widthDp.dp
            targetH = window.heightDp.dp
        }
    }

    val animDuration = if (animationsEnabled && window.stateMode != WindowStateMode.NORMAL) 140 else 0
    val animX by animateDpAsState(targetX, tween(animDuration), label = "wx")
    val animY by animateDpAsState(targetY, tween(animDuration), label = "wy")
    val animW by animateDpAsState(targetW, tween(animDuration), label = "ww")
    val animH by animateDpAsState(targetH, tween(animDuration), label = "wh")

    val cornerRadius = if (window.stateMode == WindowStateMode.MAXIMIZED) 4.dp else 12.dp
    val borderColor = if (isFocused) denColors.accent.copy(alpha = 0.68f) else denColors.border.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .offset(x = animX, y = animY)
            .size(width = animW, height = animH)
            .zIndex(window.zIndex.toFloat())
            .shadow(
                elevation = if (isFocused) 18.dp else 8.dp,
                shape = RoundedCornerShape(cornerRadius),
                clip = false
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(denColors.windowSurface)
            .pointerInput(window.id) {
                detectTapGestures(onPress = {
                    onFocus()
                    tryAwaitRelease()
                })
            }
            .testTag("window_${window.appType.name}")
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(cornerRadius),
            color = denColors.windowSurface,
            border = BorderStroke(if (isFocused) 1.2.dp else 1.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Window Title Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(
                            if (isFocused) denColors.secondarySurface
                            else denColors.windowSurface
                        )
                        .pointerInput(window.id, window.stateMode) {
                            detectTapGestures(
                                onTap = { onFocus() },
                                onDoubleTap = { onToggleMaximize() }
                            )
                        }
                        .pointerInput(window.id) {
                            detectDragGestures(
                                onDragStart = { onFocus() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = with(density) { dragAmount.x.toDp().value }
                                    val dy = with(density) { dragAmount.y.toDp().value }
                                    onDrag(dx, dy)
                                }
                            )
                        }
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = window.appType.iconVector(),
                        contentDescription = null,
                        tint = if (isFocused) denColors.accent else denColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = window.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 12.5.sp,
                            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (isFocused) denColors.textPrimary else denColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Snap Left / Right Quick Buttons
                    WindowCaptionButton(
                        icon = Icons.Outlined.ViewSidebar,
                        contentDesc = "Snap Left",
                        tint = denColors.textSecondary,
                        testTag = "window_snap_left_${window.id}",
                        onClick = onSnapLeft
                    )
                    WindowCaptionButton(
                        icon = Icons.Filled.Minimize,
                        contentDesc = "Minimize",
                        tint = denColors.textSecondary,
                        testTag = "window_minimize_${window.id}",
                        onClick = onMinimize
                    )
                    WindowCaptionButton(
                        icon = if (window.stateMode == WindowStateMode.MAXIMIZED) {
                            Icons.Filled.FilterNone
                        } else {
                            Icons.Filled.CropSquare
                        },
                        contentDesc = if (window.stateMode == WindowStateMode.MAXIMIZED) "Restore" else "Maximize",
                        tint = denColors.textSecondary,
                        testTag = "window_maximize_${window.id}",
                        onClick = onToggleMaximize
                    )
                    WindowCaptionButton(
                        icon = Icons.Filled.Close,
                        contentDesc = "Close",
                        tint = denColors.textPrimary,
                        hoverBackground = denColors.error.copy(alpha = 0.85f),
                        testTag = "window_close_${window.id}",
                        onClick = onClose
                    )
                }

                // Thin divider under title bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(denColors.border.copy(alpha = 0.6f))
                )

                // App Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    content()
                }
            }
        }

        // Bottom-right Resize Grip (only in NORMAL mode)
        if (window.stateMode == WindowStateMode.NORMAL) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .pointerHoverIcon(PointerIcon.Crosshair)
                    .pointerInput(window.id) {
                        detectDragGestures(
                            onDragStart = { onFocus() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dw = with(density) { dragAmount.x.toDp().value }
                                val dh = with(density) { dragAmount.y.toDp().value }
                                onResize(dw, dh)
                            }
                        )
                    }
                    .semantics { contentDescription = "Resize window" }
            ) {
                // Subtle diagonal resize indicator dots
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(denColors.textSecondary.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun WindowCaptionButton(
    icon: ImageVector,
    contentDesc: String,
    tint: Color,
    hoverBackground: Color? = null,
    testTag: String,
    onClick: () -> Unit
) {
    val denColors = LocalDenColors.current
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (hoverBackground != null) denColors.textPrimary else tint,
            modifier = Modifier.size(15.dp)
        )
    }
}
