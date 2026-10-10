package com.ios25pan.launcher.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ios25pan.launcher.data.browser.BrowserPrefs
import com.ios25pan.launcher.domain.BROWSER_HOME_URL
import com.ios25pan.launcher.domain.BookmarkEntry
import com.ios25pan.launcher.domain.BrowserTab
import com.ios25pan.launcher.domain.HistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 浏览器的 Store（ViewModel）。跟 [FileManagerStore] 一样，用 `hiltViewModel()` 拿到的是
 * 跟 Activity 绑定的全局唯一实例——关掉浏览器窗口再打开，所有标签页都还在。
 */
@HiltViewModel
class BrowserStore @Inject constructor(
    private val prefs: BrowserPrefs,
) : ViewModel() {

    private val firstTab = BrowserTab(id = newTabId())
    private val _state = MutableStateFlow(BrowserState(tabs = listOf(firstTab), activeTabId = firstTab.id))
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _effects = Channel<BrowserEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch { prefs.bookmarks.collect { list -> _state.update { it.copy(bookmarks = list) } } }
        viewModelScope.launch { prefs.history.collect { list -> _state.update { it.copy(history = list) } } }
    }

    fun dispatch(intent: BrowserIntent) {
        when (intent) {
            BrowserIntent.NewTab -> newTab()
            is BrowserIntent.CloseTab -> closeTab(intent.id)
            is BrowserIntent.SwitchTab -> _state.update {
                if (it.tabs.any { t -> t.id == intent.id }) it.copy(activeTabId = intent.id) else it
            }
            is BrowserIntent.Navigate -> navigate(intent.input)
            BrowserIntent.GoBack -> sendEffect(BrowserEffect.GoBack)
            BrowserIntent.GoForward -> sendEffect(BrowserEffect.GoForward)
            BrowserIntent.Reload -> sendEffect(BrowserEffect.Reload)
            is BrowserIntent.PageUpdated -> pageUpdated(intent)
            BrowserIntent.ToggleBookmarkCurrent -> toggleBookmark()
            is BrowserIntent.RemoveBookmark -> viewModelScope.launch { prefs.removeBookmark(intent.url) }
            is BrowserIntent.OpenSavedUrl -> {
                _state.update { it.copy(showBookmarks = false, showHistory = false) }
                navigate(intent.url)
            }
            BrowserIntent.ClearHistory -> viewModelScope.launch { prefs.clearHistory() }
            is BrowserIntent.SetBookmarksVisible -> _state.update { it.copy(showBookmarks = intent.visible) }
            is BrowserIntent.SetHistoryVisible -> _state.update { it.copy(showHistory = intent.visible) }
            is BrowserIntent.SetTabSwitcherVisible -> _state.update { it.copy(showTabSwitcher = intent.visible) }
        }
    }

    private fun sendEffect(effect: BrowserEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun newTab() {
        val tab = BrowserTab(id = newTabId())
        _state.update { it.copy(tabs = it.tabs + tab, activeTabId = tab.id, showTabSwitcher = false) }
    }

    private fun closeTab(id: String) {
        _state.update { s ->
            val index = s.tabs.indexOfFirst { it.id == id }
            if (index < 0) return@update s
            val remaining = s.tabs.filterNot { it.id == id }
            if (remaining.isEmpty()) {
                // 关掉最后一个标签页：开一个新的空白页顶上，浏览器窗口本身不会因为"没有标签页"而变空
                val fresh = BrowserTab(id = newTabId())
                return@update s.copy(tabs = listOf(fresh), activeTabId = fresh.id)
            }
            val nextActive = if (s.activeTabId == id) {
                remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.id ?: remaining.first().id
            } else {
                s.activeTabId
            }
            s.copy(tabs = remaining, activeTabId = nextActive)
        }
    }

    /** 地址栏输入 -> 规整成真正的 URL -> 更新当前标签页的地址 -> 通知 UI 让 WebView 去加载。 */
    private fun navigate(input: String) {
        val url = normalizeInput(input)
        val activeId = _state.value.activeTabId
        _state.update { s -> s.copy(tabs = s.tabs.map { if (it.id == activeId) it.copy(url = url) else it }) }
        sendEffect(BrowserEffect.LoadUrl(url))
    }

    private fun pageUpdated(i: BrowserIntent.PageUpdated) {
        var justFinished = false
        var finishedTitle = ""
        var finishedUrl = ""
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { tab ->
                    if (tab.id != i.tabId) return@map tab
                    // 只在"从加载中变成加载完成"这一刻记一条历史，避免同一次加载反复记录
                    if (tab.isLoading && !i.isLoading) {
                        justFinished = true
                        finishedTitle = i.title
                        finishedUrl = i.url
                    }
                    tab.copy(
                        url = i.url,
                        title = i.title.ifBlank { tab.title },
                        isLoading = i.isLoading,
                        progress = i.progress,
                        canGoBack = i.canGoBack,
                        canGoForward = i.canGoForward,
                    )
                },
            )
        }
        if (justFinished && finishedUrl.isNotBlank()) {
            viewModelScope.launch {
                prefs.addHistory(HistoryEntry(finishedTitle.ifBlank { finishedUrl }, finishedUrl, System.currentTimeMillis()))
            }
        }
    }

    private fun toggleBookmark() {
        val tab = _state.value.activeTab ?: return
        viewModelScope.launch {
            if (_state.value.isCurrentBookmarked) {
                prefs.removeBookmark(tab.url)
            } else {
                prefs.addBookmark(BookmarkEntry(tab.title.ifBlank { tab.url }, tab.url))
            }
        }
    }

    private companion object {
        fun newTabId(): String = UUID.randomUUID().toString()

        /**
         * 地址栏的输入可能是网址，也可能是一句"随便搜点什么"：
         * - 已经带 `http://`/`https://` 前缀：直接用；
         * - 形如 `example.com`（包含点号、不含空格）：大概率是省略了协议头的网址，补一个 `https://`；
         * - 其它情况：当成搜索词，交给搜索引擎。
         */
        fun normalizeInput(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return BROWSER_HOME_URL
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
            val looksLikeDomain = !trimmed.contains(" ") && trimmed.contains(".")
            if (looksLikeDomain) return "https://$trimmed"
            val query = java.net.URLEncoder.encode(trimmed, "UTF-8")
            return "https://www.bing.com/search?q=$query"
        }
    }
}
