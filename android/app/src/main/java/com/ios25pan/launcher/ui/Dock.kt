package com.ios25pan.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.ItemType
import com.ios25pan.launcher.mvi.HomeIntent
import com.ios25pan.launcher.mvi.HomeStore
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs

private val DOCK_SHAPE = RoundedCornerShape(26.dp)
private const val MAX_EXTRA_SCALE = 0.55f
private const val MAGNIFY_RADIUS_SLOTS = 1.6f

/**
 * macOS Dock。
 *
 * 放大算法：按指针到图标中心的水平距离做平方衰减（1 + max * t²），
 * 只缩放 scaleX/scaleY，transformOrigin 固定在底部中心，图标向上"长高"而不会互相挤压。
 *
 * 两处为流畅度做的处理：
 * 1. 指针位置用 PointerEventPass.Initial 观察，不消费事件，点击照常传给子控件；
 * 2. 缩放值在 [graphicsLayer] 的 lambda 内部读取 —— 这是"延迟读取"，状态变化只会
 *    让图层重绘，不会触发重组。否则手指每移动一像素都要重组整个 Dock。
 *    只有 zIndex 需要在组合期读（它属于布局阶段），所以用一个量化后的派生状态，
 *    指针跨过格子边界时才重组一次。
 */
@Composable
fun Dock(
    items: List<DesktopItem>,
    store: HomeStore,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    var dockWidth by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            // 原来是半透明纯色背景，换成毛玻璃：模糊壁纸 + Apple 的 thin 材质
            .launcherGlass(hazeState, DOCK_SHAPE, dockGlass())
            .padding(horizontal = 6.dp)
            // 注意：onSizeChanged 与 pointerInput 必须在同一层修饰符上，
            // 否则指针 x 与下面算出来的图标中心不在同一个坐标系里，放大会偏。
            .onSizeChanged { dockWidth = it.width.toFloat() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Press, PointerEventType.Move ->
                                touchX = event.changes.firstOrNull()?.position?.x
                            PointerEventType.Release, PointerEventType.Exit -> touchX = null
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (items.isEmpty()) return@Row
        val slotW = if (dockWidth > 0f) dockWidth / items.size else 0f

        // 只在指针跨过格子边界时才变化 —— 用于决定谁画在最上层
        val hoveredIndex by remember(slotW, items.size) {
            derivedStateOf {
                val x = touchX
                val w = slotW
                if (x == null || w <= 0f) null else (x / w).toInt().coerceIn(0, items.lastIndex)
            }
        }

        items.forEachIndexed { i, item ->
            if (item.type == ItemType.DIVIDER) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.5f)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                )
                return@forEachIndexed
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .zIndex(if (hoveredIndex == i) 1f else 0f)
                    .graphicsLayer {
                        // 延迟读取 touchX：只重绘图层，不重组
                        val scale = magnification(
                            center = (i + 0.5f) * slotW,
                            touchX = touchX,
                            slotWidth = slotW,
                        )
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clickable { store.dispatch(HomeIntent.Tap(item)) },
                contentAlignment = Alignment.Center,
            ) {
                ItemIcon(item = item, store = store, modifier = Modifier.fillMaxSize(0.72f))
            }
        }
    }
}

/** 与指针距离的平方衰减；没有指针时恒为 1。 */
private fun magnification(center: Float, touchX: Float?, slotWidth: Float): Float {
    if (touchX == null || slotWidth <= 0f) return 1f
    val radius = slotWidth * MAGNIFY_RADIUS_SLOTS
    val t = (1f - abs(touchX - center) / radius).coerceIn(0f, 1f)
    return 1f + MAX_EXTRA_SCALE * t * t
}
