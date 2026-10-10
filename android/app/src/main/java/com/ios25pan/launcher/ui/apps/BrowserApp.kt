package com.ios25pan.launcher.ui.apps

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ios25pan.launcher.domain.BookmarkEntry
import com.ios25pan.launcher.domain.BrowserTab
import com.ios25pan.launcher.domain.HistoryEntry
import com.ios25pan.launcher.mvi.BrowserEffect
import com.ios25pan.launcher.mvi.BrowserIntent
import com.ios25pan.launcher.mvi.BrowserState
import com.ios25pan.launcher.mvi.BrowserStore
import com.ios25pan.launcher.ui.OnGlass

/**
 * === 这个文件是干什么的 ===
 *
 * 浏览器的界面。和文件管理器那边最大的不同：这里真正显示网页的 `android.webkit.WebView`
 * 是一个传统的 Android View（不是 Compose 组件），只能通过 [AndroidView] 包一层塞进 Compose
 * 树里；而且**每个标签页都对应一个独立的 WebView 实例**——这样切换标签页的时候，每个页面的
 * 滚动位置、前进后退历史栈都还在，而不是"切回来发现又要重新加载一遍"。
 *
 * 这些 WebView 实例保存在下面的 `webViews`（一个普通的可变 Map，用 `remember` 包住，
 * 生命周期跟这个 Composable 绑定）——注意这和 `BrowserStore` 里的 `BrowserTab` 列表是两回事：
 * `BrowserTab` 只是"标签页的元数据"（网址、标题……），真正又大又不能随便复制的 WebView 对象
 * 绝对不能塞进 MVI 的 State 里（State 应该是不可变、可随意复制比较的纯数据），所以才拆成这
 * 两层——这是 Android 做"Compose + WebView"类应用的标准做法。
 *
 * 浮动窗口被关掉时，这个 Composable 离开组合，`remember` 持有的 WebView 会被垃圾回收；
 * 但 `BrowserStore`（`hiltViewModel()` 拿到的是跟 Activity 绑定的全局实例）不会被销毁，
 * 标签页列表、书签、历史记录都还在——下次重新打开浏览器窗口时，会按记住的网址给每个
 * 标签页重新创建一个新的 WebView 并 `loadUrl` 过去（代价是丢失了滚动位置/前进后退栈，
 * 但这是很多真实浏览器"后台标签页被系统回收后重新加载"时同样的体验，可以接受）。
 */
