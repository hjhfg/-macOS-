package com.ios25pan.launcher.domain

import android.content.Intent
import com.ios25pan.launcher.R
import com.ios25pan.launcher.data.apps.AppRepository
import com.ios25pan.launcher.data.prefs.LauncherPrefs
import com.ios25pan.launcher.data.window.WindowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface LaunchResult {
    /** 全屏启动。[windowFallbackReason] 非空说明本该开自由窗口但没能开成，UI 应该顺带提示一下。 */
    data class Start(val intent: Intent, val windowFallbackReason: Int? = null) : LaunchResult
    data class OpenFolder(val folderId: String) : LaunchResult

    /** 已经用自由窗口起来了：系统在画窗口，这边不需要再 startActivity。 */
    data object Windowed : LaunchResult
    data object NotInstalled : LaunchResult
    data object None : LaunchResult
}

/**
 * 点击桌面元素时：启动应用 / 打开网页 / 打开文件夹。
 *
 * 窗口模式是全局设置（控制中心切换），命中时优先尝试自由窗口；
 * 失败（没装 Shizuku、没授权、系统拒绝……）一律**静默降级为全屏启动**——
 * 点了图标必须有反应，不能因为自由窗口这个"加分项"失败就卡住。
 */
class LaunchItemUseCase @Inject constructor(
    private val apps: AppRepository,
    private val windows: WindowRepository,
    private val prefs: LauncherPrefs,
) {
    suspend operator fun invoke(item: DesktopItem): LaunchResult = withContext(Dispatchers.IO) {
        when (val action = ItemAction.parse(item.action)) {
            is ItemAction.FolderRef -> LaunchResult.OpenFolder(action.id)
            is ItemAction.Role, is ItemAction.Component -> {
                val intent = apps.intentForAction(action) ?: return@withContext LaunchResult.NotInstalled
                val component = item.component
                if (component != null && prefs.windowMode.first() == WindowMode.FREEFORM) {
                    if (windows.open(component, item.title)) return@withContext LaunchResult.Windowed
                    return@withContext LaunchResult.Start(intent, windowFallbackReason = R.string.window_fallback)
                }
                LaunchResult.Start(intent)
            }
            is ItemAction.Url -> LaunchResult.Start(apps.intentForUrl(action.url))
            else -> LaunchResult.None
        }
    }
}
