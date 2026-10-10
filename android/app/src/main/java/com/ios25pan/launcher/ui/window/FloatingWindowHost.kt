package com.ios25pan.launcher.ui.window

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.vector.ImageVector
import com.ios25pan.launcher.domain.FloatingAppType
import com.ios25pan.launcher.domain.FloatingWindowEntry
import com.ios25pan.launcher.ui.apps.BrowserApp
import com.ios25pan.launcher.ui.apps.FileManagerApp
import dev.chrisbanes.haze.HazeState

/**
 * === 这个文件是干什么的 ===
 *
 * [HomeState.floatingWindows][com.ios25pan.launcher.mvi.HomeState] 里有几个条目，
 * 这里就画几个 [FloatingWindow]——本身不关心拖拽/缩放这些交互细节（那是
 * `FloatingWindow.kt` 的事），只负责两件事：
 *  1. 按 [FloatingWindowEntry.type] 决定这个窗口该显示"文件管理器"还是"浏览器"的标题、
 *     图标和具体内容（[FileManagerApp] / [BrowserApp]）；
 *  2. 给每个新窗口算一个"看起来不完全重叠"的初始位置和大小（层叠打开，很像桌面系统
 *     新开窗口时互相错开一点的效果），具体数值在 [cascadeOffset]。
 *
 * 列表里**越靠后的条目层级越高**（这是 `HomeStore.focusById` 定下的约定——点一下某个
 * 窗口，会把它挪到列表末尾），所以这里直接用 `index` 当 `zIndex` 用。
 */
@Composable
fun FloatingWindowHost(
    windows: List<FloatingWindowEntry>,
    hazeState: HazeState,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onFocus: (String) -> Unit,
    onClose: (String) -> Unit,
    onRequestFilesAccess: () -> Unit,
) {
    windows.forEachIndexed { index, entry ->
        key(entry.id) {
            val (offsetX, offsetY) = cascadeOffset(index)
            val initialWidth = (screenWidthPx * 0.72f).coerceAtMost(screenWidthPx - 40f)
            val initialHeight = (screenHeightPx * 0.72f).coerceAtMost(screenHeightPx - 40f)
            FloatingWindow(
                title = titleFor(entry.type),
                icon = iconFor(entry.type),
                hazeState = hazeState,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                zIndex = index.toFloat(),
                initialXPx = offsetX.coerceIn(0f, (screenWidthPx - initialWidth).coerceAtLeast(0f)),
                initialYPx = offsetY.coerceIn(0f, (screenHeightPx - initialHeight).coerceAtLeast(0f)),
                initialWidthPx = initialWidth,
                initialHeightPx = initialHeight,
                onFocus = { onFocus(entry.id) },
                onClose = { onClose(entry.id) },
            ) {
                when (entry.type) {
                    FloatingAppType.FILES -> FileManagerApp(onRequestAccess = onRequestFilesAccess)
                    FloatingAppType.BROWSER -> BrowserApp()
                }
            }
        }
    }
}

private fun titleFor(type: FloatingAppType): String = when (type) {
    FloatingAppType.FILES -> "文件"
    FloatingAppType.BROWSER -> "浏览器"
}

private fun iconFor(type: FloatingAppType): ImageVector = when (type) {
    FloatingAppType.FILES -> Icons.Filled.Folder
    FloatingAppType.BROWSER -> Icons.Filled.Public
}

/** 第几个打开的窗口，相对屏幕左上角偏移多少像素——让连续打开的几个窗口不会完全叠在一起。 */
private fun cascadeOffset(index: Int): Pair<Float, Float> {
    val step = 48f
    val cycle = index % 6 // 错开 6 次之后循环，不会无限跑出屏幕
    return (120f + cycle * step) to (90f + cycle * step)
}
