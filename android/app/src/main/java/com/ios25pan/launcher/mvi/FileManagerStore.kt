package com.ios25pan.launcher.mvi

import android.content.Intent
import com.ios25pan.launcher.data.files.FileManagerRepository
import com.ios25pan.launcher.domain.FileClipboard
import com.ios25pan.launcher.domain.FileEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 文件管理器的 Store（ViewModel）。
 *
 * 用 `hiltViewModel()` 在 Compose 里拿到的是**跟 Activity 绑定、全局唯一**的一个实例——
 * 文件管理器的浮动窗口关掉再打开，拿到的还是同一个 Store，当前浏览到哪个目录、
 * 选中了哪些文件都还在，这是故意的（更像真实文件管理器的使用体验），不是 bug。
 */
@HiltViewModel
class FileManagerStore @Inject constructor(
    private val repo: FileManagerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        FileManagerState(hasAccess = repo.hasFullAccess(), currentPath = repo.rootPath),
    )
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    private val _effects = Channel<FileManagerEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** 根目录路径，UI 判断"面包屑是不是已经到头了"要用。 */
    val rootPath: String get() = repo.rootPath

    init {
        if (_state.value.hasAccess) refresh()
    }

    fun dispatch(intent: FileManagerIntent) {
        when (intent) {
            is FileManagerIntent.Enter -> enter(intent.path)
            FileManagerIntent.Up -> {
                val parent = currentFile().parentFile?.absolutePath
                if (parent != null && parent.startsWith(repo.rootPath)) enter(parent)
            }
            FileManagerIntent.Refresh -> refresh()
            is FileManagerIntent.Open -> openFile(intent.entry)
            is FileManagerIntent.ToggleSelect -> toggleSelect(intent.entry)
            FileManagerIntent.ClearSelection -> _state.update { it.copy(selection = emptySet()) }
            is FileManagerIntent.Delete -> delete(intent.entries)
            is FileManagerIntent.CreateFolder -> createFolder(intent.name)
            is FileManagerIntent.Rename -> rename(intent.entry, intent.newName)
            is FileManagerIntent.SetClipboard -> _state.update {
                it.copy(clipboard = FileClipboard(intent.entries, intent.cut), selection = emptySet())
            }
            FileManagerIntent.Paste -> paste()
            FileManagerIntent.DismissMessage -> _state.update { it.copy(message = null) }
            FileManagerIntent.RecheckAccess -> {
                val has = repo.hasFullAccess()
                _state.update { it.copy(hasAccess = has) }
                // 刚从系统设置授权回来：之前没能加载过目录，现在补上这一次
                if (has && _state.value.entries.isEmpty()) refresh()
            }
        }
    }

    private fun currentFile() = java.io.File(_state.value.currentPath)

    private fun enter(path: String) {
        _state.update { it.copy(currentPath = path, selection = emptySet()) }
        refresh()
    }

    private fun refresh() {
        val path = _state.value.currentPath
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val entries = repo.list(path)
            _state.update { it.copy(entries = entries, loading = false) }
        }
    }

    private fun openFile(entry: FileEntry) {
        if (entry.isDirectory) {
            enter(entry.path)
            return
        }
        viewModelScope.launch {
            val uri = repo.contentUriFor(entry)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, repo.mimeTypeFor(entry))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            _effects.send(FileManagerEffect.OpenFile(intent))
        }
    }

    private fun toggleSelect(entry: FileEntry) {
        _state.update { s ->
            val next = if (entry.path in s.selection) s.selection - entry.path else s.selection + entry.path
            s.copy(selection = next)
        }
    }

    private fun delete(entries: List<FileEntry>) {
        viewModelScope.launch {
            var failed = 0
            entries.forEach { if (repo.delete(it).isFailure) failed++ }
            _state.update { it.copy(selection = emptySet()) }
            report(entries.size, failed, "删除")
            refresh()
        }
    }

    private fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val result = repo.createFolder(_state.value.currentPath, name)
            _state.update { it.copy(message = result.fold({ "已新建文件夹“$name”" }, { e -> e.message ?: "新建失败" })) }
            if (result.isSuccess) refresh()
        }
    }

    private fun rename(entry: FileEntry, newName: String) {
        if (newName.isBlank() || newName == entry.name) return
        viewModelScope.launch {
            val result = repo.rename(entry, newName)
            _state.update { it.copy(message = result.fold({ "已重命名为“$newName”" }, { e -> e.message ?: "重命名失败" })) }
            if (result.isSuccess) refresh()
        }
    }

    private fun paste() {
        val clip = _state.value.clipboard ?: return
        val destDir = _state.value.currentPath
        viewModelScope.launch {
            var failed = 0
            clip.entries.forEach { entry ->
                val result = if (clip.cut) repo.move(entry, destDir) else repo.copy(entry, destDir)
                if (result.isFailure) failed++
            }
            // 剪切粘贴一次性就用掉了；复制粘贴可以反复粘很多次，剪贴板留着
            _state.update { it.copy(clipboard = if (clip.cut) null else it.clipboard) }
            report(clip.entries.size, failed, if (clip.cut) "移动" else "复制")
            refresh()
        }
    }

    private fun report(total: Int, failed: Int, verb: String) {
        val text = if (failed == 0) "已$verb $total 项" else "$verb 完成，其中 $failed 项失败"
        _state.update { it.copy(message = text) }
    }
}
