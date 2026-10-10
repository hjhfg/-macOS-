package com.ios25pan.launcher.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import com.ios25pan.launcher.domain.GridSpec
import com.ios25pan.launcher.domain.WidgetProvider
import com.ios25pan.launcher.mvi.HomeEffect
import com.ios25pan.launcher.mvi.HomeIntent
import com.ios25pan.launcher.mvi.HomeStore
import com.ios25pan.launcher.ui.window.FloatingWindowHost
import com.ios25pan.launcher.util.SafeMode

/**
 * === 新手导览：这一屏（桌面）是怎么拼出来的 ===
 *
 * 这个文件只负责"把各个模块按正确的顺序叠在一起、把用户动作转发给 [HomeStore]"，
 * 本身**不画任何具体的 UI 细节**——每一块长什么样，去对应的文件里看：
 *
 * | 叠放顺序（从底到顶） | 负责的文件 | 这一块是什么 |
 * | --- | --- | --- |
 * | 1（最底层） | [Wallpaper.kt][com.ios25pan.launcher.ui] 里的 `Wallpaper()` | 桌面背景（图片/视频/系统壁纸透传） |
 * | 2 | `StatusBar.kt` 的 `StatusBar()` | 顶部一条：时间 + 控制中心/编辑完成按钮 |
 * | 2 | `WindowShelf.kt` 的 `WindowShelf()` | 正在以自由窗口打开的 App 列表（没有打开时不显示） |
 * | 2 | `Desktop.kt` 的 `Desktop()` | 中间：可横滑翻页的图标网格 + 翻页小圆点 |
 * | 2 | `Dock.kt` 的 `Dock()` | 底部一条：常驻的几个图标 |
 * | 2.5 | `ui/window/FloatingWindowHost.kt` 的 `FloatingWindowHost()` | 文件管理器/浏览器的浮动窗口 |
 * | 3 | `FolderOverlay.kt` 的 `FolderOverlay()` | 点开文件夹时盖上来的卡片 |
 * | 3 | `ControlCenter.kt` 的 `ControlCenter()` | 控制中心面板（亮度/音量/壁纸…） |
 * | 3（最上层） | `WidgetPicker.kt` 的 `WidgetPicker()` | 选小组件的弹窗 |
 *
 * 第 2 行的四块包在同一个 `Column` 里（StatusBar 在最上、Dock 在最下、中间是可伸缩的桌面网格），
 * 第 3 行的三个是"浮层"，各自用一个 `Boolean` 开关控制显示/隐藏，互相独立、可以同时存在。
 *
 * 这种写法叫 **MVI（Model-View-Intent）**：
 *  - **Model** 就是 [HomeStore] 手里的那份 `state`——整个桌面"现在长什么样"的唯一真相来源；
 *  - **View** 就是这个文件画出来的这些 Composable——它们只管"读 state 画出来"，不做判断逻辑；
 *  - **Intent** 就是像 `HomeIntent.Tap(item)` 这样的一个个"用户做了什么"的描述，
 *    丢给 `store.dispatch(...)` 之后，具体"应该怎么响应"的逻辑全部在 [HomeStore] 里，
 *    这个文件（以及它调用的所有子模块）完全不需要知道。
 *
 * 好处：以后要查"点击图标为什么没反应"，只需要去 `HomeStore.kt` 看 `HomeIntent.Tap` 怎么处理，
 * 不用在这一堆 UI 代码里到处翻找业务逻辑。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    store: HomeStore = hiltViewModel(),
    onBindWidget: (Intent, Int, WidgetProvider) -> Unit,
    onRequestWallpaperPermission: () -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onPickVideoWallpaper: () -> Unit,
    onRequestFilesAccess: () -> Unit,
) {
    // collectAsStateWithLifecycle：订阅 HomeStore 的状态流，且在 App 退到后台时自动暂停订阅，
    // 回到前台再恢复——比裸的 collectAsState 更省电，是 Google 官方推荐的标准写法。
    val state by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 降级开关：如果毛玻璃模糊曾经导致过崩溃（见 SafeMode 的说明），这里就先不带模糊跑一次，
    // 保证用户至少能回到桌面，而不是陷入"一开App就崩溃"的死循环。
    var blurDisabled by remember { mutableStateOf(SafeMode.blurDisabled()) }
    // 全屏共用同一个模糊"取景框"：壁纸只需要被截一次屏，Dock/控制中心/文件夹面板都从这一份
    // 截图里各自裁一块出来模糊，而不是每个面板各截一次（那样性能会差很多）。
    val hazeState = rememberHazeState(blurEnabled = !blurDisabled)

    // 平板横屏下可用面积大得多：按实际 dp 重新算网格列数，而不是手机那套写死的 4x6。
    // LocalConfiguration 在屏幕旋转/折叠屏展开时会自动触发这里重新执行，不需要额外注册监听器。
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.screenWidthDp, configuration.screenHeightDp) {
        store.dispatch(HomeIntent.ScreenSizeChanged(configuration.screenWidthDp, configuration.screenHeightDp))
    }
    val expanded = GridSpec.isExpanded(configuration.screenWidthDp)

    // 一次性副作用（不是"状态"，是"动作"）：启动别的 App、弹 Toast、跳到小组件绑定页。
    // 之所以要单独用 effects 这条通道、而不是塞进 state 里，是因为这些事情"做一次就完事了"，
    // 不应该在屏幕旋转、重组之类的场合被重复触发。
    LaunchedEffect(Unit) {
        store.effects.collect { effect ->
            when (effect) {
                is HomeEffect.Launch -> runCatching { context.startActivity(effect.intent) }
                is HomeEffect.Toast -> Toast.makeText(context, effect.messageRes, Toast.LENGTH_SHORT).show()
                is HomeEffect.BindWidget -> onBindWidget(effect.intent, effect.appWidgetId, effect.provider)
            }
        }
    }

    // 桌面有几页，由 HomeStore 算好放在 state.desktop.pages 里；pagerState 是"翻页器"自己的状态
    // （当前翻到第几页、手指滑到一半的偏移量等），创建在这里是因为下面的 Wallpaper() 做视差效果
    // 也需要读它，两边必须共享同一个实例。
    val pages = state.desktop.pages
    val pagerState = rememberPagerState(pageCount = { pages.size })

    // 两个方向都要同步：用户手指滑动 -> 告诉 Store "翻到第几页了"；
    // Store 的页码被别处改动（比如删除图标导致页数变化）-> 让翻页器自己滚过去。
    LaunchedEffect(pagerState.currentPage) {
        if (pages.isNotEmpty()) store.dispatch(HomeIntent.PageChanged(pagerState.currentPage))
    }
    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(state.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
        }
    }

    // dp 转像素：graphicsLayer 系列 API 只认像素，不认 dp，这个换算只需要做一次。
    // PARALLAX_SHIFT_DP 定义在 Wallpaper.kt 里（同一个包，不需要 import）。
    val density = LocalDensity.current
    val parallaxPx = with(density) { PARALLAX_SHIFT_DP.toPx() }
    // 浮动窗口（文件管理器/浏览器）的拖拽缩放全程用像素运算，这里把屏幕宽高也换算成像素，
    // 传给 FloatingWindowHost 做"不能被拖出屏幕"的边界判断，见 ui/window/FloatingWindow.kt。
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 第 1 层：壁纸（见 Wallpaper.kt）
        Wallpaper(render = state.wallpaper, hazeState = hazeState, pagerState = pagerState, parallaxPx = parallaxPx)

        // 第 2 层：状态栏 + 自由窗口条 + 桌面网格 + Dock，自上而下排成一列
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

            Desktop(
                pages = pages,
                grid = state.desktop.grid,
                editing = state.editing,
                expanded = expanded,
                pagerState = pagerState,
                store = store,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )

            Dock(items = state.desktop.dock, store = store, hazeState = hazeState, expanded = expanded)
        }

        // 第 2.5 层：内置小程序（文件管理器/浏览器）的浮动窗口——盖在桌面/Dock 之上，
        // 但盖在下面第 3 层的文件夹卡片/控制中心/小组件选择器之下（那几个算系统级浮层，
        // 优先级更高，和真实 macOS 里"控制中心永远盖在普通 App 窗口上面"是一个道理）。
        FloatingWindowHost(
            windows = state.floatingWindows,
            hazeState = hazeState,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onFocus = { store.dispatch(HomeIntent.FocusFloatingWindow(it)) },
            onClose = { store.dispatch(HomeIntent.CloseFloatingWindow(it)) },
            onRequestFilesAccess = onRequestFilesAccess,
        )

        // 第 3 层：三个互相独立的浮层。文件夹这里额外记了"最后打开的是哪个文件夹"，
        // 是因为退出动画播放期间 openFolderId 已经变回 null 了，但画面上还得继续显示
        // 关闭前那个文件夹的内容，不能突然变成空白。
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
