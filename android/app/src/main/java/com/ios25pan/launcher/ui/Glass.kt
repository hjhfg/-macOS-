package com.ios25pan.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

/**
 * 模糊半径（dp）。
 *
 * Cupertino / Haze 的材质预设都写死 24dp，这里刻意调小：
 * `RenderEffect.createBlurEffect` 在部分设备（尤其是老一些的 GPU/驱动）上
 * 会为大半径抛 `IllegalArgumentException`，而 Haze 会把这个异常原样抛出来
 * —— 而这东西第一帧就开始画，等于一进桌面就崩。
 *
 * 如果还报 "device does not support a blur radius of Xdp"，继续往下调；
 * 调到 0.dp 就退化成纯半透明材质（有玻璃感、没有模糊），不会崩。
 */
private val BLUR_RADIUS = 16.dp

@OptIn(ExperimentalHazeMaterialsApi::class)
private fun withSafeBlur(style: HazeStyle): HazeStyle = style.copy(blurRadius = BLUR_RADIUS)

/** Dock：常驻、面积小，用薄材质让壁纸透出来。 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
@ReadOnlyComposable
fun dockGlass(): HazeStyle = withSafeBlur(CupertinoMaterials.thin())

/** 文件夹卡片：比 Dock 厚一档，压得住里面的文字。 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
@ReadOnlyComposable
fun cardGlass(): HazeStyle = withSafeBlur(CupertinoMaterials.regular())

/** 控制中心 / 小组件选择器：整块面板，用厚材质保证可读性。 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
@ReadOnlyComposable
fun panelGlass(): HazeStyle = withSafeBlur(CupertinoMaterials.thick())

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
