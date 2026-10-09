package com.ios25pan.launcher.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.ios25pan.launcher.data.wallpaper.WallpaperRender
import com.ios25pan.launcher.domain.WidgetProvider
import com.ios25pan.launcher.mvi.HomeEffect
import com.ios25pan.launcher.mvi.HomeIntent
import com.ios25pan.launcher.mvi.HomeStore
import com.ios25pan.launcher.domain.GridSpec
import com.ios25pan.launcher.util.SafeMode

/** 壁纸跟随翻页做视差，位移量（dp）。只有 Launchpad 打开、翻分页网格的页时才会用到。 */
private val PARALLAX_SHIFT_DP = 40.dp

/**
 * 桌面主场景。
 *
 * 信息架构按 macOS 来分，不再是"铺满整屏的 App 网格"：
 *  - **Desktop（默认态）**：壁纸 + 顶部菜单栏 + 一片干净的桌面区域 + 正在运行的自由窗口条 + Dock。
 *  - **Launchpad（叠加态）**：点 Dock 第一个图标才会盖上来的全屏磨砂面板，装的是"全部应用"那套
 *    分页网格 / 文件夹 / 编辑态抖动删除——这部分逻辑和上一轮完全一样，只是换了个容器。
 *
 * UI 只负责渲染并把用户动作转成 Intent 交给 Store，不含任何业务逻辑。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    store: HomeStore = hiltViewModel(),
    onBindWidget: (Intent, Int, WidgetProvider) -> Unit,
    onRequestWallpaperPermission: () -> Unit,
    onPickCustomWallpaper: () -> Unit,
) {
    val state by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 降级开关：崩溃过一次就先不带模糊地跑，保证能回到桌面
    var blurDisabled by remember { mutableStateOf(SafeMode.blurDisabled()) }
    // 全屏只有一个模糊源（壁纸），所有玻璃面板共享它
    val hazeState = rememberHazeState(blurEnabled = !blurDisabled)
    val glassAlpha = state.glassOpacity

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
    val runningComponents = remember(state.openWindows) { state.openWindows.map { it.component }.toSet() }

    Box(modifier = Modifier.fillMaxSize()) {
        Wallpaper(
            render = state.wallpaper,
            hazeState = hazeState,
            pagerState = pagerState,
            parallaxPx = parallaxPx,
            launchpadOpen = state.launchpadOpen,
        )

        // ---- Desktop：壁纸之上常驻的那一层，菜单栏 + 空白桌面区域 + 运行中窗口 + Dock ----
        Column(modifier = Modifier.fillMaxSize()) {
            MenuBar(
                hazeState = hazeState,
                glassAlpha = glassAlpha,
                onOpenControlCenter = { store.dispatch(HomeIntent.SetControlCenter(true)) },
            )

            // 桌面本身留白：v1 里不放可拖拽的桌面图标（见 README「已知取舍」），
            // 这片区域就是纯壁纸，和 Launchpad 收起后的真实 macOS 桌面一样干净。
            Box(modifier = Modifier.weight(1f).fillMaxWidth())

            if (state.openWindows.isNotEmpty()) {
                WindowShelf(
                    windows = state.openWindows,
                    hazeState = hazeState,
                    glassAlpha = glassAlpha,
                    onFocus = { store.dispatch(HomeIntent.FocusWindow(it)) },
                    onClose = { store.dispatch(HomeIntent.CloseWindow(it)) },
                )
            }

            Dock(
                items = state.desktop.dock,
                runningComponents = runningComponents,
                store = store,
                hazeState = hazeState,
                glassAlpha = glassAlpha,
                onOpenLaunchpad = { store.dispatch(HomeIntent.SetLaunchpad(true)) },
                expanded = expanded,
            )
        }

        // ---- Launchpad：叠在桌面上的"全部应用"面板 ----
        Launchpad(
            visible = state.launchpadOpen,
            pages = pages,
            cols = state.desktop.grid.cols,
            rows = state.desktop.grid.rows,
            editing = state.editing,
            expanded = expanded,
            pagerState = pagerState,
            hazeState = hazeState,
            glassAlpha = glassAlpha,
            store = store,
            onOpenWidgetPicker = { store.dispatch(HomeIntent.SetWidgetPicker(true)) },
            onDismiss = { store.dispatch(HomeIntent.SetLaunchpad(false)) },
            modifier = Modifier.fillMaxSize(),
        )

        // 文件夹：记住最后打开的那个 id，这样退出动画播放期间还有内容可以画
        val folderId = state.openFolderId
        var lastFolderId by remember { mutableStateOf<String?>(null) }
        SideEffect { folderId?.let { lastFolderId = it } }

        FolderOverlay(
            visible = folderId != null,
            hazeState = hazeState,
            glassAlpha = glassAlpha,
            items = lastFolderId?.let { state.desktop.folders[it] }.orEmpty(),
            title = lastFolderId?.let { state.desktop.folderTitles[it] }.orEmpty(),
            store = store,
            onDismiss = { store.dispatch(HomeIntent.OpenFolder(null)) },
            onItemClick = { store.dispatch(HomeIntent.Tap(it)) },
        )

        ControlCenter(
            visible = state.controlCenterOpen,
            hazeState = hazeState,
            glassAlpha = glassAlpha,
            onGlassAlphaChange = { store.dispatch(HomeIntent.SetGlassOpacity(it)) },
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
            forceLandscape = state.forceLandscape,
            onForceLandscapeChange = { store.dispatch(HomeIntent.SetForceLandscape(it)) },
            onClose = { store.dispatch(HomeIntent.SetControlCenter(false)) },
        )

        if (state.widgetPickerOpen) {
            WidgetPicker(
                hazeState = hazeState,
                glassAlpha = glassAlpha,
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
 *
 * 视差只在 Launchpad 打开、用户翻页看分页网格时才做——Desktop 态没有"页"的概念，
 * 壁纸应该像真实 macOS 桌面一样纹丝不动。
 */
@Composable
private fun Wallpaper(
    render: WallpaperRender,
    hazeState: HazeState,
    pagerState: PagerState,
    parallaxPx: Float,
    launchpadOpen: Boolean,
) {
    val parallax = Modifier
        .fillMaxSize()
        // hazeSource 放在 graphicsLayer 之前：视差变换属于"源内容"的一部分，
        // 会被一起捕获进模糊，滑块时玻璃里的背景也跟着动
        .hazeSource(hazeState)
        .graphicsLayer {
            translationX = if (launchpadOpen) -pagerState.currentPageOffsetFraction * parallaxPx else 0f
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
    }
}
