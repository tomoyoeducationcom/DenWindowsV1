package com.denwindows.v1.startmenu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denwindows.v1.applications.InstalledAppInfo
import com.denwindows.v1.data.RecentAppEntity
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.windows.DesktopAppType
import com.denwindows.v1.windows.iconVector
import java.io.File

@Composable
fun StartMenu(
    installedApps: List<InstalledAppInfo>,
    recentApps: List<RecentAppEntity>,
    searchableFiles: List<File>,
    deviceName: String,
    initialSearchFocus: Boolean,
    onDismiss: () -> Unit,
    onLaunchInternalApp: (DesktopAppType, String?) -> Unit,
    onLaunchAndroidApp: (InstalledAppInfo) -> Unit,
    onPinAndroidAppToTaskbar: (InstalledAppInfo) -> Unit,
    onAddAndroidAppToDesktop: (InstalledAppInfo) -> Unit,
    onOpenFile: (File) -> Unit,
    onMinimizeAll: () -> Unit,
    onCloseAllWindows: () -> Unit,
    onOpenHomeChooser: () -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val denColors = LocalDenColors.current
    var searchQuery by remember { mutableStateOf("") }
    var showAllApps by remember { mutableStateOf(initialSearchFocus) }
    var showPowerMenu by remember { mutableStateOf(false) }

    val query = searchQuery.trim().lowercase()

    val matchingInternalApps = remember(query) {
        DesktopAppType.entries.filter {
            query.isEmpty() || it.defaultTitle.lowercase().contains(query) || it.name.lowercase().contains(query)
        }
    }
    val matchingAndroidApps = remember(installedApps, query) {
        if (query.isEmpty()) installedApps
        else installedApps.filter {
            it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
    }
    val matchingFiles = remember(searchableFiles, query) {
        if (query.isEmpty()) emptyList()
        else searchableFiles.filter { it.name.lowercase().contains(query) }.take(8)
    }

    Surface(
        modifier = modifier
            .width(560.dp)
            .height(430.dp)
            .testTag("start_menu_panel"),
        shape = RoundedCornerShape(16.dp),
        color = denColors.glassSurface,
        border = BorderStroke(1.dp, denColors.border),
        shadowElevation = 20.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Universal Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search installed apps, utilities, files, or settings...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = denColors.textSecondary
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = denColors.accent)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = denColors.textSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = denColors.accent,
                    unfocusedBorderColor = denColors.border,
                    focusedContainerColor = denColors.secondarySurface,
                    unfocusedContainerColor = denColors.secondarySurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_menu_search_input")
            )

            Spacer(Modifier.height(12.dp))

            // Main Content Area: Search Results, All Apps, or Pinned + Recent
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    query.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (matchingInternalApps.isNotEmpty()) {
                                item { SectionHeader("Desktop Utilities & Settings") }
                                items(matchingInternalApps) { utility ->
                                    SearchResultRow(
                                        title = utility.defaultTitle,
                                        subtitle = "DenWindows Built-in Tool",
                                        onClick = {
                                            onDismiss()
                                            onLaunchInternalApp(utility, null)
                                        }
                                    ) {
                                        Icon(utility.iconVector(), null, tint = denColors.accent, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            if (matchingAndroidApps.isNotEmpty()) {
                                item { SectionHeader("Installed Android Apps (${matchingAndroidApps.size})") }
                                items(matchingAndroidApps, key = { it.packageName }) { app ->
                                    AndroidAppRow(
                                        app = app,
                                        onLaunch = {
                                            onDismiss()
                                            onLaunchAndroidApp(app)
                                        },
                                        onPinTaskbar = { onPinAndroidAppToTaskbar(app) },
                                        onAddDesktop = { onAddAndroidAppToDesktop(app) }
                                    )
                                }
                            }

                            if (matchingFiles.isNotEmpty()) {
                                item { SectionHeader("Desktop & User Files") }
                                items(matchingFiles, key = { it.absolutePath }) { file ->
                                    SearchResultRow(
                                        title = file.name,
                                        subtitle = file.parentFile?.name ?: "File",
                                        onClick = {
                                            onDismiss()
                                            onOpenFile(file)
                                        }
                                    ) {
                                        Icon(
                                            if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                                            null,
                                            tint = Color(0xFFF3C15A),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            if (matchingInternalApps.isEmpty() && matchingAndroidApps.isEmpty() && matchingFiles.isEmpty()) {
                                item {
                                    Text(
                                        "No matching apps, tools, or files found for \"$searchQuery\".",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = denColors.textSecondary,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    showAllApps -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "All Installed Apps (${installedApps.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = denColors.textPrimary
                                )
                                TextButton(onClick = { showAllApps = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Back")
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(installedApps, key = { it.packageName }) { app ->
                                    AndroidAppRow(
                                        app = app,
                                        onLaunch = {
                                            onDismiss()
                                            onLaunchAndroidApp(app)
                                        },
                                        onPinTaskbar = { onPinAndroidAppToTaskbar(app) },
                                        onAddDesktop = { onAddAndroidAppToDesktop(app) }
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Pinned Desktop Apps",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = denColors.textPrimary
                                )
                                TextButton(
                                    onClick = { showAllApps = true },
                                    modifier = Modifier.testTag("start_menu_all_apps_button")
                                ) {
                                    Text("All apps (${installedApps.size})")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(15.dp))
                                }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(DesktopAppType.entries) { appType ->
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(denColors.secondarySurface.copy(alpha = 0.45f))
                                            .clickable {
                                                onDismiss()
                                                onLaunchInternalApp(appType, null)
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                            .testTag("start_pinned_${appType.name}"),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = appType.iconVector(),
                                            contentDescription = appType.defaultTitle,
                                            tint = denColors.accent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = appType.defaultTitle,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                            color = denColors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            if (recentApps.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Recent",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = denColors.textPrimary
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    recentApps.take(4).forEach { recent ->
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(denColors.secondarySurface.copy(alpha = 0.6f))
                                                .clickable {
                                                    onDismiss()
                                                    if (recent.kind == "INTERNAL") {
                                                        runCatching { DesktopAppType.valueOf(recent.target) }
                                                            .getOrNull()
                                                            ?.let { onLaunchInternalApp(it, null) }
                                                    } else {
                                                        installedApps.firstOrNull { it.packageName == recent.target }
                                                            ?.let(onLaunchAndroidApp)
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.Apps,
                                                null,
                                                tint = denColors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                recent.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = denColors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(denColors.border.copy(alpha = 0.7f))
            )
            Spacer(Modifier.height(8.dp))

            // Footer Bar: User Device Profile + Settings + Power Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(denColors.accentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            deviceName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = denColors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        deviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = denColors.textPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onDismiss()
                            onLaunchInternalApp(DesktopAppType.APP_COMPATIBILITY, null)
                        }
                    ) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = "Window Diagnostics", tint = denColors.textSecondary)
                    }
                    IconButton(
                        onClick = {
                            onDismiss()
                            onLaunchInternalApp(DesktopAppType.SETTINGS, null)
                        }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = denColors.textSecondary)
                    }
                    Box {
                        IconButton(
                            onClick = { showPowerMenu = true },
                            modifier = Modifier.testTag("start_menu_power_button")
                        ) {
                            Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Power & Session", tint = denColors.textPrimary)
                        }
                        DropdownMenu(
                            expanded = showPowerMenu,
                            onDismissRequest = { showPowerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Show Desktop (Minimize All)") },
                                onClick = {
                                    showPowerMenu = false
                                    onDismiss()
                                    onMinimizeAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close All Windows") },
                                onClick = {
                                    showPowerMenu = false
                                    onDismiss()
                                    onCloseAllWindows()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Default Home Launcher Settings") },
                                leadingIcon = { Icon(Icons.Filled.Home, null) },
                                onClick = {
                                    showPowerMenu = false
                                    onDismiss()
                                    onOpenHomeChooser()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Exit DenWindowsV1") },
                                onClick = {
                                    showPowerMenu = false
                                    onExitApp()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val denColors = LocalDenColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = denColors.accent,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val denColors = LocalDenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = denColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AndroidAppRow(
    app: InstalledAppInfo,
    onLaunch: () -> Unit,
    onPinTaskbar: () -> Unit,
    onAddDesktop: () -> Unit
) {
    val denColors = LocalDenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onLaunch)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
            )
        } else {
            Icon(Icons.Filled.Apps, contentDescription = app.label, tint = denColors.accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = denColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = denColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onPinTaskbar, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.PushPin, contentDescription = "Pin to Taskbar", tint = denColors.textSecondary, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onAddDesktop, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.AddToHomeScreen, contentDescription = "Add to Desktop", tint = denColors.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}
