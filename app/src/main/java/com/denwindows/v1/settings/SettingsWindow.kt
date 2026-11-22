package com.denwindows.v1.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denwindows.v1.applications.InstalledAppInfo
import com.denwindows.v1.data.BuiltInWallpaper
import com.denwindows.v1.data.DesktopIconSize
import com.denwindows.v1.data.SearchEngineOption
import com.denwindows.v1.data.UserPreferences
import com.denwindows.v1.system.CompatibilityDiagnostics
import com.denwindows.v1.system.ConnectedInputInfo
import com.denwindows.v1.theme.AccentOption
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.theme.MonoFontFamily

@Composable
fun SettingsWindow(
    initialSection: String?,
    preferences: UserPreferences,
    installedBrowsers: List<InstalledAppInfo>,
    inputInfo: ConnectedInputInfo,
    diagnostics: CompatibilityDiagnostics,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAccent: (String) -> Unit,
    onSetBuiltInWallpaper: (String) -> Unit,
    onSetCustomWallpaperUri: (String?) -> Unit,
    onSetIconSize: (DesktopIconSize) -> Unit,
    onSetTaskbarAutoHide: (Boolean) -> Unit,
    onSetWindowAnimations: (Boolean) -> Unit,
    onSetDefaultBrowser: (String?) -> Unit,
    onSetSearchEngine: (String) -> Unit,
    onSetTryFreeform: (Boolean) -> Unit,
    onSetMouseHover: (Boolean) -> Unit,
    onOpenHomeSettings: () -> Unit,
    onOpenDiagnosticsWindow: () -> Unit
) {
    val context = LocalContext.current
    val denColors = LocalDenColors.current
    var activeSection by remember(initialSection) {
        mutableStateOf(initialSection ?: "personalization")
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onSetCustomWallpaperUri(uri.toString())
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(denColors.windowSurface)) {
        // Left Sidebar
        Column(
            modifier = Modifier
                .width(175.dp)
                .fillMaxHeight()
                .background(denColors.secondarySurface.copy(alpha = 0.5f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsNavTab("personalization", "Personalization", Icons.Filled.Palette, activeSection) { activeSection = it }
            SettingsNavTab("desktop", "Desktop & Taskbar", Icons.Filled.DesktopWindows, activeSection) { activeSection = it }
            SettingsNavTab("browser", "Browser & Apps", Icons.Filled.Language, activeSection) { activeSection = it }
            SettingsNavTab("input", "Mouse & Keyboard", Icons.Filled.Keyboard, activeSection) { activeSection = it }
            SettingsNavTab("system", "System & Launcher", Icons.Filled.Info, activeSection) { activeSection = it }
        }

        // Right Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (activeSection) {
                "personalization" -> {
                    Text("Personalization", style = MaterialTheme.typography.headlineMedium, color = denColors.textPrimary)

                    SettingToggleRow(
                        title = "Dark Desktop Theme",
                        subtitle = "Windows 11++ Midnight Slate appearance (recommended)",
                        checked = preferences.isDarkTheme,
                        onCheckedChange = onSetDarkTheme
                    )

                    Text("Accent Color", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentOption.entries.forEach { opt ->
                            val selected = preferences.accentId == opt.id
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(opt.color)
                                    .then(
                                        if (selected) Modifier.border(2.5.dp, denColors.textPrimary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { onSetAccent(opt.id) }
                            )
                        }
                    }

                    Text("Desktop Wallpaper", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BuiltInWallpaper.entries.forEach { wp ->
                            val selected = preferences.customWallpaperUri == null && preferences.wallpaperId == wp.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) denColors.accentContainer else denColors.secondarySurface)
                                    .clickable { onSetBuiltInWallpaper(wp.id) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = { onSetBuiltInWallpaper(wp.id) })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(wp.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = denColors.textPrimary)
                                    Text(wp.subtitle, style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Choose Custom Image")
                        }
                        if (preferences.customWallpaperUri != null) {
                            OutlinedButton(onClick = { onSetCustomWallpaperUri(null) }) {
                                Text("Reset to Built-in")
                            }
                        }
                    }
                }

                "desktop" -> {
                    Text("Desktop & Taskbar", style = MaterialTheme.typography.headlineMedium, color = denColors.textPrimary)

                    Text("Desktop Icon Size", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DesktopIconSize.entries.forEach { size ->
                            val selected = preferences.iconSize == size
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) denColors.accent else denColors.secondarySurface)
                                    .clickable { onSetIconSize(size) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    size.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.White else denColors.textPrimary
                                )
                            }
                        }
                    }

                    SettingToggleRow(
                        title = "Auto-Hide Taskbar",
                        subtitle = "Collapse taskbar when internal windows are active; tap bottom strip to reveal",
                        checked = preferences.taskbarAutoHide,
                        onCheckedChange = onSetTaskbarAutoHide
                    )

                    SettingToggleRow(
                        title = "Window Snap & State Animations",
                        subtitle = "Smooth transitions when maximizing, restoring, or snapping windows",
                        checked = preferences.windowAnimations,
                        onCheckedChange = onSetWindowAnimations
                    )
                }

                "browser" -> {
                    Text("Browser & External Apps", style = MaterialTheme.typography.headlineMedium, color = denColors.textPrimary)

                    Text("Internal Browser Search Engine", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SearchEngineOption.entries.forEach { engine ->
                            val selected = preferences.searchEngineId == engine.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(denColors.secondarySurface)
                                    .clickable { onSetSearchEngine(engine.id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = { onSetSearchEngine(engine.id) })
                                Text(engine.label, style = MaterialTheme.typography.bodyMedium, color = denColors.textPrimary)
                            }
                        }
                    }

                    Text("Preferred External Browser", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(denColors.secondarySurface)
                                .clickable { onSetDefaultBrowser(null) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = preferences.defaultExternalBrowserPkg == null, onClick = { onSetDefaultBrowser(null) })
                            Text("System Default / Chooser", style = MaterialTheme.typography.bodyMedium, color = denColors.textPrimary)
                        }
                        installedBrowsers.forEach { browser ->
                            val selected = preferences.defaultExternalBrowserPkg == browser.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(denColors.secondarySurface)
                                    .clickable { onSetDefaultBrowser(browser.packageName) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = { onSetDefaultBrowser(browser.packageName) })
                                Column {
                                    Text(browser.label, style = MaterialTheme.typography.bodyMedium, color = denColors.textPrimary)
                                    Text(browser.packageName, style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary)
                                }
                            }
                        }
                    }

                    SettingToggleRow(
                        title = "Request Freeform Window Bounds on App Launch",
                        subtitle = if (diagnostics.supportsFreeformFeature) {
                            "Device supports freeform windowing; external apps will request desktop bounds."
                        } else {
                            "Device firmware does not advertise freeform windowing; falls back to native launch."
                        },
                        checked = preferences.tryFreeformOnAppLaunch,
                        onCheckedChange = onSetTryFreeform
                    )

                    OutlinedButton(onClick = onOpenDiagnosticsWindow) {
                        Text("Open Full Compatibility Diagnostics")
                    }
                }

                "input" -> {
                    Text("Mouse & Keyboard", style = MaterialTheme.typography.headlineMedium, color = denColors.textPrimary)

                    SettingToggleRow(
                        title = "Pointer Hover Highlights",
                        subtitle = "Highlight interactive desktop surfaces for Bluetooth mouse / trackpad",
                        checked = preferences.mouseHoverHighlight,
                        onCheckedChange = onSetMouseHover
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(denColors.secondarySurface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Connected Hardware", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                        Text(
                            "Mouse/Trackpad: ${if (inputInfo.hasMouseOrTrackpad) inputInfo.mouseDeviceNames.joinToString() else "None detected"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = denColors.textSecondary
                        )
                        Text(
                            "Hardware Keyboard: ${if (inputInfo.hasHardwareKeyboard) inputInfo.keyboardDeviceNames.joinToString() else "None detected"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = denColors.textSecondary
                        )
                    }

                    Text("Keyboard Shortcuts", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(denColors.secondarySurface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Win / Super" to "Toggle Start Menu",
                            "Alt + Tab" to "Switch focused internal window",
                            "Alt + F4" to "Close focused internal window",
                            "Win + E" to "Open File Explorer",
                            "Win + R" to "Open Run Dialog",
                            "Win + D" to "Show Desktop (Minimize / Restore all)"
                        ).forEach { (keys, desc) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(keys, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = denColors.accent)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = denColors.textPrimary)
                            }
                        }
                    }
                }

                "system" -> {
                    Text("System & Default Launcher", style = MaterialTheme.typography.headlineMedium, color = denColors.textPrimary)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(denColors.secondarySurface)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("DenWindowsV1 Desktop Environment", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                        Text("Android ${diagnostics.androidRelease} (API ${diagnostics.apiLevel}) • ${diagnostics.deviceModel}", style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
                        Text(
                            if (diagnostics.isDefaultHomeLauncher) "Status: Currently active as Android Default Home Launcher."
                            else "Status: Running as standard application. You can optionally set it as your Android Home launcher.",
                            style = MaterialTheme.typography.bodySmall,
                            color = denColors.accent
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = onOpenHomeSettings) {
                            Text("Choose Default Home App in Android Settings")
                        }
                    }

                    Text("Required Permissions & Privacy", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                    Text(
                        "• INTERNET & ACCESS_NETWORK_STATE: Used exclusively by DenBrowser and the System Tray Wi-Fi indicator.\n" +
                            "• Package Queries (<queries>): Used via Android PackageManager to list installed apps and web browsers locally without QUERY_ALL_PACKAGES.\n" +
                            "• Storage Access Framework & Photo Picker: Zero broad storage permissions requested; user explicitly grants folder or wallpaper access.\n" +
                            "• Offline-first: Zero analytics, zero telemetry uploads, zero cloud backend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = denColors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNavTab(
    id: String,
    label: String,
    icon: ImageVector,
    activeId: String,
    onSelect: (String) -> Unit
) {
    val denColors = LocalDenColors.current
    val active = id == activeId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) denColors.accentContainer else Color.Transparent)
            .clickable { onSelect(id) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) denColors.accent else denColors.textSecondary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
            color = if (active) denColors.textPrimary else denColors.textSecondary
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val denColors = LocalDenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(denColors.secondarySurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = denColors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
