package com.ios25pan.launcher.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.ios25pan.launcher.R
import com.ios25pan.launcher.data.wallpaper.WallpaperRender
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.GridSpec
import com.ios25pan.launcher.domain.ItemType
import com.ios25pan.launcher.domain.WidgetProvider
import com.ios25pan.launcher.mvi.HomeEffect
import com.ios25pan.launcher.mvi.HomeIntent
import com.ios25pan.launcher.mvi.HomeStore
import com.ios25pan.launcher.util.SafeMode
import kotlin.math.abs

/** 壁纸跟随翻页做视差，位移量（dp）。 */
private val PARALLAX_SHIFT_DP = 40.dp

/**
 * 桌面主页：壁纸 + 可横滑的页面（HorizontalPager）+ Dock + 覆盖层（文件夹 / 控制中心 / 小组件选择器）。
 * UI 只负责渲染并把用户动作转成 Intent 交给 Store，不含任何业务逻辑。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    store: HomeStore = hiltViewModel(),
    onBindWidget: (Intent, Int, WidgetProvider) -> Unit,
    onRequestWallpaperPermission: () -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onPickVideoWallpaper: () -> Unit,
) {
    val state by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 降级开关：崩溃过一次就先不带模糊地跑，保证能回到桌面
    var blurDisabled by remember { mutableStateOf(SafeMode.blurDisabled()) }
    // 全屏只有一个模糊源（壁纸），所有玻璃面板共享它
    val hazeState = rememberHazeState(blurEnabled = !blurDisabled)

    // 平板横屏下可用面积大得多：按实际 dp 重新算网格列数，而不是手机那套写死的 4x6。
    // LocalConfiguration 在旋转 / 折叠展开时会自己触发重组，不需要额外监听器。
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.screenWidthDp, configuration.screenHeightDp) {
        store.dispatch(HomeIntent.ScreenSizeChanged(configuration.screenWidthDp, configuration.screenHeightDp))
    }
    val expanded = GridSpec.isExpanded(configuration.screenWidthDp)

    LaunchedEffect(Unit) {
        store.effects.collect { effect ->
            when (effect) {
                is HomeEffect.Launch -> runCatching { context.startActivity(effect.intent) }
                is HomeEffect.Toast -> Toast.makeText(context, effect.messageRes, Toast.LENGTH_SHORT).show()
                is HomeEffect.BindWidget -> onBindWidget(effect.intent, effect.appWidgetId, effect.provider)
            }
        }
    }

    val pages = state.desktop.pages
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pagerState.currentPage) {
        if (pages.isNotEmpty()) store.dispatch(HomeIntent.PageChanged(pagerState.currentPage))
    }
    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(state.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
        }
    }

    val parallaxPx = with(LocalDensity.current) { PARALLAX_SHIFT_DP.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Wallpaper(render = state.wallpaper, hazeState = hazeState, pagerState = pagerState, parallaxPx = parallaxPx)

        Column(modifier = Modifier.fillMaxSize()) {
            StatusBar(
                editing = state.editing,
                onOpenControlCenter = { store.dispatch(HomeIntent.SetControlCenter(true)) },
                onOpenWidgetPicker = { store.dispatch(HomeIntent.SetWidgetPicker(true)) },
                onExitEdit = { store.dispatch(HomeIntent.ExitEdit) },
            )

            if (state.openWindows.isNotEmpty()) {
                WindowShelf(
                    windows = state.openWindows,
                    hazeState = hazeState,
                    onFocus = { store.dispatch(HomeIntent.FocusWindow(it)) },
                    onClose = { store.dispatch(HomeIntent.CloseWindow(it)) },
                )
            }

            HorizontalPager(
                state = pagerState,
                // 预组合相邻页：滑动时不会现场组合，代价是多留一页的 AndroidView 在内存里
                beyondBoundsPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { index ->
                val page = pages.getOrNull(index)
                if (page != null) {
                    DesktopGrid(slots = page.slots, cols = state.desktop.grid.cols, rows = state.desktop.grid.rows) { slot ->
                        DesktopCell(slot.item, state.editing, store, expanded)
                    }
                }
            }

            PageIndicator(count = pages.size, current = pagerState.currentPage)
            Dock(items = state.desktop.dock, store = store, hazeState = hazeState, expanded = expanded)
        }

        // 文件夹：记住最后打开的那个 id，这样退出动画播放期间还有内容可以画
        val folderId = state.openFolderId
        var lastFolderId by remember { mutableStateOf<String?>(null) }
        SideEffect { folderId?.let { lastFolderId = it } }

        FolderOverlay(
            visible = folderId != null,
            items = lastFolderId?.let { state.desktop.folders[it] }.orEmpty(),
            title = lastFolderId?.let { state.desktop.folderTitles[it] }.orEmpty(),
            store = store,
            onDismiss = { store.dispatch(HomeIntent.OpenFolder(null)) },
            onItemClick = { store.dispatch(HomeIntent.Tap(it)) },
        )

        ControlCenter(
            visible = state.controlCenterOpen,
            hazeState = hazeState,
            blurDisabled = blurDisabled,
            onBlurDisabledChange = { disabled ->
                blurDisabled = disabled
                SafeMode.setBlurDisabled(disabled)
            },
            windowMode = state.windowMode,
            shellState = state.shellState,
            onWindowModeChange = { store.dispatch(HomeIntent.SetWindowMode(it)) },
            onRequestShizuku = { store.requestShizukuPermission() },
            onDiagnoseWindow = { store.windowDiagnostics() },
            wallpaper = state.wallpaper,
            onWallpaperModeChange = { store.dispatch(HomeIntent.SetWallpaperMode(it)) },
            onWallpaperPresetChange = { store.dispatch(HomeIntent.SetWallpaperPreset(it)) },
            onRequestWallpaperPermission = onRequestWallpaperPermission,
            onPickCustomWallpaper = onPickCustomWallpaper,
            onPickVideoWallpaper = onPickVideoWallpaper,
            forceLandscape = state.forceLandscape,
            onForceLandscapeChange = { store.dispatch(HomeIntent.SetForceLandscape(it)) },
            onClose = { store.dispatch(HomeIntent.SetControlCenter(false)) },
        )

        if (state.widgetPickerOpen) {
            WidgetPicker(
                hazeState = hazeState,
                providers = state.providers,
                onPick = { store.dispatch(HomeIntent.PickProvider(it)) },
                onDismiss = { store.dispatch(HomeIntent.SetWidgetPicker(false)) },
            )
        }
    }
}

/**
 * 壁纸层：按 [WallpaperRender] 三选一地画。
 *
 * 三条分支都要挂 [hazeSource]——即便是 [WallpaperRender.Transparent] 这种"什么都不画"的情况也一样，
 * 否则 Haze 找不到源内容，面板会整体退化成不透明，而不是我们想要的"半透明但不模糊"。
 */
