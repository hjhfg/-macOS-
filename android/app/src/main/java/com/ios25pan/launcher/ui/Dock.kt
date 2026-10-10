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

/**
 * === 这个文件是干什么的 ===
 *
 * 屏幕底部那一排常驻图标——用户口中的 "Dock"。只有一个入口 Composable：[Dock]。
 * 点击某个图标最终做什么事，这个文件完全不关心，只负责把"第几个图标被点了"这件事
 * 通过 `store.dispatch(HomeIntent.Tap(item))` 丢出去；具体是打开系统设置、还是用
 * `Intent` 跳到手机上装的某个真实 App（比如文件管理器、浏览器），都在 `HomeStore` /
 * `AppRepository` 里处理，这里只管"画出图标、响应点击/悬停"。
 *
 * 这个文件里的放大效果是本项目里手写底层动画最多的一处，第一次看可以重点理解两件事：
 *  1. **放大多少是怎么算出来的**——看下面的 [magnification] 函数；
 *  2. **为什么要这么麻烦地"延迟读取"，而不是直接用 `State` 控制缩放**——看 [Dock] 函数体里
 *     `graphicsLayer { ... }` 代码块前面的注释。
 */
private val DOCK_SHAPE = RoundedCornerShape(26.dp)

/** 手指移到图标正上方时，最多能比平时放大多少（0.55 = 放大到 1.55 倍）。 */
private const val MAX_EXTRA_SCALE = 0.55f

/** 放大效果的影响半径，单位是"格子宽度的倍数"——超出这个范围的图标完全不受影响。 */
private const val MAGNIFY_RADIUS_SLOTS = 1.6f

/**
 * macOS 风格 Dock：手指（或鼠标指针）靠近哪个图标，哪个图标就跟着放大，像水波纹一样
 * 向两边扩散衰减，离得越远放大得越少。
 *
 * **放大算法**：按指针到图标中心的水平距离做平方衰减（`1 + max * t²`），
 * 只缩放 scaleX/scaleY，`transformOrigin` 固定在底部中心，图标向上"长高"而不会互相挤压
 * （如果以图标中心为缩放基准点，放大时图标会向下陷进 Dock 里，很违和）。
 *
 * **两处为流畅度做的特殊处理**（新手容易忽略，但很重要）：
 * 1. 指针位置用 `PointerEventPass.Initial` 观察，不消费（consume）事件——意思是"我只是
 *    偷看一眼指针坐标，不拦截这次点击"，点击事件该怎么往下传给 `clickable` 还是照常传递，
 *    不会出现"因为 Dock 自己监听了手势，导致图标点不中"的 bug。
 * 2. 缩放值（`scale`）在 [graphicsLayer] 的 lambda 内部读取，而不是在外层直接
 *    `Modifier.scale(someState)`——这叫"延迟读取"：Compose 只会在真正绘制这一帧的时候
 *    才去读 lambda 里的值，值变化只触发**重绘**（redraw，便宜），不会触发**重组**
 *    （recomposition，要重新跑一遍整个 Composable 函数，贵很多）。如果不这样做，手指在
 *    Dock 上每移动一像素都要重新执行一次 `Dock()` 整个函数体，会很卡。
 *    唯一例外是 `zIndex`（决定哪个图标盖在别的图标上面）——它必须在布局阶段就知道，
 *    所以用了一个"量化"过的 [derivedStateOf]：只有指针跨过了格子边界、真正换了
 *    "谁是悬停中的图标"时才会触发一次重组，而不是跟着每一次像素级移动都重组。
 */
@Composable
fun Dock(
    items: List<DesktopItem>,
    store: HomeStore,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    var dockWidth by remember { mutableFloatStateOf(0f) }
    val dockHeight = if (expanded) 92.dp else 78.dp
    val iconFraction = if (expanded) 0.64f else 0.72f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dockHeight)
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
                ItemIcon(item = item, store = store, modifier = Modifier.fillMaxSize(iconFraction))
            }
        }
    }
}

/**
 * 算出某个图标此刻应该放大到多少倍。
 *
 * @param center 这个图标中心点的 x 坐标（像素）。
 * @param touchX 当前指针的 x 坐标；`null` 表示手指已经离开 Dock，这种情况下恒返回 1（不放大）。
 * @param slotWidth 每个图标格子的宽度，用来把"影响半径"换算成实际像素。
 * @return 1f 表示正常大小，大于 1f 表示放大了多少倍。
 */
private fun magnification(center: Float, touchX: Float?, slotWidth: Float): Float {
    if (touchX == null || slotWidth <= 0f) return 1f
    val radius = slotWidth * MAGNIFY_RADIUS_SLOTS
    val t = (1f - abs(touchX - center) / radius).coerceIn(0f, 1f)
    return 1f + MAX_EXTRA_SCALE * t * t
}