@Composable
fun BrowserApp(store: BrowserStore = hiltViewModel()) {
    val state by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // tabId -> 这个标签页专属的 WebView 实例；懒创建（第一次切到这个标签页才 new 一个）。
    val webViews = remember { mutableMapOf<String, WebView>() }

    fun webViewFor(tab: BrowserTab): WebView = webViews.getOrPut(tab.id) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    store.dispatch(
                        BrowserIntent.PageUpdated(
                            tabId = tab.id,
                            url = url ?: "",
                            title = view.title ?: "",
                            isLoading = true,
                            progress = view.progress,
                            canGoBack = view.canGoBack(),
                            canGoForward = view.canGoForward(),
                        ),
                    )
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    store.dispatch(
                        BrowserIntent.PageUpdated(
                            tabId = tab.id,
                            url = url ?: "",
                            title = view.title ?: "",
                            isLoading = false,
                            progress = 100,
                            canGoBack = view.canGoBack(),
                            canGoForward = view.canGoForward(),
                        ),
                    )
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    store.dispatch(
                        BrowserIntent.PageUpdated(
                            tabId = tab.id,
                            url = view.url ?: "",
                            title = view.title ?: "",
                            isLoading = newProgress < 100,
                            progress = newProgress,
                            canGoBack = view.canGoBack(),
                            canGoForward = view.canGoForward(),
                        ),
                    )
                }
            }
            loadUrl(tab.url)
        }
    }

    // 标签页被关掉之后，对应的 WebView 也要跟着销毁、断开引用，否则就是内存泄漏。
    LaunchedEffect(state.tabs) {
        val aliveIds = state.tabs.map { it.id }.toSet()
        val gone = webViews.keys.filterNot { it in aliveIds }
        gone.forEach { id ->
            webViews.remove(id)?.let { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
        }
    }

    // Store 发来的"请操作当前网页"指令（地址栏回车、点后退/前进/刷新按钮）
    // 最终都要落到这个当前激活标签页的真实 WebView 上执行。
    LaunchedEffect(Unit) {
        store.effects.collect { effect ->
            val active = webViews[state.activeTabId] ?: return@collect
            when (effect) {
                is BrowserEffect.LoadUrl -> active.loadUrl(effect.url)
                BrowserEffect.GoBack -> if (active.canGoBack()) active.goBack()
                BrowserEffect.GoForward -> if (active.canGoForward()) active.goForward()
                BrowserEffect.Reload -> active.reload()
            }
        }
    }

    var addressText by remember { mutableStateOf(state.activeTab?.url ?: "") }
    var addressFocused by remember { mutableStateOf(false) }
    LaunchedEffect(state.activeTab?.url, state.activeTabId) {
        if (!addressFocused) addressText = state.activeTab?.url ?: ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BrowserToolbar(
            state = state,
            addressText = addressText,
            onAddressChange = { addressText = it },
            onAddressFocusChanged = { addressFocused = it },
            onNavigate = {
                store.dispatch(BrowserIntent.Navigate(addressText))
                addressFocused = false
            },
            onBack = { store.dispatch(BrowserIntent.GoBack) },
            onForward = { store.dispatch(BrowserIntent.GoForward) },
            onReload = { store.dispatch(BrowserIntent.Reload) },
            onToggleBookmark = { store.dispatch(BrowserIntent.ToggleBookmarkCurrent) },
            onShowBookmarks = { store.dispatch(BrowserIntent.SetBookmarksVisible(true)) },
            onShowHistory = { store.dispatch(BrowserIntent.SetHistoryVisible(true)) },
            onShowTabs = { store.dispatch(BrowserIntent.SetTabSwitcherVisible(true)) },
        )

        if (state.activeTab?.isLoading == true) {
            LinearProgressIndicator(
                progress = { (state.activeTab?.progress ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            val activeTab = state.activeTab
            if (activeTab != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { FrameLayout(it) },
                    update = { frame ->
                        val wv = webViewFor(activeTab)
                        if (wv.parent !== frame) {
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            frame.removeAllViews()
                            frame.addView(
                                wv,
                                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                            )
                        }
                    },
                )
            }

            if (state.showTabSwitcher) {
                TabSwitcherOverlay(
                    tabs = state.tabs,
                    activeId = state.activeTabId,
                    onSelect = {
                        store.dispatch(BrowserIntent.SwitchTab(it))
                        store.dispatch(BrowserIntent.SetTabSwitcherVisible(false))
                    },
                    onClose = { store.dispatch(BrowserIntent.CloseTab(it)) },
                    onNewTab = { store.dispatch(BrowserIntent.NewTab) },
                    onDismiss = { store.dispatch(BrowserIntent.SetTabSwitcherVisible(false)) },
                )
            }

            if (state.showBookmarks) {
                BookmarksOverlay(
                    bookmarks = state.bookmarks,
                    onOpen = { store.dispatch(BrowserIntent.OpenSavedUrl(it)) },
                    onRemove = { store.dispatch(BrowserIntent.RemoveBookmark(it)) },
                    onDismiss = { store.dispatch(BrowserIntent.SetBookmarksVisible(false)) },
                )
            }

            if (state.showHistory) {
                HistoryOverlay(
                    history = state.history,
                    onOpen = { store.dispatch(BrowserIntent.OpenSavedUrl(it)) },
                    onClear = { store.dispatch(BrowserIntent.ClearHistory) },
                    onDismiss = { store.dispatch(BrowserIntent.SetHistoryVisible(false)) },
                )
            }
        }
    }
}

@Composable
private fun BrowserToolbar(
    state: BrowserState,
    addressText: String,
    onAddressChange: (String) -> Unit,
    onAddressFocusChanged: (Boolean) -> Unit,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShowBookmarks: () -> Unit,
    onShowHistory: () -> Unit,
    onShowTabs: () -> Unit,
) {
    val tab = state.activeTab
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = tab?.canGoBack == true, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "后退", tint = toolbarTint(tab?.canGoBack == true))
        }
        IconButton(onClick = onForward, enabled = tab?.canGoForward == true, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.ArrowForward, contentDescription = "前进", tint = toolbarTint(tab?.canGoForward == true))
        }
        IconButton(onClick = onReload, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = OnGlass)
        }
        OutlinedTextField(
            value = addressText,
            onValueChange = onAddressChange,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .height(40.dp)
                .onFocusChangedCompat(onAddressFocusChanged),
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(10.dp),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onNavigate() }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Go,
            ),
        )
        IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
            Icon(
                if (state.isCurrentBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "收藏",
                tint = if (state.isCurrentBookmarked) Color(0xFFFFC107) else OnGlass,
            )
        }
        IconButton(onClick = onShowBookmarks, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.BookmarkBorder, contentDescription = "书签", tint = OnGlass)
        }
        IconButton(onClick = onShowHistory, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.History, contentDescription = "历史记录", tint = OnGlass)
        }
        IconButton(onClick = onShowTabs, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Tab, contentDescription = "标签页（${state.tabs.size}）", tint = OnGlass)
        }
    }
}

