package com.denwindows.v1.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.denDataStore: DataStore<Preferences> by preferencesDataStore(name = "denwindows_prefs")

enum class DesktopIconSize(val label: String, val iconDp: Int, val cellWidthDp: Int, val cellHeightDp: Int) {
    SMALL("Small", 34, 78, 76),
    MEDIUM("Medium", 42, 90, 88),
    LARGE("Large", 52, 104, 100);

    companion object {
        fun fromName(name: String): DesktopIconSize = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

enum class BuiltInWallpaper(val id: String, val title: String, val subtitle: String) {
    MIDNIGHT_BLOOM("midnight_bloom", "Midnight Bloom", "Layered Cobalt Ribbons"),
    ARCTIC_SILK("arctic_silk", "Arctic Silk", "Deep Slate & Sapphire"),
    EMERALD_AURORA("emerald_aurora", "Emerald Aurora", "Nocturne Borealis"),
    OBSIDIAN_GRID("obsidian_grid", "Obsidian Precision", "Minimal Blueprint Mesh"),
    PEARL_DAYLIGHT("pearl_daylight", "Pearl Daylight", "Soft Porcelain Waves");

    companion object {
        fun fromId(id: String): BuiltInWallpaper = entries.firstOrNull { it.id == id } ?: MIDNIGHT_BLOOM
    }
}

enum class SearchEngineOption(val id: String, val label: String, val queryUrlPrefix: String) {
    DUCKDUCKGO("ddg", "DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("google", "Google", "https://www.google.com/search?q="),
    BING("bing", "Bing", "https://www.bing.com/search?q=");

    companion object {
        fun fromId(id: String): SearchEngineOption = entries.firstOrNull { it.id == id } ?: DUCKDUCKGO
    }
}

data class UserPreferences(
    val isDarkTheme: Boolean = true,
    val accentId: String = "cobalt",
    val wallpaperId: String = BuiltInWallpaper.MIDNIGHT_BLOOM.id,
    val customWallpaperUri: String? = null,
    val iconSize: DesktopIconSize = DesktopIconSize.MEDIUM,
    val taskbarAutoHide: Boolean = false,
    val windowAnimations: Boolean = true,
    val defaultExternalBrowserPkg: String? = null,
    val searchEngineId: String = SearchEngineOption.DUCKDUCKGO.id,
    val tryFreeformOnAppLaunch: Boolean = false,
    val mouseHoverHighlight: Boolean = true,
    val safTreeUris: Set<String> = emptySet()
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val ACCENT_ID = stringPreferencesKey("accent_id")
        val WALLPAPER_ID = stringPreferencesKey("wallpaper_id")
        val CUSTOM_WALLPAPER_URI = stringPreferencesKey("custom_wallpaper_uri")
        val ICON_SIZE = stringPreferencesKey("icon_size")
        val TASKBAR_AUTO_HIDE = booleanPreferencesKey("taskbar_auto_hide")
        val WINDOW_ANIMATIONS = booleanPreferencesKey("window_animations")
        val DEFAULT_EXTERNAL_BROWSER = stringPreferencesKey("default_external_browser")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val TRY_FREEFORM = booleanPreferencesKey("try_freeform")
        val MOUSE_HOVER_HIGHLIGHT = booleanPreferencesKey("mouse_hover_highlight")
        val SAF_TREE_URIS = stringPreferencesKey("saf_tree_uris")
    }

    val preferencesFlow: Flow<UserPreferences> = context.denDataStore.data.map { prefs ->
        UserPreferences(
            isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
            accentId = prefs[Keys.ACCENT_ID] ?: "cobalt",
            wallpaperId = prefs[Keys.WALLPAPER_ID] ?: BuiltInWallpaper.MIDNIGHT_BLOOM.id,
            customWallpaperUri = prefs[Keys.CUSTOM_WALLPAPER_URI]?.takeIf { it.isNotBlank() },
            iconSize = DesktopIconSize.fromName(prefs[Keys.ICON_SIZE] ?: DesktopIconSize.MEDIUM.name),
            taskbarAutoHide = prefs[Keys.TASKBAR_AUTO_HIDE] ?: false,
            windowAnimations = prefs[Keys.WINDOW_ANIMATIONS] ?: true,
            defaultExternalBrowserPkg = prefs[Keys.DEFAULT_EXTERNAL_BROWSER]?.takeIf { it.isNotBlank() },
            searchEngineId = prefs[Keys.SEARCH_ENGINE] ?: SearchEngineOption.DUCKDUCKGO.id,
            tryFreeformOnAppLaunch = prefs[Keys.TRY_FREEFORM] ?: false,
            mouseHoverHighlight = prefs[Keys.MOUSE_HOVER_HIGHLIGHT] ?: true,
            safTreeUris = prefs[Keys.SAF_TREE_URIS]
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?.toSet() ?: emptySet()
        )
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.denDataStore.edit { it[Keys.IS_DARK_THEME] = dark }
    }

    suspend fun setAccentId(accentId: String) {
        context.denDataStore.edit { it[Keys.ACCENT_ID] = accentId }
    }

    suspend fun setBuiltInWallpaper(wallpaperId: String) {
        context.denDataStore.edit {
            it[Keys.WALLPAPER_ID] = wallpaperId
            it.remove(Keys.CUSTOM_WALLPAPER_URI)
        }
    }

    suspend fun setCustomWallpaperUri(uri: String?) {
        context.denDataStore.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.CUSTOM_WALLPAPER_URI)
            else it[Keys.CUSTOM_WALLPAPER_URI] = uri
        }
    }

    suspend fun setIconSize(size: DesktopIconSize) {
        context.denDataStore.edit { it[Keys.ICON_SIZE] = size.name }
    }

    suspend fun setTaskbarAutoHide(autoHide: Boolean) {
        context.denDataStore.edit { it[Keys.TASKBAR_AUTO_HIDE] = autoHide }
    }

    suspend fun setWindowAnimations(enabled: Boolean) {
        context.denDataStore.edit { it[Keys.WINDOW_ANIMATIONS] = enabled }
    }

    suspend fun setDefaultExternalBrowser(packageName: String?) {
        context.denDataStore.edit {
            if (packageName.isNullOrBlank()) it.remove(Keys.DEFAULT_EXTERNAL_BROWSER)
            else it[Keys.DEFAULT_EXTERNAL_BROWSER] = packageName
        }
    }

    suspend fun setSearchEngine(engineId: String) {
        context.denDataStore.edit { it[Keys.SEARCH_ENGINE] = engineId }
    }

    suspend fun setTryFreeform(enabled: Boolean) {
        context.denDataStore.edit { it[Keys.TRY_FREEFORM] = enabled }
    }

    suspend fun setMouseHoverHighlight(enabled: Boolean) {
        context.denDataStore.edit { it[Keys.MOUSE_HOVER_HIGHLIGHT] = enabled }
    }

    suspend fun addSafTreeUri(uri: String) {
        context.denDataStore.edit { prefs ->
            val current = prefs[Keys.SAF_TREE_URIS]?.split("|")?.filter { it.isNotBlank() }?.toMutableSet()
                ?: mutableSetOf()
            current.add(uri)
            prefs[Keys.SAF_TREE_URIS] = current.joinToString("|")
        }
    }

    suspend fun removeSafTreeUri(uri: String) {
        context.denDataStore.edit { prefs ->
            val current = prefs[Keys.SAF_TREE_URIS]?.split("|")?.filter { it.isNotBlank() }?.toMutableSet()
                ?: mutableSetOf()
            current.remove(uri)
            prefs[Keys.SAF_TREE_URIS] = current.joinToString("|")
        }
    }
}
