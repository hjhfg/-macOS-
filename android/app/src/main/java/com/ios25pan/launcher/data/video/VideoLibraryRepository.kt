package com.ios25pan.launcher.data.video

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat
import com.ios25pan.launcher.domain.VideoEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * === 这个文件是干什么的 ===
 *
 * "本地视频"小程序的"后端"：查询系统 `MediaStore` 拿到全机视频列表、按需生成缩略图。
 * 和 `data/files/FileManagerRepository.kt` 的思路差别很大，值得先说清楚：
 *
 * - 文件管理器要的是"任意目录"，必须拿 `MANAGE_EXTERNAL_STORAGE` 这种重量级特殊权限
 *   （跳系统设置页手动开关）；
 * - 这里只要"系统已经索引过的媒体文件"，Android 专门为这种场景提供了 `MediaStore`——
 *   一个系统后台维护的数据库，所有 App（相册、相机、下载的视频……）新增/删除媒体文件时
 *   都会去更新它。查询它只需要一个**普通运行时权限**（`READ_MEDIA_VIDEO`，Android 13
 *   以下用 `READ_EXTERNAL_STORAGE`），用户点一下"允许"就行，不用去系统设置页。
 *
 * 这是官方文档明确推荐的做法：想读"相册里有什么"，优先用 MediaStore，而不是自己去扫
 * `/storage/emulated/0` 底下的目录——那样既慢（要递归扫全部文件），又拿不到外部 App
 * 通过 MediaStore 单独"贡献"进来、物理上不在公开目录里的媒体项。
 */
@Singleton
class VideoLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 缩略图这种"生成一次很贵、以后反复用"的位图，用 LRU 缓存，和 AppRepository 的图标缓存同款思路。 */
    private val thumbnailCache = LruCache<Long, Bitmap>(THUMBNAIL_CACHE_SIZE)

    /** 当前 Android 版本下，"能查到视频列表"需要的那一个运行时权限。 */
    fun requiredPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun hasAccess(): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    /** 查询全机视频，按最后修改时间倒序（和大多数相册 App"最新的排前面"的默认顺序一致）。 */
    suspend fun list(): List<VideoEntry> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptyList()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )
        val result = mutableListOf<VideoEntry>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    // 单条记录出问题（字段畸形之类）不该拖垮整个列表，跳过它就好——
                    // 和 AppRepository.launchableApps() 对"个别应用读取失败"的处理思路一样。
                    runCatching {
                        val id = cursor.getLong(idCol)
                        result += VideoEntry(
                            id = id,
                            title = cursor.getString(nameCol) ?: "未命名视频",
                            uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString(),
                            durationMs = cursor.getLong(durationCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            dateModifiedSec = cursor.getLong(dateCol),
                            bucketName = cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "其它",
                            width = cursor.getInt(widthCol),
                            height = cursor.getInt(heightCol),
                        )
                    }.onFailure { Log.w(TAG, "跳过一条异常视频记录", it) }
                }
            }
        }.onFailure { Log.w(TAG, "读取本地视频列表失败", it) }
        result
    }

    /**
     * 生成（或从缓存里取）一张缩略图。
     *
     * Android 10（API 29）起，`ContentResolver.loadThumbnail` 是官方推荐的标准做法——
     * 系统自己决定怎么最高效地拿到一张缩略图（很多时候是直接读取视频编码时内嵌的封面帧，
     * 不需要真的解码视频）。更低版本没有这个 API，退回同样来自官方、但已标记过时的
     * `MediaStore.Video.Thumbnails.getThumbnail`——过时不代表不能用，只是新项目不推荐，
     * 这里只在跑不到新 API 的老设备上才会用到这条分支。
     */
    suspend fun thumbnail(entry: VideoEntry): Bitmap? = withContext(Dispatchers.IO) {
        thumbnailCache.get(entry.id)?.let { return@withContext it }
        runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(Uri.parse(entry.uri), Size(THUMBNAIL_PX, THUMBNAIL_PX), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    entry.id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null,
                )
            }
            bitmap?.also { thumbnailCache.put(entry.id, it) }
        }.onFailure { Log.w(TAG, "生成缩略图失败: ${entry.title}", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "VideoLibraryRepository"
        const val THUMBNAIL_CACHE_SIZE = 64
        const val THUMBNAIL_PX = 480
    }
}
