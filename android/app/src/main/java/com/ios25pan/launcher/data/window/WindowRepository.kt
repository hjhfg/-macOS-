package com.ios25pan.launcher.data.window

import android.content.Context
import android.util.Log
import com.ios25pan.launcher.data.prefs.LauncherPrefs
import com.ios25pan.launcher.domain.AppWindow
import com.ios25pan.launcher.domain.WindowRect
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自由窗口的登记表。
 *
 * 这里只是**我们自己这边的记账**：实际的窗口由系统 WM Shell 画着，我们并不持有它、
 * 也没有它的句柄 —— 系统随时可能把某个窗口关掉而不通知我们（用户在最近任务里划掉它、
 * 应用自己 finish 了……）。所以这张表只用来驱动"桌面上显示哪些正在运行的窗口芯片"，
 * 不是真相来源；真做窗口管理（焦点、层叠）的权威始终是系统。
 */
@Singleton
class WindowRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: FreeformController,
    private val prefs: LauncherPrefs,
) {
    private val _windows = MutableStateFlow<List<AppWindow>>(emptyList())
    val windows: StateFlow<List<AppWindow>> = _windows.asStateFlow()

    val shellState: StateFlow<ShellState> get() = controller.shellState

    fun requestShizukuPermission(): Boolean = controller.requestPermission()

    suspend fun diagnostics(): String = runCatching { controller.diagnostics() }
        .getOrElse { "诊断失败：${it.message}" }

    /**
     * 打开（或带到前台）一个自由窗口。
     * @return 是否成功——调用方在失败时应该降级为普通全屏启动，不要让用户点了没反应。
     */
    suspend fun open(component: String, label: String): Boolean {
        val existing = _windows.value.firstOrNull { it.component == component }
        if (existing != null) return controller.launch(component, existing.rect)

        val saved = runCatching { prefs.windowBounds.first()[component] }.getOrNull()
            ?.let { WindowRect.parse(it) }
        val rect = saved ?: defaultRect(_windows.value.size)

        val ok = runCatching { controller.launch(component, rect) }
            .onFailure { Log.w(TAG, "打开自由窗口失败: $component", it) }
            .getOrDefault(false)
        if (ok) {
            _windows.update { it + AppWindow(component, label, rect, System.currentTimeMillis()) }
        }
        return ok
    }

    /** 移动 / 缩放一个已经开着的窗口，并记住这个位置供下次打开同一个 App 时复用。 */
    suspend fun moveResize(component: String, rect: WindowRect): Boolean {
        val ok = runCatching { controller.moveResize(component, rect) }.getOrDefault(false)
        if (ok) {
            _windows.update { list -> list.map { if (it.component == component) it.copy(rect = rect) else it } }
            runCatching { prefs.saveWindowBounds(component, rect.flatten()) }
        }
        return ok
    }

    suspend fun close(component: String) {
        runCatching { controller.close(component) }
        _windows.update { list -> list.filterNot { it.component == component } }
    }

    suspend fun closeAll() {
        runCatching { controller.closeAll(_windows.value.map { it.component }) }
        _windows.value = emptyList()
    }

    /** 已经开着：再发一次启动指令相当于把它带到前台，不改变记账。 */
    suspend fun focus(component: String): Boolean {
        val rect = _windows.value.firstOrNull { it.component == component }?.rect ?: return false
        return runCatching { controller.launch(component, rect) }.getOrDefault(false)
    }

    /** 首次打开时给一个居中、留出边距的默认大小；连续开几个窗口做级联偏移，不会完全叠在一起。 */
    private fun defaultRect(openCount: Int): WindowRect {
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val w = (screenW * DEFAULT_WIDTH_RATIO).toInt()
        val h = (screenH * DEFAULT_HEIGHT_RATIO).toInt()
        val cascade = (openCount % MAX_CASCADE) * CASCADE_STEP_PX
        val left = ((screenW - w) / 2 + cascade).coerceIn(0, (screenW - w).coerceAtLeast(0))
        val top = ((screenH - h) / 2 + cascade).coerceIn(0, (screenH - h).coerceAtLeast(0))
        return WindowRect(left, top, left + w, top + h)
    }

    private companion object {
        const val TAG = "WindowRepository"
        const val DEFAULT_WIDTH_RATIO = 0.62f
        const val DEFAULT_HEIGHT_RATIO = 0.68f
        const val MAX_CASCADE = 6
        const val CASCADE_STEP_PX = 56
    }
}
