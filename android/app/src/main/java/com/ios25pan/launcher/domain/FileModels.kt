package com.ios25pan.launcher.domain

/**
 * 文件管理器里的一条记录：要么是一个文件夹，要么是一个普通文件。
 *
 * [path] 是绝对路径（比如 `/storage/emulated/0/Download`），所有的文件操作
 * （删除、重命名、复制、移动）都是拿这个路径去找真实的 `java.io.File`，
 * 不是靠 [name] ——同名文件在不同目录下到处都是，只有完整路径才能唯一定位。
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    /** 文件夹统一填 0，大小对文件夹没有直接意义（要递归算很贵，用户也很少关心）。 */
    val sizeBytes: Long,
    /** 最后修改时间，Java 的 epoch 毫秒数，列表里格式化成"几分钟前/日期"用。 */
    val lastModified: Long,
)

/** 剪贴板：记着"复制"还是"剪切"了哪些文件，粘贴的时候照这个办。 */
data class FileClipboard(
    val entries: List<FileEntry>,
    /** true = 剪切（粘贴后把原文件删掉），false = 复制（原文件保留）。 */
    val cut: Boolean,
)
