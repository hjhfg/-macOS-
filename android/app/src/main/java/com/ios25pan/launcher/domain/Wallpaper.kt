package com.ios25pan.launcher.domain

/**
 * 壁纸来源。
 *
 * 除 [LIVE] 外都能显示"原手机的壁纸"或自定义素材，差别只在于**能不能被毛玻璃模糊**：
 *
 * | 模式 | 谁画的 | 会动 | Haze 能模糊 |
 * |---|---|---|---|
 * | [SYSTEM] | 我们把系统壁纸抓成一张静态位图自己画 | 不会 | 能（模糊的是这张位图） |
 * | [LIVE] | 窗口透明，真正的系统壁纸从后面透出来 | 取决于系统壁纸本身 | 不能（Compose 看不到自己窗口以外的内容） |
 * | [PRESET] / [CUSTOM] | 我们自己画一张图 | 不会 | 能 |
 * | [VIDEO] | 我们自己用 Media3 ExoPlayer 循环播放一段用户选的视频 | 会 | 能（见下） |
 *
 * [VIDEO] 能被模糊是因为播放器用的是 `PlayerView.SURFACE_TYPE_TEXTURE_VIEW`——
 * 画面落在普通 `TextureView` 上，走的是 Compose/View 的正常绘制流程，
 * Haze 的 RenderNode 快照能看到它；如果用默认的 `SURFACE_TYPE_SURFACE_VIEW`，
 * 画面走独立的硬件图层合成，Haze（以及任何基于 RenderEffect 的模糊）都看不见这一层。
 *
 * 默认是 [SYSTEM]：既用原手机壁纸，又保住了毛玻璃。想要系统动态壁纸真的动起来就切 [LIVE]，
 * 代价是玻璃退化为半透明材质（Haze 在没有源内容时不会崩，只是没有模糊可采）；
 * 想要视频壁纸还想保住毛玻璃，用 [VIDEO]。
 */
enum class WallpaperMode { SYSTEM, LIVE, PRESET, CUSTOM, VIDEO }

/** 打包进 APK 的几张内置壁纸（转换脚本从网页端导出的）。 */
val WALLPAPER_PRESETS = listOf(
    "wallpaper_sunny_night",
    "wallpaper_fog",
    "wallpaper_t01f2b8957f4c756004",
)
