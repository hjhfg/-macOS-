package com.ios25pan.launcher.mvi

import android.content.Intent
import androidx.annotation.StringRes
import com.ios25pan.launcher.data.wallpaper.WallpaperRender
import com.ios25pan.launcher.data.window.ShellState
import com.ios25pan.launcher.domain.AppWindow
import com.ios25pan.launcher.domain.Desktop
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.WallpaperMode
import com.ios25pan.launcher.domain.WidgetProvider
import com.ios25pan.launcher.domain.WindowMode

/**
 * MVI 三件套：
 *  - HomeIntent：UI 发出的意图 + 异步结果（Loaded / ProvidersLoaded / WidgetBindResult）
 *  - HomeState：唯一的真相来源，Compose 只读它
 *  - HomeEffect：一次性副作用（启动 Activity、弹 Toast、请求绑定小组件）
 */
sealed interface HomeIntent {
    data class Loaded(val desktop: Desktop) : HomeIntent
    data class PageChanged(val index: Int) : HomeIntent
    data class Tap(val item: DesktopItem) : HomeIntent
    data class LongPress(val item: DesktopItem) : HomeIntent
    data object ExitEdit : HomeIntent
    data class Remove(val item: DesktopItem) : HomeIntent
    data class OpenFolder(val folderId: String?) : HomeIntent
    data class SetControlCenter(val open: Boolean) : HomeIntent

    /** Launchpad：macOS 意义上的"全部应用"全屏面板，取代了旧版那种铺满整个桌面的分页网格。 */
    data class SetLaunchpad(val open: Boolean) : HomeIntent
    data class SetWidgetPicker(val open: Boolean) : HomeIntent
    data class ProvidersLoaded(val providers: List<WidgetProvider>) : HomeIntent
    data class PickProvider(val provider: WidgetProvider) : HomeIntent
    data class WidgetBindResult(val appWidgetId: Int, val provider: WidgetProvider, val ok: Boolean) : HomeIntent

    /** 屏幕可用尺寸变化（旋转 / 折叠展开）：驱动网格列数重新计算，平板横屏才能摆下更多图标。 */
    data class ScreenSizeChanged(val widthDp: Int, val heightDp: Int) : HomeIntent

    // ---- 窗口 ----
    data class SetWindowMode(val mode: WindowMode) : HomeIntent
    data class CloseWindow(val component: String) : HomeIntent
    data class FocusWindow(val component: String) : HomeIntent

    // ---- 壁纸 / 外观 ----
    data class SetWallpaperMode(val mode: WallpaperMode) : HomeIntent
    data class SetWallpaperPreset(val name: String) : HomeIntent
    data class PickedCustomWallpaper(val uri: String) : HomeIntent
    data object RefreshWallpaper : HomeIntent
    data class SetForceLandscape(val value: Boolean) : HomeIntent

    /** Liquid Glass 全局透明度：0（几乎看穿）到 1（接近不透明），对应 macOS 27 的 "Ultra Clear ↔ Tinted Glass" 滑块。 */
    data class SetGlassOpacity(val value: Float) : HomeIntent
}

data class HomeState(
    val loading: Boolean = true,
    val desktop: Desktop = Desktop.EMPTY,
    val currentPage: Int = 0,
    val editing: Boolean = false,
    val openFolderId: String? = null,
    val controlCenterOpen: Boolean = false,
    /** Launchpad（原来挤在桌面上的全部应用分页网格）是否展开成全屏面板。 */
    val launchpadOpen: Boolean = false,
    val widgetPickerOpen: Boolean = false,
    val providers: List<WidgetProvider> = emptyList(),
    val windowMode: WindowMode = WindowMode.FULLSCREEN,
    val openWindows: List<AppWindow> = emptyList(),
    val shellState: ShellState = ShellState.NOT_RUNNING,
    val wallpaper: WallpaperRender = WallpaperRender.Transparent,
    val forceLandscape: Boolean = true,
    val glassOpacity: Float = 1f,
)

sealed interface HomeEffect {
    data class Launch(val intent: Intent) : HomeEffect
    data class BindWidget(val intent: Intent, val appWidgetId: Int, val provider: WidgetProvider) : HomeEffect
    data class Toast(@StringRes val messageRes: Int) : HomeEffect
}
