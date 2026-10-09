package com.ios25pan.launcher.domain

/**
 * 壁纸来源。
 *
 * 三种都能显示"原手机的壁纸"，差别只在于**能不能被毛玻璃模糊**：
 *
 * | 模式 | 谁画的 | 动态壁纸 | Haze 能模糊 |
 * |---|---|---|---|
 * | [SYSTEM] | 我们把系统壁纸抓成一张静态位图自己画 | 不会动 | 能（模糊的是这张位图） |
 * | [LIVE] | 窗口透明，真正的系统壁纸从后面透出来 | 会动 | 不能（Compose 看不到自己窗口以外的内容） |
 * | [PRESET] / [CUSTOM] | 我们自己画 | — | 能 |
 *
 * 默认是 [SYSTEM]：既用原手机壁纸，又保住了毛玻璃。想要动态壁纸动起来就切 [LIVE]，
 * 代价是玻璃退化为半透明材质（Haze 在没有源内容时不会崩，只是没有模糊可采）。
 */
enum class WallpaperMode { SYSTEM, LIVE, PRESET, CUSTOM }

/** 打包进 APK 的几张内置壁纸（转换脚本从网页端导出的）。 */
val WALLPAPER_PRESETS = listOf(
    "wallpaper_sunny_night",
    "wallpaper_fog",
    "wallpaper_t01f2b8957f4c756004",
)
