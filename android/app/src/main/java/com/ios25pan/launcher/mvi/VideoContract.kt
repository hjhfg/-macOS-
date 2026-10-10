package com.ios25pan.launcher.mvi

import com.ios25pan.launcher.domain.VideoEntry
import com.ios25pan.launcher.domain.VideoSortOrder

/**
 * "本地视频"小程序的 MVI 三件套，写法和 [FileManagerContract]/[BrowserContract] 是同一套路数。
 *
 * 和浏览器那边类似：真正"能看见画面"的播放器（`androidx.media3.ui.PlayerView`）是一个
 * 传统 Android View，不归这里的 [VideoState] 管——这里只记"现在打算播放哪一条"
 * ([VideoState.playing])，真正的播放器实例活在 `ui/apps/VideoApp.kt` 里，原因和
 * `BrowserContract.kt` 文件头说的一样：播放器这种又大又不该随意复制比较的对象，
 * 不适合塞进不可变的 MVI State。
 */
sealed interface VideoIntent {
    /** 重新扫一遍 MediaStore（下拉刷新 / 刚拿到权限时）。 */
    data object Refresh : VideoIntent

    /** 重新检查一次"读取媒体库"的权限有没有被打开。 */
    data object RecheckAccess : VideoIntent

    /** 侧边栏点了某个相册/文件夹；null 表示"全部视频"。 */
    data class SelectBucket(val bucket: String?) : VideoIntent

    data class SetSortOrder(val order: VideoSortOrder) : VideoIntent
    data class SetSearchQuery(val query: String) : VideoIntent

    /** 双击/点开一个视频：进入播放界面。 */
    data class Play(val entry: VideoEntry) : VideoIntent

    /** 从播放界面返回网格/列表。 */
    data object StopPlayback : VideoIntent
}

data class VideoState(
    val hasAccess: Boolean = false,
    val loading: Boolean = true,
    val videos: List<VideoEntry> = emptyList(),
    val selectedBucket: String? = null,
    val sortOrder: VideoSortOrder = VideoSortOrder.DATE_NEWEST,
    val searchQuery: String = "",
    /** 正在播放界面里打开的那一条；null 表示还停在网格/列表页。 */
    val playing: VideoEntry? = null,
) {
    /** 侧边栏要展示的相册/文件夹列表：按名字排序，去重，"全部视频"不算在内（UI 单独画一行）。 */
    val buckets: List<String> get() = videos.map { it.bucketName }.distinct().sorted()

    /** 真正要铺到网格/列表里的那一份：先按相册筛，再按搜索词筛，最后按排序方式排好。 */
    val visibleVideos: List<VideoEntry> get() {
        val byBucket = if (selectedBucket == null) videos else videos.filter { it.bucketName == selectedBucket }
        val bySearch = if (searchQuery.isBlank()) {
            byBucket
        } else {
            byBucket.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        return when (sortOrder) {
            VideoSortOrder.DATE_NEWEST -> bySearch.sortedByDescending { it.dateModifiedSec }
            VideoSortOrder.DATE_OLDEST -> bySearch.sortedBy { it.dateModifiedSec }
            VideoSortOrder.NAME -> bySearch.sortedBy { it.title.lowercase() }
            VideoSortOrder.SIZE_LARGEST -> bySearch.sortedByDescending { it.sizeBytes }
        }
    }
}