@Composable
private fun Wallpaper(
    render: WallpaperRender,
    hazeState: HazeState,
    pagerState: PagerState,
    parallaxPx: Float,
) {
    val parallax = Modifier
        .fillMaxSize()
        // hazeSource 放在 graphicsLayer 之前：视差变换属于"源内容"的一部分，
        // 会被一起捕获进模糊，滑块时玻璃里的背景也跟着动
        .hazeSource(hazeState)
        .graphicsLayer {
            translationX = -pagerState.currentPageOffsetFraction * parallaxPx
            // 稍微放大，位移时才不会露出边缘
            scaleX = 1.08f
            scaleY = 1.08f
        }

    when (render) {
        WallpaperRender.Transparent -> Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState))
        is WallpaperRender.Bitmap -> Image(
            bitmap = render.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = parallax,
        )
        is WallpaperRender.Preset -> Image(
            painter = painterResource(render.resId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = parallax,
        )
        is WallpaperRender.Video -> VideoWallpaper(path = render.path, modifier = parallax)
    }
}

/**
 * 视频壁纸：循环静音播放一个本地文件。
 *
 * 关键是播放面用的 surface 类型必须是 TextureView，不能是默认的 SurfaceView——
 * SurfaceView 画在独立的硬件图层上，走系统合成器单独叠加，不经过我们这棵 View/Compose 树
 * 的正常绘制流程，Haze（以及任何基于 RenderEffect/RenderNode 快照的模糊方案）看到的就是
 * 一个"空洞"。TextureView 则是把每一帧解码成纹理贴到普通 View 的绘制管线里，和画一张
 * Bitmap 没有本质区别，可以被正常截帧、模糊。代价是比 SurfaceView 多一次 GPU 拷贝，
 * 对手机/平板这种规格的播放画面（不是 4K）可以接受。
 *
 * `PlayerView` 的 surface 类型只能在 XML inflate 时通过 `app:surface_type` 定，
 * 没有运行时 setter，所以下面没有直接 `PlayerView(ctx)`，而是 inflate
 * `res/layout/video_wallpaper_player.xml`。
 */
