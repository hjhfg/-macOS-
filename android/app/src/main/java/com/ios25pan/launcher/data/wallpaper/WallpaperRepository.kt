package com.ios25pan.launcher.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.ios25pan.launcher.data.prefs.LauncherPrefs
import com.ios25pan.launcher.domain.WALLPAPER_PRESETS
import com.ios25pan.launcher.domain.WallpaperMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 最终交给 UI 画的东西，解析出到底能不能被 Haze 模糊、要不要穿透到系统壁纸。 */
sealed interface WallpaperRender {
    /**
     * 窗口透明、不画任何东西，让系统合成的真实壁纸（含动态壁纸）直接透出来。
     *
     * 这是零权限路径：Activity 开了 FLAG_SHOW_WALLPAPER，系统就会把壁纸图层画在我们窗口后面。
     * 代价是 Haze **看不见**这一层——RenderEffect 模糊只能处理"我们自己窗口内已经画出来的内容"，
     * 系统合成的下层壁纸不属于我们的渲染树。所以这个分支下毛玻璃会自动退化成半透明材质。
     */
    data object Transparent : WallpaperRender

    /** 我们自己画的一张位图：系统壁纸快照，或用户挑的自定义图片。两者都能被模糊。 */
    data class Bitmap(val image: ImageBitmap) : WallpaperRender

    /** 内置预设（打包进 APK 的 drawable）。 */
    data class Preset(@DrawableRes val resId: Int) : WallpaperRender
}

/**
 * 壁纸来源仲裁。
 *
 * 默认策略（不需要用户做任何事，开箱即用地显示"原手机的壁纸"）：
 *  1. 当前是动态壁纸（[WallpaperManager.getWallpaperInfo] 非空）-> 没法截成静态位图，直接 [WallpaperRender.Transparent]
 *  2. 有读取权限 -> 截一张系统壁纸快照，[WallpaperRender.Bitmap]（可被模糊）
 *  3. 都不是 -> 还是 [WallpaperRender.Transparent]（零权限也能看到真实壁纸，只是没有模糊）
 *
 * 用户可以在控制中心主动切到预设或自定义图片，这两者都能被模糊，是"效果最好"的选项。
 */
@Singleton
class WallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: LauncherPrefs,
) {
    /** 运行时权限结果 / 壁纸被用户在系统设置里换掉之后，用这个触发重新判定。 */
    private val refreshTick = MutableStateFlow(0)

    fun notifyChanged() {
        refreshTick.value++
    }

    val render: Flow<WallpaperRender> =
        combine(
            prefs.wallpaperMode,
            prefs.wallpaperUri,
            prefs.wallpaperPreset,
            refreshTick,
        ) { mode, uri, preset, _ -> resolve(mode, uri, preset) }

    /** 是否有权限读取真实的壁纸位图（Android 13+ 要 READ_MEDIA_IMAGES，更早要 READ_EXTERNAL_STORAGE）。 */
    fun hasReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun isLiveWallpaperActive(): Boolean =
        runCatching { WallpaperManager.getInstance(context).wallpaperInfo != null }.getOrDefault(false)

    suspend fun setMode(mode: WallpaperMode) = prefs.setWallpaper(mode)

    suspend fun setPreset(name: String) = prefs.setWallpaper(WallpaperMode.PRESET, preset = name)

    /** 把用户从图库选的图片复制进私有目录（这样即使对方撤回了图库权限，壁纸也还在）。 */
    suspend fun setCustom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dest = File(context.filesDir, CUSTOM_WALLPAPER_FILE)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false
            prefs.setWallpaper(WallpaperMode.CUSTOM, uri = dest.absolutePath)
            true
        }.onFailure { Log.w(TAG, "保存自定义壁纸失败", it) }.getOrDefault(false)
    }

    private suspend fun resolve(mode: WallpaperMode, uri: String?, preset: String?): WallpaperRender =
        when (mode) {
            WallpaperMode.LIVE -> WallpaperRender.Transparent
            WallpaperMode.CUSTOM -> uri?.let { loadFile(it) } ?: WallpaperRender.Transparent
            WallpaperMode.PRESET -> presetResId(preset)?.let { WallpaperRender.Preset(it) } ?: WallpaperRender.Transparent
            WallpaperMode.SYSTEM -> resolveSystem()
        }

    private suspend fun resolveSystem(): WallpaperRender {
        if (isLiveWallpaperActive()) return WallpaperRender.Transparent
        if (!hasReadPermission()) return WallpaperRender.Transparent
        return loadSystemSnapshot() ?: WallpaperRender.Transparent
    }

    private suspend fun loadSystemSnapshot(): WallpaperRender.Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            // getDrawable 在部分 Android 13/14 设备上即便权限齐全也可能抛 SecurityException
            // （系统行为不一致），这里整段兜底，拿不到就让上层退回透明穿透，不崩。
            val drawable = WallpaperManager.getInstance(context).drawable ?: return@runCatching null
            val bitmap = drawable.toBitmap()
            WallpaperRender.Bitmap(bitmap.asImageBitmap())
        }.onFailure { Log.w(TAG, "读取系统壁纸失败，退回透明穿透", it) }.getOrNull()
    }

    private suspend fun loadFile(path: String): WallpaperRender.Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = android.graphics.BitmapFactory.decodeFile(path) ?: return@runCatching null
            WallpaperRender.Bitmap(bitmap.asImageBitmap())
        }.onFailure { Log.w(TAG, "读取自定义壁纸失败: $path", it) }.getOrNull()
    }

    private fun presetResId(name: String?): Int? {
        val resolved = name?.takeIf { it in WALLPAPER_PRESETS } ?: WALLPAPER_PRESETS.first()
        val id = context.resources.getIdentifier(resolved, "drawable", context.packageName)
        return id.takeIf { it != 0 }
    }

    private companion object {
        const val TAG = "WallpaperRepository"
        const val CUSTOM_WALLPAPER_FILE = "custom_wallpaper.jpg"
    }
}
