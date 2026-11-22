package com.denwindows.v1.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.denwindows.v1.data.BookmarkEntity
import com.denwindows.v1.data.HistoryEntity
import com.denwindows.v1.data.SearchEngineOption
import com.denwindows.v1.theme.LocalDenColors
import java.util.UUID

data class BrowserTabState(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "https://duckduckgo.com",
    val inputUrl: String = "https://duckduckgo.com",
    val progress: Int = 100,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val errorMessage: String? = null
)

data class BrowserDownloadItem(
    val url: String,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis()
)

private enum class BrowserDrawerMode { NONE, BOOKMARKS, HISTORY, DOWNLOADS }

@Composable
fun DenBrowserWindow(
    initialUrl: String?,
    searchEngine: SearchEngineOption,
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>,
    onAddBookmark: (String, String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onRecordHistory: (String, String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenInExternalBrowser: (String) -> Unit,
    onEnqueueSystemDownload: (String, String) -> Unit
) {
    val denColors = LocalDenColors.current
    val homeUrl = when (searchEngine) {
        SearchEngineOption.DUCKDUCKGO -> "https://duckduckgo.com"
        SearchEngineOption.GOOGLE -> "https://www.google.com"
        SearchEngineOption.BING -> "https://www.bing.com"
    }

    val startUrl = initialUrl?.takeIf { it.isNotBlank() } ?: homeUrl
    val tabs = remember { mutableStateListOf(BrowserTabState(title = "Home", url = startUrl, inputUrl = startUrl)) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    val webViews = remember { mutableStateMapOf<String, WebView>() }
    val downloads = remember { mutableStateListOf<BrowserDownloadItem>() }
    var drawerMode by remember { mutableStateOf(BrowserDrawerMode.NONE) }

    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
    val isBookmarked = bookmarks.any { it.url == activeTab.url }

    fun updateTab(id: String, transform: (BrowserTabState) -> BrowserTabState) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx >= 0) tabs[idx] = transform(tabs[idx])
    }

    fun resolveInputToUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return homeUrl
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (!trimmed.contains(" ") && trimmed.contains(".")) return "https://$trimmed"
        return searchEngine.queryUrlPrefix + java.net.URLEncoder.encode(trimmed, "UTF-8")
    }

    fun navigateActive(raw: String) {
        val targetUrl = resolveInputToUrl(raw)
        updateTab(activeTab.id) {
            it.copy(url = targetUrl, inputUrl = targetUrl, errorMessage = null, isLoading = true)
        }
        webViews[activeTab.id]?.loadUrl(targetUrl)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { wv ->
                runCatching {
                    wv.stopLoading()
                    wv.destroy()
                }
            }
            webViews.clear()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(denColors.windowSurface)) {
        // Tab Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(denColors.secondarySurface.copy(alpha = 0.75f))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = tab.id == activeTab.id
                    Row(
                        modifier = Modifier
                            .width(156.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                            .background(if (selected) denColors.windowSurface else denColors.secondarySurface.copy(alpha = 0.4f))
                            .clickable { activeTabId = tab.id }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab.title.ifBlank { tab.url },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) denColors.textPrimary else denColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (tabs.size > 1) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close tab",
                                tint = denColors.textSecondary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        val removedId = tab.id
                                        webViews.remove(removedId)?.destroy()
                                        val idx = tabs.indexOfFirst { it.id == removedId }
                                        tabs.removeAll { it.id == removedId }
                                        if (activeTabId == removedId && tabs.isNotEmpty()) {
                                            activeTabId = tabs[(idx - 1).coerceAtLeast(0)].id
                                        }
                                    }
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = {
                    val newTab = BrowserTabState(title = "Home", url = homeUrl, inputUrl = homeUrl)
                    tabs.add(newTab)
                    activeTabId = newTab.id
                },
                modifier = Modifier.size(28.dp).testTag("browser_new_tab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New Tab", tint = denColors.textPrimary, modifier = Modifier.size(16.dp))
            }
        }

        // Navigation & Address Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = { webViews[activeTab.id]?.goBack() },
                enabled = activeTab.canGoBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (activeTab.canGoBack) denColors.textPrimary else denColors.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(
                onClick = { webViews[activeTab.id]?.goForward() },
                enabled = activeTab.canGoForward,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (activeTab.canGoForward) denColors.textPrimary else denColors.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(
                onClick = {
                    val wv = webViews[activeTab.id]
                    if (activeTab.isLoading) wv?.stopLoading() else wv?.reload()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (activeTab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                    contentDescription = if (activeTab.isLoading) "Stop" else "Reload",
                    tint = denColors.textPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(
                onClick = { navigateActive(homeUrl) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Home, contentDescription = "Home", tint = denColors.textPrimary, modifier = Modifier.size(17.dp))
            }

            // Address Bar
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(denColors.secondarySurface)
                    .border(1.dp, denColors.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (activeTab.url.startsWith("https://")) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = if (activeTab.url.startsWith("https://")) denColors.success else denColors.warning,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = activeTab.inputUrl,
                    onValueChange = { text -> updateTab(activeTab.id) { it.copy(inputUrl = text) } },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = denColors.textPrimary),
                    cursorBrush = SolidColor(denColors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { navigateActive(activeTab.inputUrl) }),
                    modifier = Modifier.weight(1f).testTag("browser_address_bar")
                )
                IconButton(
                    onClick = {
                        if (isBookmarked) onRemoveBookmark(activeTab.url)
                        else onAddBookmark(activeTab.title, activeTab.url)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) denColors.accent else denColors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    drawerMode = if (drawerMode == BrowserDrawerMode.BOOKMARKS) BrowserDrawerMode.NONE else BrowserDrawerMode.BOOKMARKS
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Bookmarks", tint = denColors.textSecondary, modifier = Modifier.size(17.dp))
            }
            IconButton(
                onClick = {
                    drawerMode = if (drawerMode == BrowserDrawerMode.HISTORY) BrowserDrawerMode.NONE else BrowserDrawerMode.HISTORY
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.History, contentDescription = "History", tint = denColors.textSecondary, modifier = Modifier.size(17.dp))
            }
            IconButton(
                onClick = {
                    drawerMode = if (drawerMode == BrowserDrawerMode.DOWNLOADS) BrowserDrawerMode.NONE else BrowserDrawerMode.DOWNLOADS
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Downloads", tint = denColors.textSecondary, modifier = Modifier.size(17.dp))
            }
            IconButton(
                onClick = { onOpenInExternalBrowser(activeTab.url) },
                modifier = Modifier.size(32.dp).testTag("browser_open_external")
            ) {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open in External Browser", tint = denColors.accent, modifier = Modifier.size(18.dp))
            }
        }

        if (activeTab.isLoading && activeTab.progress < 100) {
            LinearProgressIndicator(
                progress = { activeTab.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = denColors.accent
            )
        }

        // Main WebView + Optional Side Drawer
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                key(activeTab.id) {
                    val tabId = activeTab.id
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            webViews[tabId] ?: WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                configureSecureWebSettings(settings)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        updateTab(tabId) {
                                            it.copy(
                                                url = url ?: it.url,
                                                inputUrl = url ?: it.inputUrl,
                                                isLoading = true,
                                                errorMessage = null,
                                                canGoBack = view?.canGoBack() ?: false,
                                                canGoForward = view?.canGoForward() ?: false
                                            )
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        val finalUrl = url ?: activeTab.url
                                        val finalTitle = view?.title?.takeIf { it.isNotBlank() } ?: finalUrl
                                        updateTab(tabId) {
                                            it.copy(
                                                title = finalTitle,
                                                url = finalUrl,
                                                inputUrl = finalUrl,
                                                isLoading = false,
                                                progress = 100,
                                                canGoBack = view?.canGoBack() ?: false,
                                                canGoForward = view?.canGoForward() ?: false
                                            )
                                        }
                                        if (finalUrl.startsWith("http")) {
                                            onRecordHistory(finalTitle, finalUrl)
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            updateTab(tabId) {
                                                it.copy(
                                                    isLoading = false,
                                                    errorMessage = error?.description?.toString() ?: "Unable to load page."
                                                )
                                            }
                                        }
                                    }

                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: SslErrorHandler?,
                                        error: SslError?
                                    ) {
                                        // Never bypass TLS validation
                                        handler?.cancel()
                                        updateTab(tabId) {
                                            it.copy(
                                                isLoading = false,
                                                errorMessage = "Blocked insecure TLS/SSL connection (Code ${error?.primaryError ?: -1})."
                                            )
                                        }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        updateTab(tabId) { it.copy(progress = newProgress) }
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) {
                                            updateTab(tabId) { it.copy(title = title) }
                                        }
                                    }
                                }
                                setDownloadListener(DownloadListener { url, _, contentDisposition, _, _ ->
                                    val name = android.webkit.URLUtil.guessFileName(url, contentDisposition, null)
                                    downloads.add(0, BrowserDownloadItem(url, name))
                                    onEnqueueSystemDownload(url, name)
                                    drawerMode = BrowserDrawerMode.DOWNLOADS
                                })
                                loadUrl(activeTab.url)
                                webViews[tabId] = this
                            }
                        },
                        update = { wv ->
                            if (wv.parent != null) {
                                // Existing WebView stays attached cleanly
                            }
                        }
                    )
                }

                // Error Page Overlay
                activeTab.errorMessage?.let { err ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = denColors.windowSurface
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = denColors.warning, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Page Could Not Be Loaded", style = MaterialTheme.typography.headlineMedium, color = denColors.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(err, style = MaterialTheme.typography.bodyMedium, color = denColors.textSecondary)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { navigateActive(activeTab.url) }) {
                                    Text("Retry")
                                }
                                OutlinedButton(onClick = { onOpenInExternalBrowser(activeTab.url) }) {
                                    Text("Open in External Browser")
                                }
                            }
                        }
                    }
                }
            }

            // Bookmarks / History / Downloads Side Drawer
            if (drawerMode != BrowserDrawerMode.NONE) {
                Column(
                    modifier = Modifier
                        .width(230.dp)
                        .fillMaxHeight()
                        .background(denColors.secondarySurface)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (drawerMode) {
                                BrowserDrawerMode.BOOKMARKS -> "Bookmarks"
                                BrowserDrawerMode.HISTORY -> "History"
                                BrowserDrawerMode.DOWNLOADS -> "Downloads"
                                BrowserDrawerMode.NONE -> ""
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = denColors.textPrimary
                        )
                        Row {
                            if (drawerMode == BrowserDrawerMode.HISTORY && history.isNotEmpty()) {
                                TextButton(onClick = onClearHistory) {
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                            IconButton(onClick = { drawerMode = BrowserDrawerMode.NONE }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Filled.Close, null, tint = denColors.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (drawerMode) {
                            BrowserDrawerMode.BOOKMARKS -> {
                                items(bookmarks, key = { it.id }) { bm ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(denColors.windowSurface.copy(alpha = 0.6f))
                                            .clickable { navigateActive(bm.url) }
                                            .padding(8.dp)
                                    ) {
                                        Text(bm.title, style = MaterialTheme.typography.bodySmall, color = denColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(bm.url, style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            BrowserDrawerMode.HISTORY -> {
                                items(history, key = { it.id }) { h ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(denColors.windowSurface.copy(alpha = 0.6f))
                                            .clickable { navigateActive(h.url) }
                                            .padding(8.dp)
                                    ) {
                                        Text(h.title, style = MaterialTheme.typography.bodySmall, color = denColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(h.url, style = MaterialTheme.typography.labelSmall, color = denColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            BrowserDrawerMode.DOWNLOADS -> {
                                items(downloads) { dl ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(denColors.windowSurface.copy(alpha = 0.6f))
                                            .padding(8.dp)
                                    ) {
                                        Text(dl.fileName, style = MaterialTheme.typography.bodySmall, color = denColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Sent to Android DownloadManager", style = MaterialTheme.typography.labelSmall, color = denColors.accent)
                                    }
                                }
                            }
                            BrowserDrawerMode.NONE -> Unit
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureSecureWebSettings(settings: WebSettings) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        settings.safeBrowsingEnabled = true
    }
}