@Composable
private fun VideoWallpaper(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(path))))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f // 壁纸不出声，和系统动态壁纸的默认行为一致
            playWhenReady = true
            prepare()
        }
    }

    // 退到后台就暂停、回到前台再续播——视频解码一直跑是实打实的电量和发热成本，
    // 用户已经看不到桌面了没有理由继续解码。
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // surface_type（texture_view）只能在布局 inflate 的时候定，PlayerView 没有运行时
            // setter——所以这里用一个小 XML 布局，而不是直接 new PlayerView(ctx)。
            val view = android.view.LayoutInflater.from(ctx)
                .inflate(R.layout.video_wallpaper_player, null) as PlayerView
            view.player = player
            view
        },
        onRelease = { it.player = null },
    )
}

@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            val size by animateDpAsState(
                targetValue = if (i == current) 8.dp else 6.dp,
                animationSpec = Motion.springyDp,
                label = "dot",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(size)
                    .clip(CircleShape)
                    .background(if (i == current) Color.White else Color.White.copy(alpha = 0.4f)),
            )
        }
    }
}

@Composable
private fun DesktopCell(item: DesktopItem, editing: Boolean, store: HomeStore, expanded: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = Motion.snappyFloat,
        label = "press",
    )

    // 编辑态的抖动：给每个图标一个不同的周期，避免整齐划一地"齐步走"
    val wiggle = if (editing) {
        val transition = rememberInfiniteTransition(label = "wiggle")
        transition.animateFloat(
            initialValue = -1.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 150 + abs(item.id.hashCode() % 6) * 22,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wiggle",
        )
    } else {
        null
    }

    Box(modifier = Modifier.fillMaxSize().padding(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 三个值都是延迟读取：按下、抖动、都不触发重组，只重绘图层
                    scaleX = pressScale
                    scaleY = pressScale
                    rotationZ = wiggle?.value ?: 0f
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null, // 用缩放做反馈，不画水波纹（更接近 iOS，也少一层绘制）
                    onClick = { store.dispatch(HomeIntent.Tap(item)) },
                    onLongClick = { store.dispatch(HomeIntent.LongPress(item)) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (item.type == ItemType.WIDGET) {
                WidgetHost(item = item, store = store, modifier = Modifier.fillMaxSize())
            } else {
                IconLabel(label = item.title, iconSize = GridSpec.iconPlateSize(expanded)) {
                    ItemIcon(item = item, store = store, modifier = Modifier.fillMaxSize(0.8f))
                }
            }
        }

        AnimatedVisibility(
            visible = editing && item.type != ItemType.DIVIDER,
            enter = scaleIn(animationSpec = Motion.panelScale, initialScale = 0.4f) + fadeIn(),
            exit = scaleOut(animationSpec = Motion.panelScale, targetScale = 0.4f) + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            IconButton(
                onClick = { store.dispatch(HomeIntent.Remove(item)) },
                modifier = Modifier.size(34.dp), // 保证可点区域够大
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color(0xAA000000), CircleShape),
                )
            }
        }
    }
}
