package com.ios25pan.launcher.data.browser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ios25pan.launcher.domain.BookmarkEntry
import com.ios25pan.launcher.domain.HistoryEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(name = "browser_prefs")

/**
 * 浏览器的书签 / 历史记录持久化。
 *
 * 项目里已经在用 Jetpack DataStore（见 `data/prefs/LauncherPrefs.kt`），这里沿用同一套，
 * 只是开了一个独立的 DataStore 文件（`browser_prefs`），不跟桌面设置混在一起。
 *
 * 没有引入 JSON 库（项目目前没有 kotlinx.serialization 依赖，这个沙箱环境也没法连到
 * Google 的 Maven 仓库去新增依赖验证），书签/历史记录改用最朴素的"一行一条记录，
 * 字段之间用一个几乎不可能出现在正常文本里的字符分隔"的办法手写编码：
 * - 分隔符用 `\u0001`（ASCII 里的"标题开始"控制字符），网页标题、网址正常情况下都不会用到它；
 * - 多条记录之间用换行符 `\n` 分隔。
 * 新手如果看不懂这种格式也没关系——本质就是 Excel 表格另存成"用某个符号分列"的纯文本，
 * 道理是一样的。
 */
@Singleton
class BrowserPrefs @Inject constructor(@ApplicationContext private val context: Context) {

    private val bookmarksKey = stringPreferencesKey("bookmarks")
    private val historyKey = stringPreferencesKey("history")

    val bookmarks: Flow<List<BookmarkEntry>> = context.browserDataStore.data
        .map { prefs -> decodeLines(prefs[bookmarksKey]).map { BookmarkEntry(title = it[0], url = it[1]) } }
        .distinctUntilChanged()

    val history: Flow<List<HistoryEntry>> = context.browserDataStore.data
        .map { prefs ->
            decodeLines(prefs[historyKey]).mapNotNull { f ->
                val visitedAt = f.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
                HistoryEntry(title = f[0], url = f[1], visitedAt = visitedAt)
            }
        }
        .distinctUntilChanged()

    suspend fun addBookmark(entry: BookmarkEntry) {
        context.browserDataStore.edit { prefs ->
            val current = decodeLines(prefs[bookmarksKey]).map { BookmarkEntry(it[0], it[1]) }
            // 已经收藏过同一个网址就不用重复加一条
            if (current.none { it.url == entry.url }) {
                prefs[bookmarksKey] = encodeLines(current + entry) { listOf(it.title, it.url) }
            }
        }
    }

    suspend fun removeBookmark(url: String) {
        context.browserDataStore.edit { prefs ->
            val current = decodeLines(prefs[bookmarksKey]).map { BookmarkEntry(it[0], it[1]) }
            prefs[bookmarksKey] = encodeLines(current.filterNot { it.url == url }) { listOf(it.title, it.url) }
        }
    }

    /** 新访问的记录加在最前面；只保留最近 [MAX_HISTORY] 条，历史记录不该无限膨胀下去。 */
    suspend fun addHistory(entry: HistoryEntry) {
        context.browserDataStore.edit { prefs ->
            val current = decodeLines(prefs[historyKey]).mapNotNull { f ->
                val visitedAt = f.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
                HistoryEntry(f[0], f[1], visitedAt)
            }
            val next = (listOf(entry) + current).take(MAX_HISTORY)
            prefs[historyKey] = encodeLines(next) { listOf(it.title, it.url, it.visitedAt.toString()) }
        }
    }

    suspend fun clearHistory() {
        context.browserDataStore.edit { it[historyKey] = "" }
    }

    // ---- 编码 / 解码的小工具函数 ----

    private fun <T> encodeLines(items: List<T>, fields: (T) -> List<String>): String =
        items.joinToString("\n") { fields(it).joinToString(FIELD_SEP) }

    /** 按行拆开，再按字段分隔符拆开每一行；空字符串/空行直接跳过，不产出一条"空记录"。 */
    private fun decodeLines(raw: String?): List<List<String>> =
        raw?.split("\n")?.filter { it.isNotBlank() }?.map { it.split(FIELD_SEP) } ?: emptyList()

    private companion object {
        const val FIELD_SEP = "\u0001"
        const val MAX_HISTORY = 200
    }
}
