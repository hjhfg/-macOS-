package com.ios25pan.launcher.ui.window

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ios25pan.launcher.ui.OnGlass
import com.ios25pan.launcher.ui.launcherGlass
import com.ios25pan.launcher.ui.panelGlass
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/**
 * === 这个文件是干什么的 ===
 *
 * "浮动窗口"的通用外壳——标题栏（图标 + 标题 + 最大化/关闭按钮）、可以拖动标题栏移动整个
 * 窗口、可以从四条边和四个角拖拽改变大小、点哪里都会把这个窗口顶到最上层。这些行为跟
 * 窗口里面具体显示的是文件管理器还是浏览器完全无关——所以这个文件**不认识**
 * `FileManagerApp`/`BrowserApp`，只接收一个 `content: @Composable () -> Unit`，
 * 内容是什么由调用方（`FloatingWindowHost.kt`）决定。这是 Compose 里很常见的
 * "外壳 Composable 接收一个 content 插槽"写法，`Scaffold`/`Card` 等官方组件也是这么设计的。
 *
 * 新手如果没写过"可拖拽缩放的窗口"，这里最值得弄懂的两件事：
 *
 * 1. **几何信息为什么用像素（Float）存，不用 Dp？**
 *    手指拖动产生的 `dragAmount`（见 [detectDragGestures]）天生就是像素单位，
 *    如果存成 Dp 就要在每一次拖动回调里做一次 px -> dp 的换算，没有必要；
 *    只有最后真正拿去摆放（`Modifier.offset`/`Modifier.size`）的那一刻才需要 dp，
 *    所以换算放在渲染的最后一步，拖动过程全程是纯 Float 像素运算，少很多次转换。
 *
 * 2. **"锚点法"算缩放**：拖右下角，窗口的左上角不应该动，只有宽高在变——这是"右边/下边
 *    是活动的，左边/上边是锚点"；反过来拖左上角，右下角不动，左边/上边才是活动的。
 *    [resizeFromLeft]/[resizeFromTop] 两个函数都是先把"不动的那条边"算出来，
 *    再用"不动的边 - 新的活动边位置"推出新的宽/高，这样无论怎么拖、拖到多小，
 *    对面那条边永远钉在原地，不会出现"拖着拖着窗口自己跳一下"的观感。
 */

/** 窗口最小宽高：太小会连标题栏的几个按钮都放不下，缩放时统一卡在这个下限。 */
private val MIN_WINDOW_WIDTH = 280.dp
private val MIN_WINDOW_HEIGHT = 220.dp

/** 拖拽把手的可点击区域：比实际看到的边框粗一些，手指才容易点中（iOS/macOS 同款经验值）。 */
private val HANDLE_THICKNESS = 14.dp

/** 标题栏高度，最小化状态下窗口就只剩这么高——和桌面系统"收起窗口只留标题栏"的效果类似。 */
private val TITLE_BAR_HEIGHT = 44.dp

