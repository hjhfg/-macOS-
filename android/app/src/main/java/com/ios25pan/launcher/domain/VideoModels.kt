package com.ios25pan.launcher.domain

/**
 * === 这个文件是干什么的 ===
 *
 * "本地视频"小程序的数据模型。和文件管理器不一样——这里不直接拿 `java.io.File` 读目录，
 * 而是查询 Android 的 `MediaStore`（系统维护的一份"手机里有哪些媒体文件"的数据库），
 * 这是官方推荐的做法：不需要"所有文件访问权限"这种重量级特殊权限，只要一个普通的
 * 运行时权限（`READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE`）就能拿到全机所有 App
 * 拍摄/下载过的视频列表，包括系统相册 App 扫描到的那些。
 *
 * 具体怎么查询在 `data/video/VideoLibraryRepository.kt`，这个文件只放"查出来的一条记录
 * 长什么样"。
 */

/** MediaStore 里的一条视频记录。 */
data class VideoEntry(
    /** MediaStore 给每条媒体记录分配的唯一 id，用它可以随时拼出这条记录的 content:// Uri。 */
    val id: Long,
    /** 文件名（不含路径），列表/网格里显示的标题。 */
    val title: String,
    /** 这条记录真正的内容地址（`content://media/external/video/media/<id>` 这种形式）。 */
    val uri: String,
    val durationMs: Long,
    val sizeBytes: Long,
    /** 最后修改时间，epoch **秒**（MediaStore 原始列就是秒，不是毫秒，注意别和别处的毫秒搞混）。 */
    val dateModifiedSec: Long,
    /** 视频所在的相册/文件夹名字（MediaStore 的 BUCKET_DISPLAY_NAME），侧边栏按它分组。 */
    val bucketName: String,
    val width: Int,
    val height: Int,
)

/** 网格/列表怎么排序，和大多数相册/视频 App 的排序菜单选项一致。 */
enum class VideoSortOrder {
    DATE_NEWEST,
    DATE_OLDEST,
    NAME,
    SIZE_LARGEST,
}
