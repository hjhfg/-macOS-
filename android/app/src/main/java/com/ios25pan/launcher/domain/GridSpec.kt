package com.ios25pan.launcher.domain

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 平板横屏网格。
 *
 * 原来写死 4 列 × 6 行（手机竖屏的规格），放到 14.6 英寸的平板上会变成
 * 一排四个巨大的图标。这里按可用 dp 推算，并且**只在横向上加列**：
 * 平板的高度增长有限，宽度才是多出来的那部分。
 *
 * 目标格子大小约 92dp × 98dp（图标 + 标签 + 留白），上下预留 [RESERVED_HEIGHT_DP]
 * 给状态栏 / 任务栏 / Dock。结果落在 4..18 列、3..11 行之间——
 * 手机竖屏算出来还是 4×6 左右，16 寸笔记本这种大横屏能到十几列。
 *
 * 这两个格子尺寸（以及下面的列/行上限）原来是 116×118 / 12×8，是按"手机/平板触屏，
 * 图标要够大方便手指点"设计的，在真正宽的大屏幕上会显得异常空旷——同一页能放的图标数
 * 远少于网页版（网页端是鼠标操作，图标可以明显更小更密）。缩小格子、放宽列/行上限之后，
 * 同一份数据能在一页里放下更多图标，减少"内容被迫拆到好几页、第一页看起来空荡荡"的情况，
 * 也更贴近网页端那种"大屏桌面密密麻麻摆满图标"的视觉密度。
 */
object GridSpec {

    data class Grid(val cols: Int, val rows: Int) {
        companion object {
            val DEFAULT = Grid(GRID_COLS, GRID_ROWS)
        }
    }

    /** 状态栏 + 页面指示 + Dock + 任务栏大约占掉的垂直空间。 */
    private const val RESERVED_HEIGHT_DP = 108

    private const val CELL_WIDTH_DP = 92
    private const val CELL_HEIGHT_DP = 98

    /** 宽到这个 dp 以上就算"平板/展开"，图标和 Dock 都放大一档。 */
    const val EXPANDED_WIDTH_DP = 840

    fun auto(widthDp: Int, heightDp: Int): Grid {
        val cols = (widthDp / CELL_WIDTH_DP).coerceIn(4, 18)
        val usable = (heightDp - RESERVED_HEIGHT_DP).coerceAtLeast(CELL_HEIGHT_DP)
        val rows = (usable / CELL_HEIGHT_DP).coerceIn(3, 11)
        return Grid(cols, rows)
    }

    /** 网格算好后按用户设置修正：0 表示"自动"。 */
    fun resolve(override: Pair<Int, Int>, widthDp: Int, heightDp: Int): Grid {
        val auto = auto(widthDp, heightDp)
        return Grid(
            cols = override.first.takeIf { it > 0 } ?: auto.cols,
            rows = override.second.takeIf { it > 0 } ?: auto.rows,
        )
    }

    // 格子本身缩小了（见上面 CELL_WIDTH_DP/CELL_HEIGHT_DP 的注释），图标底板也跟着
    // 按比例缩小一点，留出和网页端类似的"图标间留白"，不然图标会紧贴到几乎没有间距。
    /** 图标底板大小：平板上放大，否则格子一大就显得空。 */
    fun iconPlateSize(expanded: Boolean): Dp = if (expanded) 68.dp else 50.dp

    fun isExpanded(widthDp: Int): Boolean = widthDp >= EXPANDED_WIDTH_DP
}
