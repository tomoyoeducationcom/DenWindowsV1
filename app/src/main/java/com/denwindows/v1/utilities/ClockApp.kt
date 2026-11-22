package com.denwindows.v1.utilities

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.theme.MonoFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ClockApp() {
    val context = LocalContext.current
    val denColors = LocalDenColors.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(denColors.windowSurface)) {
        TabRow(selectedTabIndex = selectedTab, containerColor = denColors.secondarySurface) {
            listOf("Clock", "Stopwatch", "Timer", "Alarms").forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        when (selectedTab) {
            0 -> WorldClockPane()
            1 -> StopwatchPane()
            2 -> CountdownTimerPane()
            3 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("System Alarms", style = MaterialTheme.typography.headlineMedium, color = denColors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Alarms are managed reliably by your device's Android Clock app so they ring even if DenWindowsV1 is closed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = denColors.textSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = {
                        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    }) {
                        Text("Open Android Alarms")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldClockPane() {
    val denColors = LocalDenColors.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val timeStr = remember(nowMs) { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nowMs)) }
    val dateStr = remember(nowMs) { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(nowMs)) }
    val tz = TimeZone.getDefault().displayName

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timeStr,
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = MonoFontFamily, fontSize = 42.sp),
            color = denColors.accent
        )
        Spacer(Modifier.height(6.dp))
        Text(dateStr, style = MaterialTheme.typography.titleMedium, color = denColors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(tz, style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
    }
}

@Composable
private fun StopwatchPane() {
    val denColors = LocalDenColors.current
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    val laps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(running) {
        var last = System.currentTimeMillis()
        while (running) {
            delay(50L)
            val now = System.currentTimeMillis()
            elapsedMs += (now - last)
            last = now
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatStopwatch(elapsedMs),
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = MonoFontFamily, fontSize = 36.sp),
            color = denColors.textPrimary
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { running = !running }) {
                Text(if (running) "Pause" else "Start")
            }
            OutlinedButton(
                onClick = { if (running) laps.add(0, elapsedMs) },
                enabled = running
            ) {
                Text("Lap")
            }
            OutlinedButton(onClick = {
                running = false
                elapsedMs = 0L
                laps.clear()
            }) {
                Text("Reset")
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(laps) { index, lapMs ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(denColors.secondarySurface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Lap ${laps.size - index}", style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
                    Text(formatStopwatch(lapMs), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily), color = denColors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun CountdownTimerPane() {
    val denColors = LocalDenColors.current
    var totalSeconds by remember { mutableIntStateOf(300) }
    var remainingSeconds by remember { mutableIntStateOf(300) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
            if (remainingSeconds == 0) running = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60
        Text(
            text = String.format(Locale.US, "%02d:%02d", mins, secs),
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = MonoFontFamily, fontSize = 38.sp),
            color = if (remainingSeconds == 0) denColors.success else denColors.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f },
            modifier = Modifier.width(240.dp).height(6.dp),
            color = denColors.accent
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60 to "1m", 300 to "5m", 600 to "10m", 1500 to "25m").forEach { (sec, label) ->
                OutlinedButton(
                    onClick = {
                        running = false
                        totalSeconds = sec
                        remainingSeconds = sec
                    }
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { if (remainingSeconds > 0) running = !running },
                enabled = remainingSeconds > 0
            ) {
                Text(if (running) "Pause" else "Start")
            }
            OutlinedButton(onClick = {
                running = false
                remainingSeconds = totalSeconds
            }) { Text("Reset") }
        }
    }
}

private fun formatStopwatch(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val hundredths = (ms % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", min, sec, hundredths)
}