@Composable
fun FloatingWindow(
    title: String,
    icon: ImageVector,
    hazeState: HazeState,
    screenWidthPx: Float,
    screenHeightPx: Float,
    zIndex: Float,
    initialXPx: Float,
    initialYPx: Float,
    initialWidthPx: Float,
    initialHeightPx: Float,
    onFocus: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val minWidthPx = with(density) { MIN_WINDOW_WIDTH.toPx() }
    val minHeightPx = with(density) { MIN_WINDOW_HEIGHT.toPx() }

    // 窗口没有最大化时的几何信息——这四个才是"真实状态"，最大化只是渲染时临时换一套数字显示，
    // 不会覆盖掉它们，所以点一下"还原"按钮能立刻变回最大化之前的大小和位置。
    var xPx by remember { mutableFloatStateOf(initialXPx) }
    var yPx by remember { mutableFloatStateOf(initialYPx) }
    var widthPx by remember { mutableFloatStateOf(initialWidthPx) }
    var heightPx by remember { mutableFloatStateOf(initialHeightPx) }
    var maximized by remember { mutableStateOf(false) }
    // 最小化：窗口"卷起来"只剩标题栏，位置/宽度不变，内容区域仍然留在组合树里（WebView/文件列表
    // 这些状态都还活着，只是暂时没有画面高度展示），再点一下标题栏的按钮就"展开"回原来的高度。
    var minimized by remember { mutableStateOf(false) }

    /** 拖动标题栏移动整个窗口；限制在"至少露出一部分标题栏"的范围内，不会被拖得再也找不回来。 */
    fun moveBy(dx: Float, dy: Float) {
        val minVisible = with(density) { 72.dp.toPx() }
        xPx = (xPx + dx).coerceIn(minVisible - widthPx, screenWidthPx - minVisible)
        yPx = (yPx + dy).coerceIn(0f, screenHeightPx - minVisible)
    }

    /** 从左边缘拖：右边缘是锚点，宽度 = 锚点 - 新的左边缘位置，顶到最小宽度就不再继续收缩。 */
    fun resizeFromLeft(dx: Float) {
        val rightEdge = xPx + widthPx
        val newX = (xPx + dx).coerceAtLeast(0f)
        val newWidth = (rightEdge - newX).coerceAtLeast(minWidthPx)
        xPx = rightEdge - newWidth
        widthPx = newWidth
    }

    /** 从上边缘拖：逻辑和 [resizeFromLeft] 完全对称，锚点换成下边缘。 */
    fun resizeFromTop(dy: Float) {
        val bottomEdge = yPx + heightPx
        val newY = (yPx + dy).coerceAtLeast(0f)
        val newHeight = (bottomEdge - newY).coerceAtLeast(minHeightPx)
        yPx = bottomEdge - newHeight
        heightPx = newHeight
    }

    /** 从右边缘拖：左边缘（xPx）本来就没变过，直接改宽度即可，顺带不让右边超出屏幕。 */
    fun resizeFromRight(dx: Float) {
        widthPx = (widthPx + dx).coerceIn(minWidthPx, (screenWidthPx - xPx).coerceAtLeast(minWidthPx))
    }

    /** 从下边缘拖：和 [resizeFromRight] 对称。 */
    fun resizeFromBottom(dy: Float) {
        heightPx = (heightPx + dy).coerceIn(minHeightPx, (screenHeightPx - yPx).coerceAtLeast(minHeightPx))
    }

    // 最大化时"显示出来"的几何信息直接等于整个屏幕；真实的 xPx/widthPx 等完全不动，
    // 所以这里不需要专门记一份"最大化之前的大小"，还原按钮只是把 maximized 设回 false。
    // 最小化优先级比最大化高：同时成立时（理论上不会，UI 上互斥）按"只剩标题栏"显示。
    val titleBarHeightPx = with(density) { TITLE_BAR_HEIGHT.toPx() }
    val shownXPx = if (maximized) 0f else xPx
    val shownYPx = if (maximized) 0f else yPx
    val shownWidthPx = if (maximized) screenWidthPx else widthPx
    val shownHeightPx = when {
        minimized -> titleBarHeightPx
        maximized -> screenHeightPx
        else -> heightPx
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(shownXPx.roundToInt(), shownYPx.roundToInt()) }
            .size(with(density) { shownWidthPx.toDp() }, with(density) { shownHeightPx.toDp() })
            .zIndex(zIndex)
            // 点窗口任意位置都置顶：只"偷看"一下有没有按下，不拦截事件，手指该点到里面的按钮
            // 还是能点到——和 ui/Dock.kt 放大效果用的是同一个 PointerEventPass.Initial 技巧。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) onFocus()
                    }
                }
            }
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(18.dp))
            .launcherGlass(hazeState, RoundedCornerShape(18.dp), panelGlass()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(
                title = title,
                icon = icon,
                maximized = maximized,
                minimized = minimized,
                onDrag = { dx, dy ->
                    if (!minimized) {
                        // 收起状态下不响应拖动，先得点一下展开；展开状态才按原逻辑移动窗口。
                        if (maximized) maximized = false // 拖动已最大化的标题栏：先还原，再跟手移动，贴近真实系统的手感
                        moveBy(dx, dy)
                    }
                },
                onToggleMaximize = { maximized = !maximized },
                onToggleMinimize = { minimized = !minimized },
                onClose = onClose,
            )
            // 收起状态下这块区域高度被压到 0，但 content() 依旧留在组合树里——WebView/文件列表的
            // 状态不会丢，这才是"最小化"和"关闭"的本质区别。
            Box(modifier = Modifier.weight(1f).fillMaxSize()) { content() }
        }

        // 最大化或最小化状态下都没有可拖拽的"边"（最小化时窗口只剩标题栏，缩放没有意义），
        // 缩放把手只在两者都不成立时显示。
        if (!maximized && !minimized) {
            ResizeHandles(
                onDragLeft = { dx, _ -> resizeFromLeft(dx) },
                onDragRight = { dx, _ -> resizeFromRight(dx) },
                onDragTop = { _, dy -> resizeFromTop(dy) },
                onDragBottom = { _, dy -> resizeFromBottom(dy) },
                onDragTopLeft = { dx, dy -> resizeFromLeft(dx); resizeFromTop(dy) },
                onDragTopRight = { dx, dy -> resizeFromRight(dx); resizeFromTop(dy) },
                onDragBottomLeft = { dx, dy -> resizeFromLeft(dx); resizeFromBottom(dy) },
                onDragBottomRight = { dx, dy -> resizeFromRight(dx); resizeFromBottom(dy) },
            )
        }
    }
}

