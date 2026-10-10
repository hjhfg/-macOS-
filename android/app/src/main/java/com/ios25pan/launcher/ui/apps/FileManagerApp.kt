package com.ios25pan.launcher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ios25pan.launcher.domain.FileEntry
import com.ios25pan.launcher.mvi.FileManagerEffect
import com.ios25pan.launcher.mvi.FileManagerIntent
import com.ios25pan.launcher.mvi.FileManagerStore
import com.ios25pan.launcher.ui.OnGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * === 这个文件是干什么的 ===
 *
 * 文件管理器的界面——这是浮动窗口（见 `ui/window/FloatingWindow.kt`）里具体显示的内容之一。
 * 这个文件只管"画出来 + 把用户操作转成 Intent 丢给 Store"，真正的文件读写在
 * `data/files/FileManagerRepository.kt`，状态怎么流转在 `mvi/FileManagerStore.kt`——
 * 和桌面那一套 MVI 分层完全一样，如果你已经看懂了 `HomeScreen.kt`，这里不需要学新东西。
 *
 * 弹窗（新建文件夹、重命名、删除确认）用的是**本地 Compose 状态**
 * （`remember { mutableStateOf(...) }`），不是 MVI 的 State——原因见 `FileManagerContract.kt`
 * 文件头的说明：是否显示一个弹窗，是纯界面层面的事，用户点"确定"之后才会真正发一个
 * 会改变数据的 Intent 过去。
 *
 * @param onRequestAccess 用户还没有授予"所有文件访问权限"时，点击按钮应该做什么——
 *   具体是跳系统设置页还是弹运行时权限对话框，由 Activity 决定（不同 Android 版本不一样，
 *   见 `MainActivity.kt` 的注释），这个文件完全不关心，只管在合适的时候调用这个回调。
 */
@Composable
fun FileManagerApp(onRequestAccess: () -> Unit, store: FileManagerStore = hiltViewModel()) {
    val state by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 窗口第一次出现、或者用户刚从系统设置里把权限开关打开回来时，都应该重新判断一次权限。
    LaunchedEffect(Unit) { store.dispatch(FileManagerIntent.RecheckAccess) }

    LaunchedEffect(Unit) {
        store.effects.collect { effect ->
            when (effect) {
                is FileManagerEffect.OpenFile -> runCatching { context.startActivity(effect.intent) }
            }
        }
    }

    // ---- 以下都是纯界面状态：会不会弹出某个对话框、输入框里现在是什么文字 ----
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<List<FileEntry>?>(null) }

    if (!state.hasAccess) {
        PermissionGate(onRequestAccess = onRequestAccess)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Toolbar(
            path = state.currentPath,
            rootPath = store.rootPath,
            canGoUp = state.canGoUp(store.rootPath),
            hasClipboard = state.clipboard != null,
            onUp = { store.dispatch(FileManagerIntent.Up) },
            onRefresh = { store.dispatch(FileManagerIntent.Refresh) },
            onNewFolder = { showNewFolderDialog = true },
            onPaste = { store.dispatch(FileManagerIntent.Paste) },
        )

        state.message?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2500)
                store.dispatch(FileManagerIntent.DismissMessage)
            }
            Text(
                text = msg,
                color = OnGlass,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = OnGlass)
            } else if (state.entries.isEmpty()) {
                Text("这里是空的", color = OnGlass.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            selected = entry.path in state.selection,
                            selecting = state.selection.isNotEmpty(),
                            onClick = {
                                if (state.selection.isNotEmpty()) {
                                    store.dispatch(FileManagerIntent.ToggleSelect(entry))
                                } else {
                                    store.dispatch(FileManagerIntent.Open(entry))
                                }
                            },
                            onLongClick = { store.dispatch(FileManagerIntent.ToggleSelect(entry)) },
                        )
                    }
                }
            }
        }

        if (state.selection.isNotEmpty()) {
            val selectedEntries = state.entries.filter { it.path in state.selection }
            SelectionBar(
                count = selectedEntries.size,
                canRename = selectedEntries.size == 1,
                onCopy = { store.dispatch(FileManagerIntent.SetClipboard(selectedEntries, cut = false)) },
                onCut = { store.dispatch(FileManagerIntent.SetClipboard(selectedEntries, cut = true)) },
                onRename = { renameTarget = selectedEntries.first() },
                onDelete = { pendingDelete = selectedEntries },
                onCancel = { store.dispatch(FileManagerIntent.ClearSelection) },
            )
        }
    }

    if (showNewFolderDialog) {
        NameInputDialog(
            title = "新建文件夹",
            initial = "",
            confirmLabel = "创建",
            onConfirm = { name ->
                store.dispatch(FileManagerIntent.CreateFolder(name))
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }

    renameTarget?.let { target ->
        NameInputDialog(
            title = "重命名",
            initial = target.name,
            confirmLabel = "确定",
            onConfirm = { name ->
                store.dispatch(FileManagerIntent.Rename(target, name))
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    pendingDelete?.let { targets ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除 ${targets.size} 项？") },
            text = { Text("删除后无法恢复，请确认。", style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    store.dispatch(FileManagerIntent.Delete(targets))
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

/** 还没拿到"所有文件访问权限"时显示的引导页。 */
@Composable
private fun PermissionGate(onRequestAccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = OnGlass, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(12.dp))
        Text(
            text = "需要“所有文件访问权限”才能浏览手机存储",
            color = OnGlass,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.size(16.dp))
        TextButton(onClick = onRequestAccess) { Text("去授权") }
    }
}

@Composable
private fun Toolbar(
    path: String,
    rootPath: String,
    canGoUp: Boolean,
    hasClipboard: Boolean,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onNewFolder: () -> Unit,
    onPaste: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUp, enabled = canGoUp, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "上一级", tint = if (canGoUp) OnGlass else OnGlass.copy(alpha = 0.3f))
        }
        Text(
            text = path.removePrefix(rootPath).ifEmpty { "/内部存储" },
            color = OnGlass,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        if (hasClipboard) {
            IconButton(onClick = onPaste, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ContentPaste, contentDescription = "粘贴", tint = OnGlass)
            }
        }
        IconButton(onClick = onNewFolder, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.CreateNewFolder, contentDescription = "新建文件夹", tint = OnGlass)
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = OnGlass)
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDirectory) Color(0xFF64B5F6) else OnGlass.copy(alpha = 0.8f),
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(entry.name, color = OnGlass, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                text = fileSubtitle(entry),
                color = OnGlass.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (selecting) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (selected) Color(0xFF64B5F6) else Color.Transparent, CircleShape),
            )
        }
    }
}

private fun fileSubtitle(entry: FileEntry): String {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entry.lastModified))
    if (entry.isDirectory) return date
    val size = formatSize(entry.sizeBytes)
    return "$size · $date"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
}

@Composable
private fun SelectionBar(
    count: Int,
    canRename: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("已选 $count 项", color = OnGlass, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "复制", tint = OnGlass) }
        IconButton(onClick = onCut) { Icon(Icons.Filled.ContentCut, contentDescription = "剪切", tint = OnGlass) }
        if (canRename) {
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "重命名", tint = OnGlass)
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "删除", tint = OnGlass) }
        TextButton(onClick = onCancel) { Text("取消") }
    }
}

@Composable
private fun NameInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
