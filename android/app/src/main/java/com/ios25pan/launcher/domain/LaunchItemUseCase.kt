package com.ios25pan.launcher.domain

import android.content.Intent
import com.ios25pan.launcher.data.apps.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface LaunchResult {
    data class Start(val intent: Intent) : LaunchResult
    data class OpenFolder(val folderId: String) : LaunchResult
    data object NotInstalled : LaunchResult
    data object None : LaunchResult
}

/** 点击桌面元素时：启动应用 / 打开网页 / 打开文件夹。 */
class LaunchItemUseCase @Inject constructor(
    private val apps: AppRepository,
) {
    suspend operator fun invoke(item: DesktopItem): LaunchResult = withContext(Dispatchers.IO) {
        when (val action = ItemAction.parse(item.action)) {
            is ItemAction.FolderRef -> LaunchResult.OpenFolder(action.id)
            is ItemAction.Role, is ItemAction.Component ->
                apps.intentForAction(action)?.let { LaunchResult.Start(it) } ?: LaunchResult.NotInstalled
            is ItemAction.Url -> LaunchResult.Start(apps.intentForUrl(action.url))
            else -> LaunchResult.None
        }
    }
}
