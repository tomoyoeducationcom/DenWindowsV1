package com.denwindows.v1.taskbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denwindows.v1.data.DesktopItemEntity
import com.denwindows.v1.data.DesktopItemKind
import com.denwindows.v1.system.BatteryTelemetry
import com.denwindows.v1.system.ConnectedInputInfo
import com.denwindows.v1.system.NetworkTelemetry
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.windows.DesktopAppType
import com.denwindows.v1.windows.DesktopWindow
import com.denwindows.v1.windows.WindowStateMode
import com.denwindows.v1.windows.iconVector

@Composable
fun Taskbar(
    isStartMenuOpen: Boolean,
    isSearchOpen: Boolean,
    isQuickSettingsOpen: Boolean,
    pinnedItems: List<DesktopItemEntity>,
    openWindows: List<DesktopWindow>,
    focusedWindowId: String?,
    clockText: String,
    dateText: String,
    battery: BatteryTelemetry,
    network: NetworkTelemetry,
    bluetoothEnabled: Boolean,
    inputInfo: ConnectedInputInfo,
    getAndroidAppIcon: (String) -> ImageBitmap?,
    onToggleStartMenu: () -> Unit,
    onToggleSearch: () -> Unit,
    onClickPinnedItem: (DesktopItemEntity) -> Unit,
    onClickWindowItem: (DesktopWindow) -> Unit,
    onToggleQuickSettings: () -> Unit,
    onShowDesktop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val denColors = LocalDenColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = denColors.taskbarSurface,
        shadowElevation = 10.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(denColors.border.copy(alpha = 0.7f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Balance Spacer / Hardware Input Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    if (inputInfo.hasMouseOrTrackpad) {
                        Icon(
                            imageVector = Icons.Filled.Mouse,
                            contentDescription = "Mouse connected",
                            tint = denColors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (inputInfo.hasHardwareKeyboard) {
                        Icon(
                            imageVector = Icons.Filled.Keyboard,
                            contentDescription = "Hardware keyboard connected",
                            tint = denColors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Center-Left Core Launcher & Open Windows Strip
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Start Button with Original DenWindows 4-Pane Prism Mark
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isStartMenuOpen) denColors.accentContainer
                                else Color.Transparent
                            )
                            .clickable(onClick = onToggleStartMenu)
                            .testTag("taskbar_start_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        DenPrismLogo(
                            primary = denColors.accent,
                            secondary = denColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    // Universal Search Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isSearchOpen) denColors.accentContainer
                                else Color.Transparent
                            )
                            .clickable(onClick = onToggleSearch)
                            .testTag("taskbar_search_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = if (isSearchOpen) denColors.accent else denColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Pinned Internal & Android Apps
                    val taskbarPinned = pinnedItems.filter { it.isPinnedToTaskbar }
                    val openInternalByType = openWindows.associateBy { it.appType.name }

                    taskbarPinned.forEach { item ->
                        val matchingWindow = if (item.kind == DesktopItemKind.INTERNAL_APP.name) {
                            openInternalByType[item.target]
                        } else null
                        val isOpen = matchingWindow != null
                        val isFocused = matchingWindow != null &&
                            matchingWindow.id == focusedWindowId &&
                            matchingWindow.stateMode != WindowStateMode.MINIMIZED

                        TaskbarAppButton(
                            label = item.title,
                            isOpen = isOpen,
                            isFocused = isFocused,
                            accent = denColors.accent,
                            activeBg = denColors.secondarySurface,
                            testTag = "taskbar_pinned_${item.target}",
                            onClick = {
                                if (matchingWindow != null) onClickWindowItem(matchingWindow)
                                else onClickPinnedItem(item)
                            }
                        ) {
                            if (item.kind == DesktopItemKind.ANDROID_APP.name) {
                                val bmp = getAndroidAppIcon(item.target)
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = item.title,
                                        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                                    )
                                } else {
                                    Icon(Icons.Filled.Notifications, item.title, tint = denColors.textPrimary, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                val appType = runCatching { DesktopAppType.valueOf(item.target) }
                                    .getOrDefault(DesktopAppType.SETTINGS)
                                Icon(
                                    imageVector = appType.iconVector(),
                                    contentDescription = item.title,
                                    tint = if (isFocused) denColors.accent else denColors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    // Unpinned Open Internal Windows
                    val pinnedInternalTargets = taskbarPinned
                        .filter { it.kind == DesktopItemKind.INTERNAL_APP.name }
                        .map { it.target }
                        .toSet()

                    openWindows
                        .filter { !pinnedInternalTargets.contains(it.appType.name) }
                        .forEach { win ->
                            val isFocused = win.id == focusedWindowId && win.stateMode != WindowStateMode.MINIMIZED
                            TaskbarAppButton(
                                label = win.title,
                                isOpen = true,
                                isFocused = isFocused,
                                accent = denColors.accent,
                                activeBg = denColors.secondarySurface,
                                testTag = "taskbar_window_${win.id}",
                                onClick = { onClickWindowItem(win) }
                            ) {
                                Icon(
                                    imageVector = win.appType.iconVector(),
                                    contentDescription = win.title,
                                    tint = if (isFocused) denColors.accent else denColors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                }

                // Right System Tray (Quick Settings, Network, Bluetooth, Battery, Clock/Date, Show Desktop)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isQuickSettingsOpen) denColors.secondarySurface else Color.Transparent)
                        .clickable(onClick = onToggleQuickSettings)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("taskbar_system_tray"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = when {
                            network.isWifi -> Icons.Filled.Wifi
                            network.isConnected -> Icons.Filled.SignalCellularAlt
                            else -> Icons.Filled.WifiOff
                        },
                        contentDescription = network.label,
                        tint = if (network.isConnected) denColors.textPrimary else denColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Icon(
                        imageVector = if (bluetoothEnabled) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                        contentDescription = if (bluetoothEnabled) "Bluetooth On" else "Bluetooth Off",
                        tint = if (bluetoothEnabled) denColors.accent else denColors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                battery.isCharging -> Icons.Filled.BatteryChargingFull
                                battery.levelPercent > 80 -> Icons.Filled.BatteryFull
                                else -> Icons.Filled.BatteryStd
                            },
                            contentDescription = "Battery ${battery.levelPercent}%",
                            tint = if (battery.levelPercent <= 15 && !battery.isCharging) denColors.error else denColors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${battery.levelPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = denColors.textPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = clockText,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                            color = denColors.textPrimary
                        )
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = denColors.textSecondary
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Far-Right Show Desktop Sliver
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, denColors.border.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        .clickable(onClick = onShowDesktop)
                        .testTag("taskbar_show_desktop")
                )
            }
        }
    }
}

@Composable
private fun TaskbarAppButton(
    label: String,
    isOpen: Boolean,
    isFocused: Boolean,
    accent: Color,
    activeBg: Color,
    testTag: String,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isFocused) activeBg else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        iconContent()
        if (isOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .width(if (isFocused) 18.dp else 7.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) accent else accent.copy(alpha = 0.65f))
            )
        }
    }
}

@Composable
fun DenPrismLogo(
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val gap = size.width * 0.1f
        val cellW = (size.width - gap) / 2f
        val cellH = (size.height - gap) / 2f
        val r = CornerRadius(cellW * 0.22f, cellW * 0.22f)
        drawRoundRect(primary, Offset(0f, 0f), Size(cellW, cellH), r)
        drawRoundRect(primary.copy(alpha = 0.78f), Offset(cellW + gap, 0f), Size(cellW, cellH), r)
        drawRoundRect(primary.copy(alpha = 0.65f), Offset(0f, cellH + gap), Size(cellW, cellH), r)
        drawRoundRect(secondary.copy(alpha = 0.9f), Offset(cellW + gap, cellH + gap), Size(cellW, cellH), r)
    }
}
