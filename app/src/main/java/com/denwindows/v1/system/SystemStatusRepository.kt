package com.denwindows.v1.system

import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.InputDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatteryTelemetry(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val statusLabel: String = "Unknown",
    val temperatureCelsius: Float = 0f
)

data class NetworkTelemetry(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val isEthernet: Boolean = false,
    val label: String = "Offline"
)

data class ConnectedInputInfo(
    val hasMouseOrTrackpad: Boolean = false,
    val hasHardwareKeyboard: Boolean = false,
    val mouseDeviceNames: List<String> = emptyList(),
    val keyboardDeviceNames: List<String> = emptyList()
)

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long
)

data class MemoryInfoSnapshot(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val lowMemory: Boolean,
    val appHeapUsedMb: Long,
    val appHeapMaxMb: Long
)

data class CompatibilityDiagnostics(
    val androidRelease: String,
    val apiLevel: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val supportedAbis: String,
    val supportsFreeformFeature: Boolean,
    val isCurrentlyInMultiWindow: Boolean,
    val supportsPictureInPicture: Boolean,
    val isDefaultHomeLauncher: Boolean,
    val recommendation: String
)

class SystemStatusRepository(private val context: Context) {

    fun observeClock(): Flow<Pair<String, String>> = flow {
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            emit(timeFmt.format(now) to dateFmt.format(now))
            val msUntilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(msUntilNextMinute.coerceIn(1000L, 60_000L))
        }
    }

    fun observeBattery(): Flow<BatteryTelemetry> = callbackFlow {
        fun parseIntent(intent: Intent?): BatteryTelemetry {
            if (intent == null) return BatteryTelemetry()
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val pct = if (level >= 0) (level * 100) / scale else 100
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            }
            return BatteryTelemetry(
                levelPercent = pct.coerceIn(0, 100),
                isCharging = charging,
                statusLabel = statusText,
                temperatureCelsius = tempTenths / 10f
            )
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(parseIntent(intent))
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        trySend(parseIntent(initial))
        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    fun observeNetwork(): Flow<NetworkTelemetry> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun currentTelemetry(): NetworkTelemetry {
            val active = cm.activeNetwork ?: return NetworkTelemetry()
            val caps = cm.getNetworkCapabilities(active) ?: return NetworkTelemetry()
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val cell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val eth = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val label = when {
                !connected -> "No Internet"
                eth -> "Ethernet"
                wifi -> "Wi-Fi"
                cell -> "Cellular"
                else -> "Connected"
            }
            return NetworkTelemetry(connected, wifi, cell, eth, label)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentTelemetry())
            }
            override fun onLost(network: Network) {
                trySend(currentTelemetry())
            }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(currentTelemetry())
            }
        }
        trySend(currentTelemetry())
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }

    fun observeBluetoothEnabled(): Flow<Boolean> = callbackFlow {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        fun isEnabled(): Boolean = runCatching { bm?.adapter?.isEnabled == true }.getOrDefault(false)
        trySend(isEnabled())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(isEnabled())
            }
        }
        val filter = IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    fun observeConnectedInputDevices(): Flow<ConnectedInputInfo> = callbackFlow {
        val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager

        fun scanDevices(): ConnectedInputInfo {
            val mice = mutableListOf<String>()
            val keyboards = mutableListOf<String>()
            for (id in InputDevice.getDeviceIds()) {
                val dev = InputDevice.getDevice(id) ?: continue
                if (dev.isVirtual) continue
                val sources = dev.sources
                val isMouse = (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                    (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
                val isKeyboard = (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                    dev.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                if (isMouse) mice.add(dev.name)
                if (isKeyboard) keyboards.add(dev.name)
            }
            return ConnectedInputInfo(
                hasMouseOrTrackpad = mice.isNotEmpty(),
                hasHardwareKeyboard = keyboards.isNotEmpty(),
                mouseDeviceNames = mice.distinct(),
                keyboardDeviceNames = keyboards.distinct()
            )
        }

        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { trySend(scanDevices()) }
            override fun onInputDeviceRemoved(deviceId: Int) { trySend(scanDevices()) }
            override fun onInputDeviceChanged(deviceId: Int) { trySend(scanDevices()) }
        }
        trySend(scanDevices())
        im.registerInputDeviceListener(listener, null)
        awaitClose { im.unregisterInputDeviceListener(listener) }
    }

    fun getStorageInfo(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        return StorageInfo(totalBytes = total, freeBytes = free, usedBytes = (total - free).coerceAtLeast(0L))
    }

    fun getMemorySnapshot(): MemoryInfoSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val rt = Runtime.getRuntime()
        val heapUsed = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
        val heapMax = rt.maxMemory() / (1024L * 1024L)
        return MemoryInfoSnapshot(
            totalRamMb = mi.totalMem / (1024L * 1024L),
            availableRamMb = mi.availMem / (1024L * 1024L),
            lowMemory = mi.lowMemory,
            appHeapUsedMb = heapUsed,
            appHeapMaxMb = heapMax
        )
    }

    fun getCompatibilityDiagnostics(activity: Activity?): CompatibilityDiagnostics {
        val pm = context.packageManager
        val supportsFreeform = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        } else {
            false
        }
        val supportsPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pm.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        } else {
            false
        }
        val inMultiWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
            activity.isInMultiWindowMode
        } else {
            false
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolvedHome = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val isDefaultHome = resolvedHome?.activityInfo?.packageName == context.packageName

        val recommendation = when {
            supportsFreeform ->
                "Freeform window management is supported by this device. You can enable 'Request Freeform Bounds' in Settings for external Android apps."
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ->
                "Stock Android on this device does not advertise hardware freeform desktop windows. External apps will launch natively in full-screen or Android Split-Screen mode, and returning restores DenWindowsV1 cleanly."
            else ->
                "Multi-window APIs require Android 7.0 (API 24) or higher."
        }

        return CompatibilityDiagnostics(
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            supportsFreeformFeature = supportsFreeform,
            isCurrentlyInMultiWindow = inMultiWindow,
            supportsPictureInPicture = supportsPip,
            isDefaultHomeLauncher = isDefaultHome,
            recommendation = recommendation
        )
    }

    fun openAndroidSystemSettings(action: String) {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }
}
