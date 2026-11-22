package com.denwindows.v1.taskbar

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denwindows.v1.system.BatteryTelemetry
import com.denwindows.v1.system.ConnectedInputInfo
import com.denwindows.v1.system.NetworkTelemetry
import com.denwindows.v1.theme.LocalDenColors

@Composable
fun QuickSettingsPanel(
    isDarkTheme: Boolean,
    battery: BatteryTelemetry,
    network: NetworkTelemetry,
    bluetoothEnabled: Boolean,
    inputInfo: ConnectedInputInfo,
    onToggleTheme: () -> Unit,
    onOpenAndroidSettings: (String) -> Unit,
    onOpenDesktopSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val denColors = LocalDenColors.current

    Surface(
        modifier = modifier
            .width(340.dp)
            .testTag("quick_settings_panel"),
        shape = RoundedCornerShape(16.dp),
        color = denColors.glassSurface,
        border = BorderStroke(1.dp, denColors.border),
        shadowElevation = 18.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "System Status & Quick Settings",
                style = MaterialTheme.typography.titleSmall,
                color = denColors.textPrimary
            )

            // Quick Tiles Grid (2 columns)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile(
                    icon = Icons.Filled.Wifi,
                    title = network.label,
                    subtitle = "Android Wi-Fi Settings",
                    active = network.isConnected,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenAndroidSettings(Settings.ACTION_WIFI_SETTINGS) }
                )
                QuickTile(
                    icon = Icons.Filled.Bluetooth,
                    title = if (bluetoothEnabled) "Bluetooth On" else "Bluetooth Off",
                    subtitle = "Pair mouse / keyboard",
                    active = bluetoothEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenAndroidSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile(
                    icon = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                    title = if (isDarkTheme) "Dark Theme" else "Light Theme",
                    subtitle = "Tap to switch",
                    active = isDarkTheme,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleTheme
                )
                QuickTile(
                    icon = Icons.Filled.DisplaySettings,
                    title = "Display",
                    subtitle = "System display",
                    active = false,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenAndroidSettings(Settings.ACTION_DISPLAY_SETTINGS) }
                )
            }

            // Battery Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(denColors.secondarySurface.copy(alpha = 0.7f))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (battery.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                            null,
                            tint = denColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Battery ${battery.levelPercent}% (${battery.statusLabel})",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = denColors.textPrimary
                        )
                    }
                    if (battery.temperatureCelsius > 0f) {
                        Text(
                            "${battery.temperatureCelsius}°C",
                            style = MaterialTheme.typography.bodySmall,
                            color = denColors.textSecondary
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { battery.levelPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = denColors.accent,
                    trackColor = denColors.border
                )
            }

            // Connected Peripherals Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(denColors.secondarySurface.copy(alpha = 0.7f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Mouse, null, tint = denColors.textSecondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (inputInfo.hasMouseOrTrackpad) {
                            "Pointer: ${inputInfo.mouseDeviceNames.joinToString()}"
                        } else {
                            "Pointer: Touchscreen active (no external mouse)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = denColors.textPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Keyboard, null, tint = denColors.textSecondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (inputInfo.hasHardwareKeyboard) {
                            "Keyboard: ${inputInfo.keyboardDeviceNames.joinToString()}"
                        } else {
                            "Keyboard: On-screen IME"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = denColors.textPrimary
                    )
                }
            }

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenDiagnostics)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = denColors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Multi-Window Info", style = MaterialTheme.typography.labelMedium, color = denColors.textPrimary)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenDesktopSettings)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Settings, null, tint = denColors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Settings", style = MaterialTheme.typography.labelMedium, color = denColors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val denColors = LocalDenColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) denColors.accentContainer else denColors.secondarySurface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (active) denColors.accent else denColors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = denColors.textPrimary,
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = denColors.textSecondary,
            maxLines = 1
        )
    }
}
