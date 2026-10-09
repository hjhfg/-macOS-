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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlin.math.abs

private const val MAX_EXTRA_SCALE = 0.55f
private const MAGNIFY_RADIUS_SLOTS = 1.6f

/**
 * macOS Dock。
 *
 * 放大算法：按指针到图标中心的水平距离做平方衰减（1 + max * t²），
 * 只缩放 scaleX/scaleY，transformOrigin 固定在底部中心，图标向上"长高"而不会互相挤压。
 * 用 PointerEventPass.Initial 观察指针位置但不消费事件，点击照常传给子控件。
 */
@Composable
fun Dock(items: List<DesktopItem>, store: HomeStore, modifier: Modifier = Modifier) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    var dockWidth by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .background(Color(0x551C1C1E), RoundedCornerShape(26.dp))
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
                            PointerEventType.Release -> touchX = null
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (items.isEmpty()) return@Row
        val slotW = if (dockWidth > 0f) dockWidth / items.size else 0f
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
            val scale = magnification(center = (i + 0.5f) * slotW, touchX = touchX, slotWidth = slotW)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .zIndex(scale)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
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