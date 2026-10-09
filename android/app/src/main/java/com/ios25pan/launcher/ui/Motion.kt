package com.ios25pan.launcher.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * 统一的动效规格。
 *
 * 全部走 spring（不是 tween）：手势驱动的界面里，弹簧在被打断时能从当前速度接着走，
 * 而固定时长的补间会重新起跑，看着就是"顿一下"。
 */
object Motion {
    /** 通用：起步快、收尾稳，几乎不回弹。 */
    val springyFloat: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)

    /** 按压反馈这类小位移：更硬、更快。 */
    val snappyFloat: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessHigh)

    val springyDp: FiniteAnimationSpec<Dp> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)

    /** 面板进出：不要回弹，否则滑到底会抖。 */
    val panelOffset: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /** 面板缩放：轻微回弹，像 iOS 的文件夹展开。 */
    val panelScale: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.75f, stiffness = 320f)

    /** 图标淡入，避免加载完成后"啪"地跳出来。 */
    const val ICON_FADE_MS = 140
}
