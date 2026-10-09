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
 * 目标格子大小约 116dp × 118dp（图标 + 标签 + 留白），上下预留 [RESERVED_HEIGHT_DP]
 * 给状态栏 / 任务栏 / Dock。结果落在 4..12 列、3..8 行之间 ——
 * 手机竖屏算出来正好还是 4×5~6，平板横屏能到 12×8。
 */
object GridSpec {

    data class Grid(val cols: Int, val rows: Int) {
        companion object {
            val DEFAULT = Grid(GRID_COLS, GRID_ROWS)
        }
    }

    /** 状态栏 + 页面指示 + Dock + 任务栏大约占掉的垂直空间。 */
    private const val RESERVED_HEIGHT_DP = 108

    private const val CELL_WIDTH_DP = 116
    private const val CELL_HEIGHT_DP = 118

    /** 宽到这个 dp 以上就算"平板/展开"，图标和 Dock 都放大一档。 */
    const val EXPANDED_WIDTH_DP = 840

    fun auto(widthDp: Int, heightDp: Int): Grid {
        val cols = (widthDp / CELL_WIDTH_DP).coerceIn(4, 12)
        val usable = (heightDp - RESERVED_HEIGHT_DP).coerceAtLeast(CELL_HEIGHT_DP)
        val rows = (usable / CELL_HEIGHT_DP).coerceIn(3, 8)
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

    /** 图标底板大小：平板上放大，否则格子一大就显得空。 */
    fun iconPlateSize(expanded: Boolean): Dp = if (expanded) 76.dp else 56.dp

    fun isExpanded(widthDp: Int): Boolean = widthDp >= EXPANDED_WIDTH_DP
}
