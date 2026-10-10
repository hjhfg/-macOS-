package com.ios25pan.launcher.mvi

import android.content.Intent
import com.ios25pan.launcher.domain.FileClipboard
import com.ios25pan.launcher.domain.FileEntry

/**
 * 文件管理器的 MVI 三件套，写法和 [HomeIntent]/[HomeState]/[HomeEffect] 完全一样——
 * 如果你已经看懂了桌面那一套，这里不需要学新东西，只是换了一个更小的场景。
 *
 * 这里刻意**不**把"要不要弹确认框/输入框"这种纯界面状态放进 [FileManagerState]
 * （比如"删除前有没有弹出确认对话框"）——那是 `ui/apps/FileManagerApp.kt` 自己用
 * `remember { mutableStateOf(...) }` 管理的本地 UI 状态，用户点了"确定"之后才会
 * 发一个真正的 [FileManagerIntent.Delete] 过来。这和 `ControlCenter.kt` 里
 * `var showCrashLog by remember` 是同一个思路：只有"数据层面真正发生的事情"
 * 才值得进 MVI 的 State，纯粹"这个弹窗现在显示还是隐藏"不值得。
 */
sealed interface FileManagerIntent {
    /** 进入一个目录（点文件夹，或者点面包屑上的某一级）。 */
    data class Enter(val path: String) : FileManagerIntent

    /** 返回上一级目录。 */
    data object Up : FileManagerIntent

    /** 重新读取当前目录（下拉刷新 / 做完某个操作后自己调一次）。 */
    data object Refresh : FileManagerIntent

    /** 点一下一个文件（不是文件夹）：交给系统弹"用什么打开"的选择器。 */
    data class Open(val entry: FileEntry) : FileManagerIntent

    /** 长按 / 多选模式下点击：选中或取消选中这一项。 */
    data class ToggleSelect(val entry: FileEntry) : FileManagerIntent
    data object ClearSelection : FileManagerIntent

    data class Delete(val entries: List<FileEntry>) : FileManagerIntent
    data class CreateFolder(val name: String) : FileManagerIntent
    data class Rename(val entry: FileEntry, val newName: String) : FileManagerIntent

    /** 把当前选中的项目记到剪贴板；[cut] = true 是"剪切"，false 是"复制"。 */
    data class SetClipboard(val entries: List<FileEntry>, val cut: Boolean) : FileManagerIntent

    /** 把剪贴板里的内容粘贴到当前目录。 */
    data object Paste : FileManagerIntent

    /** 操作结果提示（成功/失败的文字）已经展示过了，清空它，不要一直挂着。 */
    data object DismissMessage : FileManagerIntent

    /** 重新检查一次"所有文件访问权限"有没有被打开——窗口从后台切回来，或者用户刚从系统设置点了授权。 */
    data object RecheckAccess : FileManagerIntent
}

data class FileManagerState(
    /** 有没有拿到"所有文件访问权限"；没有的话界面只显示一个引导授权的提示，不读取任何目录。 */
    val hasAccess: Boolean = false,
    val currentPath: String = "",
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = true,
    /** 当前多选选中了哪些路径；为空就代表不处于"多选模式"。 */
    val selection: Set<String> = emptySet(),
    val clipboard: FileClipboard? = null,
    /** 操作完成后的一次性文字提示（"已删除" / "重命名失败：xxx"），展示完会被 [FileManagerIntent.DismissMessage] 清空。 */
    val message: String? = null,
) {
    /** 当前目录能不能往上退（退到根目录就到头了）。 */
    fun canGoUp(rootPath: String): Boolean = currentPath.isNotEmpty() && currentPath != rootPath
}

sealed interface FileManagerEffect {
    /** 点开一个文件：把"用什么打开"的系统选择器 Intent 丢给 Activity 去 startActivity。 */
    data class OpenFile(val intent: Intent) : FileManagerEffect
}
