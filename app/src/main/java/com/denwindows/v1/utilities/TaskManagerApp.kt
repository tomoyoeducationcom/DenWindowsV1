package com.denwindows.v1.utilities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denwindows.v1.explorer.formatBytes
import com.denwindows.v1.system.BatteryTelemetry
import com.denwindows.v1.system.MemoryInfoSnapshot
import com.denwindows.v1.system.StorageInfo
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.theme.MonoFontFamily
import com.denwindows.v1.windows.DesktopWindow
import com.denwindows.v1.windows.iconVector

@Composable
fun TaskManagerApp(
    openWindows: List<DesktopWindow>,
    battery: BatteryTelemetry,
    getMemorySnapshot: () -> MemoryInfoSnapshot,
    getStorageInfo: () -> StorageInfo,
    onFocusWindow: (String) -> Unit,
    onCloseWindow: (String) -> Unit
) {
    val denColors = LocalDenColors.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val memory = remember(refreshTick) { getMemorySnapshot() }
    val storage = remember(refreshTick) { getStorageInfo() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(denColors.windowSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Task Manager", style = MaterialTheme.typography.titleMedium, color = denColors.textPrimary)
                Text(
                    "Monitors DenWindowsV1 internal windows, JVM heap, and permitted Android system telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = denColors.textSecondary
                )
            }
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh metrics", tint = denColors.accent)
            }
        }

        // System Telemetry Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val usedRam = (memory.totalRamMb - memory.availableRamMb).coerceAtLeast(0L)
            val ramRatio = if (memory.totalRamMb > 0) usedRam.toFloat() / memory.totalRamMb else 0f
            MetricCard(
                title = "System RAM",
                value = "${usedRam} / ${memory.totalRamMb} MB",
                sub = "App Heap: ${memory.appHeapUsedMb}/${memory.appHeapMaxMb} MB",
                progress = ramRatio,
                modifier = Modifier.weight(1f)
            )
            val storageRatio = if (storage.totalBytes > 0) storage.usedBytes.toFloat() / storage.totalBytes else 0f
            MetricCard(
                title = "Storage",
                value = "${formatBytes(storage.freeBytes)} free",
                sub = "Total: ${formatBytes(storage.totalBytes)}",
                progress = storageRatio,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Battery",
                value = "${battery.levelPercent}%",
                sub = "${battery.statusLabel} • ${battery.temperatureCelsius}°C",
                progress = battery.levelPercent / 100f,
                modifier = Modifier.weight(1f)
            )
        }

        // Active Internal Desktop Windows
        Text(
            "Open Desktop Windows (${openWindows.size})",
            style = MaterialTheme.typography.titleSmall,
            color = denColors.textPrimary
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (openWindows.isEmpty()) {
                item {
                    Text("No internal windows currently open.", style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
                }
            } else {
                items(openWindows, key = { it.id }) { win ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(denColors.secondarySurface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(win.appType.iconVector(), null, tint = denColors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(win.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = denColors.textPrimary)
                            Text(
                                "State: ${win.stateMode.name} • ${win.widthDp.toInt()}×${win.heightDp.toInt()} dp",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                color = denColors.textSecondary
                            )
                        }
                        IconButton(onClick = { onFocusWindow(win.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.OpenInFull, contentDescription = "Focus window", tint = denColors.accent, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onCloseWindow(win.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "End task", tint = denColors.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Text(
            "Android Security Note: Non-root apps cannot inspect or force-stop third-party Android app processes.",
            style = MaterialTheme.typography.labelSmall,
            color = denColors.textSecondary
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    sub: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val denColors = LocalDenColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(denColors.secondarySurface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily), color = denColors.textPrimary)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = denColors.accent
        )
        Text(sub, style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary)
    }
}
