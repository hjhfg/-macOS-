package com.ios25pan.launcher.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ios25pan.launcher.domain.GRID_COLS
import com.ios25pan.launcher.domain.GRID_ROWS
import com.ios25pan.launcher.domain.Slot

/**
 * 一个桌面页：按 slot 的 row / col / rowSpan / colSpan 直接摆位。
 * 这样 2x2 的时钟小组件、跨度更大的卡片都能正确占位 —— 与网页端 rect 的语义完全一致。
 */
@Composable
fun DesktopGrid(
    slots: List<Slot>,
    modifier: Modifier = Modifier,
    item: @Composable (Slot) -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxSize(),
        content = { slots.forEach { item(it) } },
    ) { measurables, constraints ->
        val cellW = constraints.maxWidth / GRID_COLS
        val cellH = constraints.maxHeight / GRID_ROWS
        val placeables = measurables.mapIndexed { i, m ->
            val s = slots[i]
            val w = (s.colSpan * cellW).coerceAtMost(constraints.maxWidth)
            val h = (s.rowSpan * cellH).coerceAtMost(constraints.maxHeight)
            m.measure(Constraints.fixed(w, h))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val s = slots[i]
                p.place(s.col * cellW, s.row * cellH)
            }
        }
    }
}

/** 图标 + 标签（网页端 gridSettings.showLabels=false 时也可只显示图标）。 */
@Composable
fun IconLabel(
    label: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) { icon() }
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
