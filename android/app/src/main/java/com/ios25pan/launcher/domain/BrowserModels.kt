package com.ios25pan.launcher.domain

/** 新标签页默认打开的网址。 */
const val BROWSER_HOME_URL = "https://www.bing.com"

/**
 * 浏览器的一个标签页——这里只存"元数据"（地址栏上能看到的那些信息）。
 *
 * 真正的网页内容、前进/后退的历史栈，是 Android 系统的 `WebView` 自己在内部管理的，
 * 不会（也没办法）塞进这样一个普通的数据类里；那部分状态活在 `ui/apps/BrowserApp.kt`
 * 里每个标签页对应的真实 `WebView` 对象上，详见那个文件头部的说明。
 */
data class BrowserTab(
    val id: String,
    val url: String = BROWSER_HOME_URL,
    val title: String = "新标签页",
    val isLoading: Boolean = false,
    /** 页面加载进度，0..100，地址栏下面那条细进度条用。 */
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

/** 一条书签。 */
data class BookmarkEntry(val title: String, val url: String)

/** 一条历史记录；[visitedAt] 是访问时刻的 epoch 毫秒数，列表按时间倒序显示。 */
data class HistoryEntry(val title: String, val url: String, val visitedAt: Long)
