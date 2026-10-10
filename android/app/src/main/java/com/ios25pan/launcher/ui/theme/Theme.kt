package com.ios25pan.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 桌面主题。
 *
 * 注意 `surface` 必须是**不透明**的：Haze 的 Cupertino 材质预设会直接拿
 * `MaterialTheme.colorScheme.surface` 当玻璃的背景色（backgroundColor），
 * Haze 文档也要求它不透明 —— 否则没被模糊的原始内容会从玻璃背后透出来，看着就脏了。
 *
 * 同时 `surface` 的明暗还决定 Apple 材质走深色还是浅色配方
 * （CupertinoMaterials 内部按 `surface.luminance() < 0.5f` 判断），
 * 所以这两套配色的明暗要和壁纸（深色）及玻璃上的文字颜色保持一致。
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF1C1C1E),
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
