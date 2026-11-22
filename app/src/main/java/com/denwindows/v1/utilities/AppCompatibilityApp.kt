package com.denwindows.v1.utilities

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denwindows.v1.applications.InstalledAppInfo
import com.denwindows.v1.system.CompatibilityDiagnostics
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.theme.MonoFontFamily

@Composable
fun AppCompatibilityApp(
    diagnostics: CompatibilityDiagnostics,
    installedAppsCount: Int,
    installedBrowsers: List<InstalledAppInfo>,
    onOpenHomeSettings: () -> Unit,
    onOpenDeveloperSettings: () -> Unit
) {
    val denColors = LocalDenColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(denColors.windowSurface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("External App & Multi-Window Compatibility", style = MaterialTheme.typography.titleMedium, color = denColors.textPrimary)
        Text(
            "Android executes third-party apps (Chrome, Edge, YouTube, Play Store) as native OS activities. Freeform bounds depend on Android OEM/system support.",
            style = MaterialTheme.typography.bodySmall,
            color = denColors.textSecondary
        )

        DiagnosticRow("Android Version", "${diagnostics.androidRelease} (API ${diagnostics.apiLevel})")
        DiagnosticRow("Device Model", "${diagnostics.deviceManufacturer} ${diagnostics.deviceModel}")
        DiagnosticRow("CPU ABIs", diagnostics.supportedAbis)
        DiagnosticRow(
            "Freeform Window Feature",
            if (diagnostics.supportsFreeformFeature) "Supported (FEATURE_FREEFORM_WINDOW_MANAGEMENT)" else "Not advertised by device firmware"
        )
        DiagnosticRow(
            "Currently In Multi-Window",
            if (diagnostics.isCurrentlyInMultiWindow) "Yes" else "No (Full desktop screen)"
        )
        DiagnosticRow(
            "Picture-in-Picture (PiP)",
            if (diagnostics.supportsPictureInPicture) "Supported by OS" else "Unsupported"
        )
        DiagnosticRow(
            "Default Home Launcher",
            if (diagnostics.isDefaultHomeLauncher) "DenWindowsV1 is active Home" else "Another launcher is default"
        )
        DiagnosticRow("Discovered Launcher Apps", "$installedAppsCount native Android apps")
        DiagnosticRow(
            "Discovered External Browsers",
            installedBrowsers.joinToString { it.label }.ifEmpty { "None detected" }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(denColors.accentContainer.copy(alpha = 0.65f))
                .padding(12.dp)
        ) {
            Text("Recommended Configuration", style = MaterialTheme.typography.titleSmall, color = denColors.accent)
            Spacer(Modifier.height(4.dp))
            Text(diagnostics.recommendation, style = MaterialTheme.typography.bodySmall, color = denColors.textPrimary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpenHomeSettings) {
                Text("Default Home Settings")
            }
            OutlinedButton(onClick = onOpenDeveloperSettings) {
                Text("System Settings")
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    val denColors = LocalDenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(denColors.secondarySurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = denColors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = denColors.textPrimary)
    }
}
