package com.ios25pan.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ios25pan.launcher.domain.GRID_COLS
import com.ios25pan.launcher.domain.GRID_ROWS
import com.ios25pan.launcher.domain.GridSpec
import com.ios25pan.launcher.domain.Slot

/**
 * === 这个文件是干什么的 ===
 *
 * 桌面"一页"里图标该摆在哪个格子的底层布局算法，外加一个到处复用的"图标+文字标签"组件。
 * 这是桌面渲染链路里最靠近底层的一环：`Desktop.kt` 负责"翻到第几页、这页有哪些格子"，
 * 而这个文件负责"给定了格子列表之后，具体每个格子该画在屏幕的什么坐标、多大尺寸"。
 *
 * 新手提示：[Layout] 是 Compose 里"自定义测量和摆放逻辑"的底层 API，类似传统 Android View
 * 体系里自己重写 `onMeasure()` / `onLayout()`。平时写界面很少需要直接用它（用 `Row`/`Column`/
 * `Box` 组合一下通常就够了），但像这种"网格里每一格大小、位置都要自己按行列号算"的场景，
 * 就必须用 [Layout] 自己控制测量与摆放这两个阶段。
 */

/**
 * 一页桌面：按每个格子（[Slot]）自带的 row / col / rowSpan / colSpan 直接摆位。
 *
 * - `row`/`col`：这个格子左上角在第几行第几列（从 0 开始数）。
 * - `rowSpan`/`colSpan`：这个格子要占几行几列——普通图标是 1x1，2x2 的时钟小组件
 *   `rowSpan=2, colSpan=2`，这样大小不同的图标、小组件才能摆在同一套网格系统里，
 *   和网页端用 CSS Grid 的 `rect` 属性描述占位是同一套语义，方便两边数据对得上。
 *
 * @param cols 这一页总共分成几列。不再是写死的常量：平板横屏可用面积大得多，由
 *   [com.ios25pan.launcher.domain.GridSpec] 按屏幕实际 dp 动态算出来；手机上算出来的值
 *   仍然是原来的 4 列。
 * @param rows 这一页总共分成几行，算法同上（手机上是 6 行）。
 * @param item 每个格子具体要画什么——调用方（`Desktop.kt`）会传一个"根据 slot 画一个
 *   `DesktopCell`"的 lambda 进来，这个文件完全不关心格子里画的是图标还是小组件。
 */
@Composable
fun DesktopGrid(
    slots: List<Slot>,
    cols: Int = GRID_COLS,
    rows: Int = GRID_ROWS,
    modifier: Modifier = Modifier,
    item: @Composable (Slot) -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxSize(),
        // content：先把每个 slot 对应的小部件（由调用方的 item lambda 决定长什么样）
        // 全部声明出来。注意这一步只是"声明有哪些子节点"，具体摆在哪由下面的 lambda 决定。
        content = { slots.forEach { item(it) } },
    ) { measurables, constraints ->
        // 第一步——测量阶段：先算出"一个格子"占多少像素。
        // 网页端相邻格子之间是有明确缝隙的（见 GridSpec 类注释里的 gapX/gapY），不是
        // 简单地把容器宽高除以列数/行数——那样图标会一个挨一个贴在一起，没有呼吸感。
        // 这里先把 (cols-1) 条横向缝 / (rows-1) 条纵向缝占用的像素减掉，剩下的才是
        // 真正用来摆 cols×rows 个格子的空间，和网页端 CSS Grid 的 gap 语义一致。
        val gapXPx = GridSpec.gapX.toPx()
        val gapYPx = GridSpec.gapY.toPx()
        val cellW = ((constraints.maxWidth - gapXPx * (cols - 1)) / cols.coerceAtLeast(1))
            .coerceAtLeast(0f)
        val cellH = ((constraints.maxHeight - gapYPx * (rows - 1)) / rows.coerceAtLeast(1))
            .coerceAtLeast(0f)
        // measurables：和 content 里声明的子节点一一对应的"待测量"句柄列表。
        // 对每一个子节点，按它自己声明的 colSpan/rowSpan 算出"应该有多大"——注意 colSpan>1
        // 的格子（比如 2x2 的时钟小组件）要把跨过的那几条缝也算进自己的宽高里，不然宽/高
        // 会比视觉上应该占的面积小一圈缝隙的量。
        val placeables = measurables.mapIndexed { i, m ->
            val s = slots[i]
            val w = (s.colSpan * cellW + (s.colSpan - 1) * gapXPx)
                .toInt()
                .coerceAtMost(constraints.maxWidth)
            val h = (s.rowSpan * cellH + (s.rowSpan - 1) * gapYPx)
                .toInt()
                .coerceAtMost(constraints.maxHeight)
            m.measure(Constraints.fixed(w, h)) // 返回一个"已经测量完、马上能摆放"的 Placeable
        }
        // 第二步——摆放阶段：layout(...) 声明 DesktopGrid 自己最终占多大，
        // 然后在它的 lambda 里把每个测量好的子节点放到它该在的像素坐标上。
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val s = slots[i]
                // 格子左上角坐标 = 第几行/列 × (每格宽/高 + 一条缝的宽度)，
                // 比之前"纯乘法"多加了缝隙的累计偏移。
                val x = (s.col * (cellW + gapXPx)).toInt()
                val y = (s.row * (cellH + gapYPx)).toInt()
                p.place(x, y)
            }
        }
    }
}

/**
 * 图标 + 下面一行文字标签的组合，桌面图标、文件夹里的图标、Dock 图标都复用这一个组件
 * （Dock 不显示标签，所以调用时会传 `showLabel = false`）。
 *
 * 对应网页端 `gridSettings.showLabels` 这个开关——为 false 时只画图标、不画文字。
 */
@Composable
fun IconLabel(
    label: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    iconSize: Dp = 56.dp,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) { icon() }
        if (showLabel) {
            Text(
                text = label,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
