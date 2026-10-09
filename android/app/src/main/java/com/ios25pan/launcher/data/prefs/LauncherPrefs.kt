package com.ios25pan.launcher.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ios25pan.launcher.domain.WallpaperMode
import com.ios25pan.launcher.domain.WindowMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

/**
 * 设置项（DataStore）。
 *
 * 存的东西分三类：
 *  - 桌面内容：用户从桌面移除过的元素（避免被"自动补齐"又出现）
 *  - 外观：壁纸模式 / 壁纸 URI / 是否强制横屏 / 网格列数
 *  - 窗口：点击图标时是全屏还是自由窗口，以及每个窗口上次的位置（重开时恢复）
 */
@Singleton
class LauncherPrefs @Inject constructor(@ApplicationContext private val context: Context) {

    private val hiddenKey = stringSetPreferencesKey("hidden_actions")
    private val wallpaperModeKey = stringPreferencesKey("wallpaper_mode")
    private val wallpaperUriKey = stringPreferencesKey("wallpaper_uri")
    private val wallpaperPresetKey = stringPreferencesKey("wallpaper_preset")
    private val forceLandscapeKey = booleanPreferencesKey("force_landscape")
    private val gridColsKey = intPreferencesKey("grid_cols")
    private val gridRowsKey = intPreferencesKey("grid_rows")
    private val windowModeKey = stringPreferencesKey("window_mode")
    private val windowBoundsKey = stringSetPreferencesKey("window_bounds")

    val hiddenActions: Flow<Set<String>> =
        context.launcherDataStore.data.map { it[hiddenKey] ?: emptySet() }

    suspend fun hide(action: String) {
        context.launcherDataStore.edit { p -> p[hiddenKey] = (p[hiddenKey] ?: emptySet()) + action }
    }

    // ---- 外观 ----

    val wallpaperMode: Flow<WallpaperMode> = context.launcherDataStore.data.map {
        runCatching { WallpaperMode.valueOf(it[wallpaperModeKey] ?: "") }.getOrDefault(WallpaperMode.SYSTEM)
    }

    val wallpaperUri: Flow<String?> = context.launcherDataStore.data.map { it[wallpaperUriKey] }

    val wallpaperPreset: Flow<String?> = context.launcherDataStore.data.map { it[wallpaperPresetKey] }

    suspend fun setWallpaper(mode: WallpaperMode, uri: String? = null, preset: String? = null) {
        context.launcherDataStore.edit { p ->
            p[wallpaperModeKey] = mode.name
            if (uri != null) p[wallpaperUriKey] = uri
            if (preset != null) p[wallpaperPresetKey] = preset
        }
    }

    /** 平板桌面按横屏设计；关掉后就跟着设备传感器走。 */
    val forceLandscape: Flow<Boolean> =
        context.launcherDataStore.data.map { it[forceLandscapeKey] ?: true }

    suspend fun setForceLandscape(value: Boolean) {
        context.launcherDataStore.edit { it[forceLandscapeKey] = value }
    }

    /** 0 表示跟随屏幕尺寸自动算（见 GridSpec）。 */
    val gridOverride: Flow<Pair<Int, Int>> = context.launcherDataStore.data.map {
        it[gridColsKey] ?: 0 to (it[gridRowsKey] ?: 0)
    }

    suspend fun setGridOverride(cols: Int, rows: Int) {
        context.launcherDataStore.edit {
            it[gridColsKey] = cols
            it[gridRowsKey] = rows
        }
    }

    // ---- 窗口 ----

    val windowMode: Flow<WindowMode> = context.launcherDataStore.data.map {
        runCatching { WindowMode.valueOf(it[windowModeKey] ?: "") }.getOrDefault(WindowMode.FULLSCREEN)
    }

    suspend fun setWindowMode(mode: WindowMode) {
        context.launcherDataStore.edit { it[windowModeKey] = mode.name }
    }

    /** 组件名 -> 上次窗口位置（"left top right bottom"），重开同一个应用时恢复。 */
    val windowBounds: Flow<Map<String, String>> = context.launcherDataStore.data.map { prefs ->
        (prefs[windowBoundsKey] ?: emptySet()).mapNotNull { entry ->
            val i = entry.indexOf('=')
            if (i <= 0) null else entry.substring(0, i) to entry.substring(i + 1)
        }.toMap()
    }

    suspend fun saveWindowBounds(component: String, bounds: String) {
        context.launcherDataStore.edit { p ->
            val set = (p[windowBoundsKey] ?: emptySet()).filterNot { it.startsWith("$component=") }.toSet()
            p[windowBoundsKey] = set + "$component=$bounds"
        }
    }
}
