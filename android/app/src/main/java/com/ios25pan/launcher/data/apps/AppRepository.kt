package com.ios25pan.launcher.data.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.ios25pan.launcher.domain.AppSnapshot
import com.ios25pan.launcher.domain.ItemAction
import com.ios25pan.launcher.domain.LaunchableApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PackageManager 访问层。
 *
 * 网页里的"系统应用"没有固定包名，这里用 Intent 角色（拨号、邮件、相机……）
 * 去系统里解析真正的应用 —— 这是 Launcher 的标准做法，也是为什么 QUERY_ALL_PACKAGES 必不可少。
 */
@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pm: PackageManager = context.packageManager
    private val ownPackage = context.packageName
    private val iconCache = LruCache<String, ImageBitmap>(160)

    suspend fun snapshot(): AppSnapshot = withContext(Dispatchers.IO) {
        val apps = launchableApps()
        val roles = ROLES.associateWith { resolveRole(it)?.flattenToString() }
        AppSnapshot(apps, roles)
    }

    /** 包安装 / 卸载 / 变更时发出一次信号。 */
    fun packageChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    /** 同步取缓存里的图标（没有返回 null）。UI 用它做初始值，翻页回来时不会闪。 */
    fun cachedIcon(flat: String): ImageBitmap? = iconCache.get(flat)

    suspend fun icon(flat: String): ImageBitmap? {
        iconCache.get(flat)?.let { return it }
        return withContext(Dispatchers.IO) {
            val cn = ComponentName.unflattenFromString(flat) ?: return@withContext null
            val drawable = runCatching { pm.getActivityIcon(cn) }.getOrNull() ?: return@withContext null
            drawable.toBitmap(128, 128).asImageBitmap().also { iconCache.put(flat, it) }
        }
    }

    /** 为 Intent 角色生成可直接启动的 Intent（已绑定到解析出的组件）。 */
    fun intentForRole(role: String): Intent? {
        val base = roleIntent(role) ?: return null
        val cn = resolveRole(role) ?: return null
        return Intent(base).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun intentForComponent(flat: String): Intent? {
        val cn = ComponentName.unflattenFromString(flat) ?: return null
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun intentForUrl(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun intentForAction(action: ItemAction): Intent? = when (action) {
        is ItemAction.Role -> intentForRole(action.role)
        is ItemAction.Component -> intentForComponent(action.flat)
        is ItemAction.Url -> intentForUrl(action.url)
        else -> null
    }

    // ---- 内部 ----

    private fun launchableApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return queryActivities(intent)
            .asSequence()
            .filter { it.activityInfo.packageName != ownPackage }
            .map { ri ->
                val ai = ri.activityInfo
                LaunchableApp(
                    component = ComponentName(ai.packageName, ai.name).flattenToString(),
                    label = ri.loadLabel(pm).toString(),
                    packageName = ai.packageName,
                )
            }
            .distinctBy { it.component }
            .toList()
    }

    private fun resolveRole(role: String): ComponentName? {
        val base = roleIntent(role) ?: return null
        val info = queryActivities(base).firstOrNull { it.activityInfo.packageName != ownPackage }
            ?: return null
        return ComponentName(info.activityInfo.packageName, info.activityInfo.name)
    }

    @Suppress("DEPRECATION")
    private fun queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }

    companion object {
        /** 与 desktop_seed.json 中 action 的 role:xxx 一一对应。 */
        val ROLES = listOf(
            "phone", "camera", "music", "settings", "gallery", "mail", "calendar", "maps",
            "messages", "browser", "market", "contacts", "files", "calculator", "recorder", "wallpaper",
        )

        fun roleIntent(role: String): Intent? = when (role) {
            "phone" -> Intent(Intent.ACTION_DIAL)
            "camera" -> Intent("android.media.action.STILL_IMAGE_CAMERA")
            "music" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            "gallery" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
            "mail" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL)
            "calendar" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            "maps" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
            "messages" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
            "browser" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
            "market" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET)
            "contacts" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS)
            "files" -> Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_FILES")
            "calculator" -> Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CALCULATOR")
            "recorder" -> Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
            "wallpaper" -> Intent(Intent.ACTION_SET_WALLPAPER)
            "clock" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            else -> null
        }
    }
}