/** 标题栏：图标 + 标题文字（挤不下就省略号）+ 最小化/最大化/还原按钮 + 关闭按钮。 */
@Composable
private fun TitleBar(
    title: String,
    icon: ImageVector,
    maximized: Boolean,
    minimized: Boolean,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onToggleMaximize: () -> Unit,
    onToggleMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TITLE_BAR_HEIGHT)
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = OnGlass, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            color = OnGlass,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        // 收起/展开：和下面的最大化/关闭按钮并排，图标朝下表示"点一下收起"，
        // 收起之后图标变成朝上，表示"点一下展开回来"——不需要依赖文字说明。
        IconButton(onClick = onToggleMinimize, modifier = Modifier.size(32.dp)) {
            Icon(
                if (minimized) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = OnGlass,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onToggleMaximize, modifier = Modifier.size(32.dp)) {
            Icon(
                if (maximized) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = null,
                tint = OnGlass,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = OnGlass, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * 8 个拖拽把手：上下左右 4 条边 + 4 个角。角上的把手同时影响两个方向，
 * 比如右下角拖拽既要调用"改宽度"又要调用"改高度"，所以 [onDragBottomRight] 这类
 * 角落回调本身就接收 (dx, dy) 两个分量，由调用方（[FloatingWindow]）自己拆开转发。
 *
 * 每个把手都是完全透明的 [Box]——用户感知不到它们的存在，只会感觉"拖窗口边缘能改大小"，
 * 这也是桌面操作系统窗口管理器的标准做法（边框本身很细，但热区比视觉边框粗）。
 */
@Composable
private fun BoxScope.ResizeHandles(
    onDragLeft: (Float, Float) -> Unit,
    onDragRight: (Float, Float) -> Unit,
    onDragTop: (Float, Float) -> Unit,
    onDragBottom: (Float, Float) -> Unit,
    onDragTopLeft: (Float, Float) -> Unit,
    onDragTopRight: (Float, Float) -> Unit,
    onDragBottomLeft: (Float, Float) -> Unit,
    onDragBottomRight: (Float, Float) -> Unit,
) {
    // Modifier.align(...) 只有在 BoxScope 里才能用——这就是为什么这个函数本身要声明成
    // BoxScope 的扩展函数（`fun BoxScope.xxx`），而不是普通的顶层 Composable 函数。
    Handle(Modifier.fillMaxWidth().height(HANDLE_THICKNESS).align(Alignment.TopStart), onDragTop)
    Handle(Modifier.fillMaxWidth().height(HANDLE_THICKNESS).align(Alignment.BottomStart), onDragBottom)
    Handle(Modifier.fillMaxHeight().width(HANDLE_THICKNESS).align(Alignment.TopStart), onDragLeft)
    Handle(Modifier.fillMaxHeight().width(HANDLE_THICKNESS).align(Alignment.TopEnd), onDragRight)
    Handle(Modifier.size(HANDLE_THICKNESS * 1.6f).align(Alignment.TopStart), onDragTopLeft)
    Handle(Modifier.size(HANDLE_THICKNESS * 1.6f).align(Alignment.TopEnd), onDragTopRight)
    Handle(Modifier.size(HANDLE_THICKNESS * 1.6f).align(Alignment.BottomStart), onDragBottomLeft)
    Handle(Modifier.size(HANDLE_THICKNESS * 1.6f).align(Alignment.BottomEnd), onDragBottomRight)
}

/** 单个拖拽热区：完全透明，只负责"把手指拖动的像素增量转发出去"。 */
@Composable
private fun Handle(modifier: Modifier, onDrag: (Float, Float) -> Unit) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, amount ->
                change.consume()
                onDrag(amount.x, amount.y)
            }
        },
    )
}
