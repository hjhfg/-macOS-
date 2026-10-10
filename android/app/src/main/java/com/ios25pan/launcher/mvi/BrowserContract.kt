package com.ios25pan.launcher.mvi

import com.ios25pan.launcher.domain.BookmarkEntry
import com.ios25pan.launcher.domain.BrowserTab
import com.ios25pan.launcher.domain.HistoryEntry

/**
 * 浏览器的 MVI 三件套。
 *
 * 和桌面、文件管理器不太一样的地方：真正"能看见网页内容"的 `android.webkit.WebView`
 * 是一个传统 Android View，它**不是**由这里的 [BrowserState] 描述、也不归这个 Store 持有——
 * WebView 实例活在 `ui/apps/BrowserApp.kt` 里（原因见那个文件的文件头注释）。所以这一层
 * 分成两种"指令"：
 *  - [BrowserIntent.PageUpdated] 是**WebView 往外汇报事实**（网页标题变了、能不能后退了……），
 *    Store 只是把这些事实记下来，给工具栏按钮的"能不能点"、标题栏文字显示用；
 *  - [BrowserIntent.Navigate]/[BrowserIntent.GoBack] 等是**用户想让当前网页做什么**，
 *    Store 自己不会去操作 WebView（它拿不到 WebView 的引用），而是通过 [BrowserEffect]
 *    把"请做这件事"扔回 UI 层，UI 再去调用真正的 `webView.loadUrl(...)` / `webView.goBack()`。
 */
sealed interface BrowserIntent {
    data object NewTab : BrowserIntent
    data class CloseTab(val id: String) : BrowserIntent
    data class SwitchTab(val id: String) : BrowserIntent

    /** 地址栏回车：[input] 可能是一个网址，也可能是一句搜索词，由 Store 判断并规整成真正的 URL。 */
    data class Navigate(val input: String) : BrowserIntent

    data object GoBack : BrowserIntent
    data object GoForward : BrowserIntent
    data object Reload : BrowserIntent

    /** WebView 的回调观察到的事实，同步回状态里。 */
    data class PageUpdated(
        val tabId: String,
        val url: String,
        val title: String,
        val isLoading: Boolean,
        val progress: Int,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    ) : BrowserIntent

    /** 收藏/取消收藏"当前这个标签页正在看的网址"。 */
    data object ToggleBookmarkCurrent : BrowserIntent
    data class RemoveBookmark(val url: String) : BrowserIntent

    /** 点书签面板/历史记录面板里的一条：跳过去，并收起对应面板。 */
    data class OpenSavedUrl(val url: String) : BrowserIntent

    data object ClearHistory : BrowserIntent

    data class SetBookmarksVisible(val visible: Boolean) : BrowserIntent
    data class SetHistoryVisible(val visible: Boolean) : BrowserIntent
    data class SetTabSwitcherVisible(val visible: Boolean) : BrowserIntent
}

data class BrowserState(
    val tabs: List<BrowserTab> = emptyList(),
    val activeTabId: String = "",
    val bookmarks: List<BookmarkEntry> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val showBookmarks: Boolean = false,
    val showHistory: Boolean = false,
    val showTabSwitcher: Boolean = false,
) {
    val activeTab: BrowserTab? get() = tabs.find { it.id == activeTabId }
    val isCurrentBookmarked: Boolean get() = activeTab?.let { tab -> bookmarks.any { it.url == tab.url } } ?: false
}

sealed interface BrowserEffect {
    /** 让"当前正显示着的那个 WebView"去加载这个地址。 */
    data class LoadUrl(val url: String) : BrowserEffect
    data object GoBack : BrowserEffect
    data object GoForward : BrowserEffect
    data object Reload : BrowserEffect
}
