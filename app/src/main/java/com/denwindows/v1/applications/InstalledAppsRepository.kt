package com.denwindows.v1.applications

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isBrowser: Boolean
)

data class AppLaunchResult(
    val launched: Boolean,
    val usedFreeformBounds: Boolean,
    val message: String
)

class InstalledAppsRepository(private val context: Context) {

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _installedBrowsers = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedBrowsers: StateFlow<List<InstalledAppInfo>> = _installedBrowsers.asStateFlow()

    private val iconCache = mutableMapOf<String, ImageBitmap>()

    suspend fun refreshInstalledApps() = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val browserPackages = queryActivitiesCompat(pm, browserIntent)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != context.packageName }
            .toSet()

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = queryActivitiesCompat(pm, launcherIntent)

        val apps = resolveInfos
            .asSequence()
            .filter { it.activityInfo != null && it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val icon = iconCache[pkg] ?: runCatching {
                    drawableToImageBitmap(info.loadIcon(pm))
                }.getOrNull()?.also { iconCache[pkg] = it }

                InstalledAppInfo(
                    packageName = pkg,
                    activityName = info.activityInfo.name,
                    label = label,
                    icon = icon,
                    isBrowser = browserPackages.contains(pkg)
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()

        _installedApps.value = apps
        _installedBrowsers.value = apps.filter { it.isBrowser }
    }

    fun getIconForPackage(packageName: String): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        return _installedApps.value.firstOrNull { it.packageName == packageName }?.icon
    }

    fun launchApp(
        packageName: String,
        tryFreeform: Boolean = false
    ): AppLaunchResult {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return AppLaunchResult(
                launched = false,
                usedFreeformBounds = false,
                message = "No launchable activity found for $packageName."
            )

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val supportsFreeform = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

        if (tryFreeform && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val metrics = context.resources.displayMetrics
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val bounds = Rect(
                (w * 0.12f).toInt(),
                (h * 0.08f).toInt(),
                (w * 0.88f).toInt(),
                (h * 0.86f).toInt()
            )
            val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
            return runCatching {
                context.startActivity(launchIntent, options.toBundle())
                AppLaunchResult(
                    launched = true,
                    usedFreeformBounds = supportsFreeform,
                    message = if (supportsFreeform) {
                        "Launched $packageName with freeform launch bounds."
                    } else {
                        "Launched $packageName natively (device does not advertise freeform windowing)."
                    }
                )
            }.getOrElse {
                runCatching {
                    context.startActivity(launchIntent)
                    AppLaunchResult(true, false, "Launched $packageName natively.")
                }.getOrElse { err ->
                    AppLaunchResult(false, false, err.localizedMessage ?: "Failed to launch $packageName")
                }
            }
        }

        return runCatching {
            context.startActivity(launchIntent)
            AppLaunchResult(true, false, "Launched $packageName.")
        }.getOrElse { err ->
            AppLaunchResult(false, false, err.localizedMessage ?: "Failed to launch $packageName.")
        }
    }

    fun openUrlInExternalBrowser(url: String, preferredPackage: String? = null): Boolean {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!preferredPackage.isNullOrBlank()) {
                setPackage(preferredPackage)
            }
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            if (!preferredPackage.isNullOrBlank()) {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(fallback); true }.getOrDefault(false)
            } else {
                false
            }
        }
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun queryActivitiesCompat(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
