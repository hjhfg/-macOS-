package com.ios25pan.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

/**
 * 桌面上所有"玻璃"的统一入口。
 *
 * 材质取自 [CupertinoMaterials] —— 它的数值直接来自 Apple 官方发布的 iOS 18 Figma，
 * 所以做仿 macOS 的桌面时不需要自己调透明度/模糊半径，直接用它那几档即可。
 *
 * 两件值得说明的事：
 *
 * 1. **降采样**（[INPUT_SCALE]）：模糊的成本和像素数成正比。把输入缩到 0.8 再放大回来，
 *    总像素数减少约 35%，肉眼基本无感，但在壁纸跟随翻页移动（每帧都要重算模糊）时差别很大。
 *    这是 Haze 官方明确推荐的性能旋钮（`HazeInputScale`），这里统一设成 0.8。
 *
 * 2. **clip 必须在 hazeEffect 之前**：`hazeEffect` 是绘制在节点内容背后的，
 *    只有写在它前面的 `clip` 才能把模糊裁成圆角（见 Haze 官方 sample 的写法）。
 */
private const val INPUT_SCALE = 0.8f

/** Dock：常驻、面积小，用薄材质让壁纸透出来。 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
@ReadOnlyComposable
fun dockGlass(): HazeStyle = CupertinoMaterials.thin()

/** 文件夹卡片：比 Dock 厚一档，压得住里面的文字。 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
@ReadOnlyComposable
fun cardGlass(): HazeStyle = CupertinoMaterials.regular()

/** 控制中心 / 小组件选择器：整块面板，用厚材质保证可读性。 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
@ReadOnlyComposable
fun panelGlass(): HazeStyle = CupertinoMaterials.thick()

/**
 * 把一块区域变成玻璃：圆角裁剪 + Haze 模糊 + 降采样。
 *
 * 壁纸（[hazeSource] 那一侧）由调用方在共同祖先上注册一次即可，这里只做"效果"侧。
 */
@OptIn(ExperimentalHazeApi::class)
fun Modifier.launcherGlass(
    hazeState: HazeState,
    shape: Shape,
    style: HazeStyle,
    alpha: Float = 1f,
): Modifier = this
    .clip(shape)
    .hazeEffect(state = hazeState, style = style) {
        this.alpha = alpha
        inputScale = HazeInputScale.Fixed(INPUT_SCALE)
    }

/**
 * 玻璃上的文字颜色：跟随主题的 onSurface。
 *
 * 这样深色主题下是白字压深色玻璃，浅色主题下是深字压浅色玻璃，都能读。
 * 不能写死白色 —— 浅色主题时 Apple 材质会切成浅色配方，白字就看不见了。
 */
val OnGlass: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface
