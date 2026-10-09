package com.ios25pan.launcher.mvi

import android.content.Intent
import androidx.annotation.StringRes
import com.ios25pan.launcher.domain.Desktop
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.WidgetProvider

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
    data class SetWidgetPicker(val open: Boolean) : HomeIntent
    data class ProvidersLoaded(val providers: List<WidgetProvider>) : HomeIntent
    data class PickProvider(val provider: WidgetProvider) : HomeIntent
    data class WidgetBindResult(val appWidgetId: Int, val provider: WidgetProvider, val ok: Boolean) : HomeIntent
}

data class HomeState(
    val loading: Boolean = true,
    val desktop: Desktop = Desktop.EMPTY,
    val currentPage: Int = 0,
    val editing: Boolean = false,
    val openFolderId: String? = null,
    val controlCenterOpen: Boolean = false,
    val widgetPickerOpen: Boolean = false,
    val providers: List<WidgetProvider> = emptyList(),
)

sealed interface HomeEffect {
    data class Launch(val intent: Intent) : HomeEffect
    data class BindWidget(val intent: Intent, val appWidgetId: Int, val provider: WidgetProvider) : HomeEffect
    data class Toast(@StringRes val messageRes: Int) : HomeEffect
}
