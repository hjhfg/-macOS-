package com.ios25pan.launcher.data.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ios25pan.launcher.domain.FileEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * === 这个文件是干什么的 ===
 *
 * 文件管理器的"后端"：真正去读写手机存储的全部 `java.io.File` 操作都在这里，
 * 上面的 Compose 界面（`ui/apps/FileManagerApp.kt`）和状态机
 * （`mvi/FileManagerStore.kt`）都不直接碰 `File`，而是调用这个类——
 * 这是整个项目一直在用的分层方式：UI 只管画，Store 只管状态流转，
 * 真正的"脏活"（文件系统、PackageManager、DataStore……）都封装在 `data/` 包里。
 *
 * === 权限是这里最绕的一块，提前说清楚 ===
 * Android 10（API 29）起系统默认开启"分区存储"（Scoped Storage）：普通 App 只能看到
 * 自己的专属目录和媒体库（照片/视频/音乐），看不到"/storage/emulated/0/随便什么目录"
 * 这种任意路径。真正的文件管理器要突破这个限制，Android 11（API 30）起提供了
 * `MANAGE_EXTERNAL_STORAGE`——"所有文件访问权限"，这不是普通的运行时权限弹窗，
 * 而是要跳到系统设置页，用户手动打开一个开关（和市面上文件管理器 App 要求的操作
 * 一模一样）。[hasFullAccess] 就是在检查这个开关有没有被打开；[allFilesAccessIntent]
 * 生成跳过去的 Intent。
 *
 * 更低版本（API < 30）没有这个"全文件访问"开关，退回传统的 `WRITE_EXTERNAL_STORAGE`
 * 运行时权限（弹一个普通的"允许/拒绝"对话框）即可拿到同等效果。
 */
@Singleton
class FileManagerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 文件管理器打开时默认停在的位置：和系统自带文件管理器一样，是"内部存储"的根目录。 */
    val rootPath: String = Environment.getExternalStorageDirectory().absolutePath

    /** 当前有没有被授权，可以自由读写 [rootPath] 下的任意文件。 */
    fun hasFullAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 跳到"所有文件访问权限"设置页的 Intent；只在 API 30+ 有意义。
     * API < 30 应该走普通的运行时权限请求（[Manifest.permission.WRITE_EXTERNAL_STORAGE]），
     * 那条路径由调用方（Activity）用 `ActivityResultContracts.RequestPermission` 处理，
     * 和控制中心里"系统壁纸权限"的请求方式是同一套，这里不重复提供。
     */
    fun allFilesAccessIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    }

    /** 列出一个目录下的内容：文件夹排在前面，然后按名字排序，和大多数文件管理器的默认顺序一致。 */
    suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val files = File(path).listFiles() ?: return@withContext emptyList()
        files
            // 以 "." 开头的是隐藏文件（.nomedia、.thumbnails 这类），默认不打扰用户
            .filterNot { it.name.startsWith(".") }
            .map { f ->
                FileEntry(
                    name = f.name,
                    path = f.absolutePath,
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isDirectory) 0L else f.length(),
                    lastModified = f.lastModified(),
                )
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /** 新建文件夹。同名已存在、或者系统拒绝创建都会走 [Result.failure]，错误信息直接给用户看。 */
    suspend fun createFolder(parentPath: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(parentPath, name)
            check(!target.exists()) { "“$name”已经存在" }
            check(target.mkdir()) { "创建失败，请检查名称或权限" }
        }
    }

    /** 重命名（在同一个目录下换名字，不涉及移动到别的目录）。 */
    suspend fun rename(entry: FileEntry, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(entry.path)
            val dst = File(src.parentFile, newName)
            check(!dst.exists()) { "“$newName”已经存在" }
            check(src.renameTo(dst)) { "重命名失败" }
        }
    }

    /** 删除一个文件，或者递归删除一整个文件夹（和系统自带文件管理器的"删除"效果一致）。 */
    suspend fun delete(entry: FileEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(deleteRecursively(File(entry.path))) { "“${entry.name}”删除失败" }
        }
    }

    /** 复制到另一个目录；原文件保留。 */
    suspend fun copy(entry: FileEntry, destDir: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(entry.path)
            val dst = File(destDir, src.name)
            check(!dst.exists()) { "目标位置已经有“${src.name}”了" }
            copyRecursively(src, dst)
        }
    }

    /**
     * 移动到另一个目录。优先用 `File.renameTo`（同一个分区上几乎是瞬间完成的"改名字"，
     * 不用真的搬运数据）；它在跨存储分区时会失败，这时退化成"复制一份再删掉原件"。
     */
    suspend fun move(entry: FileEntry, destDir: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(entry.path)
            val dst = File(destDir, src.name)
            check(!dst.exists()) { "目标位置已经有“${src.name}”了" }
            if (!src.renameTo(dst)) {
                copyRecursively(src, dst)
                check(deleteRecursively(src)) { "已复制到新位置，但删除原文件失败" }
            }
        }
    }

    /**
     * 为了把本地文件交给别的 App 打开（点一下文件 -> 用对应的 App 查看），
     * 需要先把 `file://` 路径换成一个真正能跨 App 共享的 `content://` 地址——
     * Android 7.0 起，直接把 `file://` Uri 塞进 Intent 发给别的 App 会被系统
     * 当成安全漏洞直接抛 `FileUriExposedException` 崩溃，[FileProvider] 就是
     * 官方指定的"安全地把本地文件换成一个临时授权地址"的标准做法。
     */
    fun contentUriFor(entry: FileEntry): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(entry.path))

    /** 根据扩展名猜测 MIME 类型，猜不出来就用通配符，交给系统弹"用什么打开"的选择器。 */
    fun mimeTypeFor(entry: FileEntry): String {
        val ext = entry.name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { if (!deleteRecursively(it)) return false }
        }
        return file.delete()
    }

    private fun copyRecursively(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { child -> copyRecursively(child, File(dst, child.name)) }
        } else {
            src.copyTo(dst)
        }
    }
}