private fun toolbarTint(enabled: Boolean) = if (enabled) OnGlass else OnGlass.copy(alpha = 0.3f)

/** `Modifier.onFocusChanged` 需要导入的包名比较绕，这里包一层让上面调用处清爽一点。 */
private fun Modifier.onFocusChangedCompat(onChanged: (Boolean) -> Unit): Modifier =
    this.then(Modifier.onFocusEvent { onChanged(it.isFocused) })

@Composable
private fun TabSwitcherOverlay(
    tabs: List<BrowserTab>,
    activeId: String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayScaffold(title = "标签页", onDismiss = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tabs, key = { it.id }) { tab ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(tab.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tab.title.ifBlank { "新标签页" },
                            color = if (tab.id == activeId) Color(0xFF64B5F6) else OnGlass,
                            fontWeight = if (tab.id == activeId) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                        Text(tab.url, color = OnGlass.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    IconButton(onClick = { onClose(tab.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭标签页", tint = OnGlass)
                    }
                }
            }
            item {
                TextButton(onClick = onNewTab, modifier = Modifier.padding(horizontal = 12.dp)) { Text("+ 新建标签页") }
            }
        }
    }
}

@Composable
private fun BookmarksOverlay(
    bookmarks: List<BookmarkEntry>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayScaffold(title = "书签", onDismiss = onDismiss) {
        if (bookmarks.isEmpty()) {
            EmptyHint("还没有收藏任何网页")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(bookmarks, key = { it.url }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.url) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title.ifBlank { item.url }, color = OnGlass, maxLines = 1)
                            Text(item.url, color = OnGlass.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        IconButton(onClick = { onRemove(item.url) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除书签", tint = OnGlass)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryOverlay(
    history: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayScaffold(title = "历史记录", onDismiss = onDismiss, trailing = { TextButton(onClick = onClear) { Text("清空") } }) {
        if (history.isEmpty()) {
            EmptyHint("还没有浏览记录")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history.sortedByDescending { it.visitedAt }, key = { it.visitedAt.toString() + it.url }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.url) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(item.title.ifBlank { item.url }, color = OnGlass, maxLines = 1)
                        Text(item.url, color = OnGlass.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = OnGlass.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** 标签页/书签/历史记录三个面板共用的外壳：顶部标题 + 关闭按钮，下面是内容区。 */
@Composable
private fun OverlayScaffold(
    title: String,
    onDismiss: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC1C1C1E)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = OnGlass, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            trailing?.invoke()
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "关闭", tint = OnGlass) }
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}
