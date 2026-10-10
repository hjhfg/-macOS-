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
 * 下面这几个数字不是拍脑袋猜的——是直接从 `ios.25pan.com.zip` 里 `assets/DeskRoot-*.js`
 * 反编译出来的真实默认值（网页端桌面布局引擎里字面量就是
 * `{cols:6,rows:8,cellWidth:88,cellHeight:104,gapX:14,gapY:18,paddingInline:28,paddingBlock:72}`），
 * 单位是 CSS px；CSS px 本来就是"和屏幕密度无关的逻辑像素"，和 Android 的 dp 是同一个概念，
 * 可以直接 1:1 当 dp 用，不需要换算。网页端这组"6×8"只是一个未经视口自适应前的出厂默认值，
 * 真正显示的列数由网页自己的"桌面图标自动填满"逻辑按容器宽度重新算——这正是我们下面
 * [auto] 函数在做的事，只是算法这边是我们自己按同一套格子尺寸反推的，不是抄网页的计算代码
 * （拿不到网页那段自适应函数的完整实现，只能照着"格子多大、缝多宽、边距多少"这几个
 * 写死的数字自己填算法）。
 *
 * 上下预留 [RESERVED_HEIGHT_DP] 给状态栏 / 翻页圆点 / Dock + 搜索栏；[GAP_X_DP]/[GAP_Y_DP]
 * 是图标格子之间的缝隙，[PADDING_INLINE_DP] 是整页左右留白——这三个和 [CELL_WIDTH_DP]/
 * [CELL_HEIGHT_DP] 一起决定了"这么宽的屏幕到底能横着摆几列"。16 寸笔记本这种大横屏，
 * 按这套公式能摆到十几列，比之前随手定的 92×98dp 更贴近网页端真实的密度。
 */
object GridSpec {

    data class Grid(val cols: Int, val rows: Int) {
        companion object {
            val DEFAULT = Grid(GRID_COLS, GRID_ROWS)
        }
    }

    /** 状态栏 + 页面指示 + Dock + 搜索栏大约占掉的垂直空间。 */
    private const val RESERVED_HEIGHT_DP = 130

    // 以下 5 个常量原样照抄自网页端 DeskRoot 的默认网格配置（见上面类注释），
    // 单位 CSS px == dp，不需要换算。
    private const val CELL_WIDTH_DP = 88
    private const val CELL_HEIGHT_DP = 104
    private const val GAP_X_DP = 14
    private const val GAP_Y_DP = 18
    private const val PADDING_INLINE_DP = 28

    /** 图标格子之间的横向缝隙，供 [com.ios25pan.launcher.ui.DesktopGrid] 画格子时用。 */
    val gapX: Dp get() = GAP_X_DP.dp

    /** 图标格子之间的纵向缝隙，供 [com.ios25pan.launcher.ui.DesktopGrid] 画格子时用。 */
    val gapY: Dp get() = GAP_Y_DP.dp

    /** 宽到这个 dp 以上就算"平板/展开"，图标和 Dock 都放大一档。 */
    const val EXPANDED_WIDTH_DP = 840

    fun auto(widthDp: Int, heightDp: Int): Grid {
        // 列数公式照抄网页端"格子+缝隙"的排布方式反推：
        // 可用宽度 = 总宽 - 左右各一份 PADDING_INLINE；N 列之间有 N-1 条缝，
        // 所以 N*(cellWidth+gapX) - gapX <= 可用宽度，解出 N 的上界。
        val usableWidth = (widthDp - PADDING_INLINE_DP * 2).coerceAtLeast(CELL_WIDTH_DP)
        val cols = ((usableWidth + GAP_X_DP) / (CELL_WIDTH_DP + GAP_X_DP)).coerceIn(4, 18)
        val usableHeight = (heightDp - RESERVED_HEIGHT_DP).coerceAtLeast(CELL_HEIGHT_DP)
        val rows = ((usableHeight + GAP_Y_DP) / (CELL_HEIGHT_DP + GAP_Y_DP)).coerceIn(3, 11)
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

    /** 图标底板大小：跟网页端的 baseIconSize(58px) 对齐，平板上按比例放大一档。 */
    fun iconPlateSize(expanded: Boolean): Dp = if (expanded) 64.dp else 52.dp

    fun isExpanded(widthDp: Int): Boolean = widthDp >= EXPANDED_WIDTH_DP
}

