package com.ios25pan.launcher.ui.apps

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ios25pan.launcher.domain.VideoEntry
import com.ios25pan.launcher.domain.VideoSortOrder
import com.ios25pan.launcher.mvi.VideoIntent
import com.ios25pan.launcher.mvi.VideoStore
import com.ios25pan.launcher.ui.OnGlass

/**
 * === 这个文件是干什么的 ===
 *
 * "本地视频"小程序的界面。用户特意提到这个窗口要按"桌面 UI 布局"来画——因为平板屏幕
 * 大（16 寸），不应该照搬手机上那种"一条一条往下滑的单列列表"，而是仿照 macOS/Windows
 * 上视频库 App 的经典布局：**左侧一条相册/文件夹侧边栏 + 右侧网格缩略图**，拖大窗口时
 * 网格会自动多摆几列（[GridCells.Adaptive]），充分利用大屏幕的横向空间。
 *
 * 和浏览器（`BrowserApp.kt`）一样的分工：真正"能看见画面"的播放器
 * （`androidx.media3.ui.PlayerView`）是一个传统 Android View，不归 [VideoStore] 的
 * [com.ios25pan.launcher.mvi.VideoState] 管，只在这个文件里按需创建/销毁——原因同样是
 * "播放器这种又大又不该被随意复制比较的对象，不适合塞进不可变的 MVI State"。
 *
 * 缩略图通过 `VideoStore.thumbnailFor()` 异步生成（最终调用的是系统
 * `ContentResolver.loadThumbnail`，见 `data/video/VideoLibraryRepository.kt`），每张图
 * 用 `produceState` 懒加载，和 `ui/Widgets.kt` 里应用图标的加载方式是同一个套路。
 */
@Composable
fun VideoApp(onRequestAccess: () -> Unit, store: VideoStore = hiltViewModel()) {
    val state by store.state.collectAsStateWithLifecycle()

    // 窗口第一次出现、或者用户刚从权限弹窗里点了允许，都重新判断一次权限。
    LaunchedEffect(Unit) { store.dispatch(VideoIntent.RecheckAccess) }

    if (!state.hasAccess) {
        PermissionGate(
            message = "需要访问设备上的视频才能使用",
            onRequestAccess = onRequestAccess,
        )
        return
    }

    val playing = state.playing
    if (playing != null) {
        VideoPlayerScreen(entry = playing, onBack = { store.dispatch(VideoIntent.StopPlayback) })
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        VideoSidebar(
            buckets = state.buckets,
            selectedBucket = state.selectedBucket,
            totalCount = state.videos.size,
            onSelect = { store.dispatch(VideoIntent.SelectBucket(it)) },
        )
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            VideoToolbar(
                query = state.searchQuery,
                onQueryChange = { store.dispatch(VideoIntent.SetSearchQuery(it)) },
                sortOrder = state.sortOrder,
                onSortChange = { store.dispatch(VideoIntent.SetSortOrder(it)) },
                onRefresh = { store.dispatch(VideoIntent.Refresh) },
            )
            val visible = state.visibleVideos
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when {
                    state.loading && visible.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = OnGlass)

                    visible.isEmpty() -> Text(
                        text = if (state.searchQuery.isBlank()) "没有找到本地视频" else "没有匹配的视频",
                        color = OnGlass.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> LazyVerticalGrid(
                        // Adaptive：按可用宽度自动决定一行摆几列——窗口拖得越大，一行就摆得越多，
                        // 这正是"桌面 UI 布局"在大屏幕上该有的样子，而不是手机那种写死的列数。
                        columns = GridCells.Adaptive(minSize = 176.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visible, key = { it.id }) { entry ->
                            VideoCard(entry = entry, store = store, onClick = { store.dispatch(VideoIntent.Play(entry)) })
                        }
                    }
                }
            }
        }
    }
}

/** 还没拿到"读取视频"权限时的引导页，和 FileManagerApp 里那个是同一个思路。 */
@Composable
private fun PermissionGate(message: String, onRequestAccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = OnGlass, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(12.dp))
        Text(text = message, color = OnGlass, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(16.dp))
        TextButton(onClick = onRequestAccess) { Text("去授权") }
    }
}

/** 左侧侧边栏：全部视频 + 按相册/文件夹分组——"桌面 UI 布局"的标志性元素。 */
@Composable
private fun VideoSidebar(
    buckets: List<String>,
    selectedBucket: String?,
    totalCount: Int,
    onSelect: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(Color.White.copy(alpha = 0.04f))
            .padding(vertical = 12.dp),
    ) {
        SidebarRow(
            icon = Icons.Filled.VideoLibrary,
            label = "全部视频",
            count = totalCount,
            selected = selectedBucket == null,
            onClick = { onSelect(null) },
        )
        if (buckets.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = "相册",
                color = OnGlass.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            buckets.forEach { bucket ->
                SidebarRow(
                    icon = Icons.Filled.Folder,
                    label = bucket,
                    count = null,
                    selected = selectedBucket == bucket,
                    onClick = { onSelect(bucket) },
                )
            }
        }
    }
}

@Composable
private fun SidebarRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color(0xFF64B5F6) else OnGlass.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = OnGlass,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        if (count != null) {
            Text(count.toString(), color = OnGlass.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VideoToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    sortOrder: VideoSortOrder,
    onSortChange: (VideoSortOrder) -> Unit,
    onRefresh: () -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("搜索视频") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).height(44.dp),
        )
        Box {
            IconButton(onClick = { sortMenuOpen = true }) {
                Icon(Icons.Filled.Sort, contentDescription = "排序方式", tint = OnGlass)
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                sortLabel.forEach { (order, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSortChange(order)
                            sortMenuOpen = false
                        },
                        trailingIcon = { if (order == sortOrder) Text("✓") },
                    )
                }
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = OnGlass)
        }
    }
}

private val sortLabel = listOf(
    VideoSortOrder.DATE_NEWEST to "最近修改在前",
    VideoSortOrder.DATE_OLDEST to "最早修改在前",
    VideoSortOrder.NAME to "按名称",
    VideoSortOrder.SIZE_LARGEST to "按大小（大到小）",
)

/** 网格里的一张缩略图卡片：16:9 封面 + 时长角标 + 标题 + 大小/相册副标题。 */
@Composable
private fun VideoCard(entry: VideoEntry, store: VideoStore, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
        ) {
            val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = entry.id) {
                value = store.thumbnailFor(entry)?.asImageBitmap()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(Color.Black, RoundedCornerShape(10.dp)),
                )
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
            Text(
                text = formatDuration(entry.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Text(
            text = entry.title,
            color = OnGlass,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "${formatSize(entry.sizeBytes)} · ${entry.bucketName}",
            color = OnGlass.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** 播放界面：黑底 + 顶部返回条 + 铺满剩余空间的 PlayerView（自带播放/暂停/进度条控件）。 */
@Composable
private fun VideoPlayerScreen(entry: VideoEntry, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(entry.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(entry.uri)))
            playWhenReady = true
            prepare()
        }
    }

    // 退到后台（整个启动器被切走）就暂停，回来不自动续播——交给用户自己点播放，
    // 和大多数视频 App 的行为一致，避免"回到桌面声音还在放"的意外。
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Text(
                text = entry.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            onRelease = { it.player = null },
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
}
