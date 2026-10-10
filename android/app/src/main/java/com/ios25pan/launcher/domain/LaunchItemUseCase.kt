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

    /**
     * 打开（或者把已经开着的那个提到最前面）一个启动器自己实现的内置小程序
     * ——文件管理器 / 浏览器，见 [FloatingAppType]。这两个不走 Intent，
     * 不需要"有没有安装"这个问题，点一下必定成功。
     */
    data class OpenVirtualApp(val type: FloatingAppType) : LaunchResult
    data object NotInstalled : LaunchResult
    data object None : LaunchResult
}

/**
 * 点击桌面元素时：启动应用 / 打开网页 / 打开文件夹 / 打开内置小程序。
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

            is ItemAction.Role -> {
                // “文件”“浏览器”两个角色在本启动器里不是跳到别的 App，而是我们自己真正
                // 实现了界面的内置小程序——必须在走到下面通用的 Role 解析（会去 PackageManager
                // 里找外部 App）之前就拦下来，否则手机上随便装一个文件管理器/浏览器都会把这两个
                // 图标的行为重新劫持回“跳外部 App”。
                val virtualApp = virtualAppForRole(action.role)
                if (virtualApp != null) LaunchResult.OpenVirtualApp(virtualApp) else startExternal(action, item)
            }

            is ItemAction.Component -> startExternal(action, item)
            is ItemAction.Url -> LaunchResult.Start(apps.intentForUrl(action.url))
            else -> LaunchResult.None
        }
    }

    /** “role:files”“role:browser”对应启动器自己实现的内置小程序；其它角色都走外部 App。 */
    private fun virtualAppForRole(role: String): FloatingAppType? = when (role) {
        "files" -> FloatingAppType.FILES
        "browser" -> FloatingAppType.BROWSER
        else -> null
    }

    /** 外部 App 的通用启动路径：Role（除文件/浏览器外）和 Component 共用这一段逻辑。 */
    private suspend fun startExternal(action: ItemAction, item: DesktopItem): LaunchResult {
        val intent = apps.intentForAction(action) ?: return LaunchResult.NotInstalled
        val component = item.component
        if (component != null && prefs.windowMode.first() == WindowMode.FREEFORM) {
            if (windows.open(component, item.title)) return LaunchResult.Windowed
            return LaunchResult.Start(intent, windowFallbackReason = R.string.window_fallback)
        }
        return LaunchResult.Start(intent)
    }
}

