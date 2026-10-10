package com.ios25pan.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.DesktopPage
import com.ios25pan.launcher.domain.GridSpec
import com.ios25pan.launcher.domain.ItemType
import com.ios25pan.launcher.mvi.HomeIntent
import com.ios25pan.launcher.mvi.HomeStore
import kotlin.math.abs

/**
 * === 这个文件是干什么的 ===
 *
 * "桌面"本体：可以左右横滑切换的那几页图标网格，加上页面下方一排小圆点（翻页指示器）。
 * 对应 `HomeScreen.kt` 里三大块 UI 里的中间那一块——上面是 [StatusBar]，下面是 [Dock]，
 * 中间夹着的就是这里的 [Desktop]。
 *
 * 新手提示：手机桌面"能左右滑动翻页"这件事，在 Compose 里就是一个
 * [androidx.compose.foundation.pager.HorizontalPager]（横向分页器）。它的用法很像
 * `RecyclerView`/`ViewPager`：你告诉它一共有几页、当前在第几页，它负责画出来并处理滑动手势，
 * 你只需要在 `content` lambda 里描述"第 i 页要画什么"。
 */

/**
 * 桌面：翻页器 + 每一页的图标网格 + 底部翻页小圆点。
 *
 * @param pages 桌面一共有哪几页、每页里有哪些格子（[DesktopPage]），来自 `HomeStore` 算好的桌面快照。
 * @param grid 当前应该按几列几行摆放图标——手机竖屏是固定的 4×6，平板横屏会按实际可用面积
 *   算出更大的列数/行数，见 [GridSpec]。
 * @param editing 是否处于"长按进入的编辑态"（抖动 + 每个图标左上角冒出删除按钮）。
 * @param expanded 是否是"大屏模式"（平板横屏），用来决定图标占的格子大小。
 * @param pagerState 翻页器状态，由调用方（`HomeScreen`）创建并持有——因为壁纸的视差效果也需要
 *   读这同一个状态，所以状态提升到了更上层，不在这个文件内部创建。
 * @param store 用来把"点击图标""长按图标""删除图标"这些用户动作转发成 [HomeIntent]。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Desktop(
    pages: List<DesktopPage>,
    grid: GridSpec.Grid,
    editing: Boolean,
    expanded: Boolean,
    pagerState: PagerState,
    store: HomeStore,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            // beyondBoundsPageCount = 1：提前把相邻的一页也组合好（但不一定显示），
            // 代价是多留一页的内容常驻内存，换来的是滑动时不会"现场组合"而卡顿。
            beyondBoundsPageCount = 1,
            modifier = Modifier
                .weight(1f) // 把 Dock、翻页指示器之外剩下的空间全部让给网格
                .fillMaxWidth(),
        ) { index ->
            // HorizontalPager 的 content lambda 在滑动预加载阶段可能拿到还没准备好的页，
            // 用 getOrNull 兜底，拿不到就什么都不画（避免数组越界崩溃）。
            val page = pages.getOrNull(index)
            if (page != null) {
                DesktopGrid(slots = page.slots, cols = grid.cols, rows = grid.rows) { slot ->
                    DesktopCell(item = slot.item, editing = editing, store = store, expanded = expanded)
                }
            }
        }

        PageIndicator(count = pages.size, current = pagerState.currentPage)
    }
}

/** 翻页小圆点：当前页画成稍大的实心点，其它页是半透明的小点，和 iOS/系统桌面的样式一致。 */
@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return // 只有一页就没必要显示指示器，省一次布局
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            // animateDpAsState：数值变化时自动补间动画，不用自己写帧回调。
            val size by animateDpAsState(
                targetValue = if (i == current) 8.dp else 6.dp,
                animationSpec = Motion.springyDp,
                label = "dot",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(size)
                    .clip(CircleShape)
                    .background(if (i == current) Color.White else Color.White.copy(alpha = 0.4f)),
            )
        }
    }
}

/**
 * 桌面网格里的一个格子：一个图标（或小组件），加上编辑态才会出现的抖动动画和删除按钮。
 *
 * 这里不区分"这是电话图标还是相机图标"——所有普通应用图标长得一样，真正显示什么图片/名字
 * 由 [ItemIcon] 和 [item] 自己的数据决定。文件管理器、浏览器这些具体是哪个 App，并不是在这里
 * 写死的：点击之后会经过 `HomeStore.onTap()` → `AppRepository` 按"角色"（比如 role:filemanager、
 * role:browser）去系统里找真正安装的那个 App 并用 `Intent` 打开它——这个启动器本身不自己实现
 * 文件管理器或浏览器界面，原因和取舍写在仓库根目录 `README.md` 的"关于系统应用"一节。
 */
@Composable
internal fun DesktopCell(item: DesktopItem, editing: Boolean, store: HomeStore, expanded: Boolean) {
    // MutableInteractionSource：Compose 用来上报"这个控件有没有被按住"之类交互事件的通道。
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按下去缩小一点、松手弹回去——比系统默认的水波纹效果更接近 iOS 图标的手感。
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = Motion.snappyFloat,
        label = "press",
    )

    // 编辑态的抖动动画：每个图标用自己 id 算出的一个随机周期（150~272ms 之间），
    // 这样一整屏图标不会"齐步走"抖得整整齐齐，而是像真机那样略有参差、更自然。
    val wiggle = if (editing) {
        val transition = rememberInfiniteTransition(label = "wiggle")
        transition.animateFloat(
            initialValue = -1.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 150 + abs(item.id.hashCode() % 6) * 22,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse, // 来回摆动，不是摆到头了突然跳回起点
            ),
            label = "wiggle",
        )
    } else {
        null // 不是编辑态就完全不启动这个无限动画，省电省性能
    }

    Box(modifier = Modifier.fillMaxSize().padding(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 这三行都是在"图层绘制阶段"读取最新值（而不是在组合阶段），
                    // 好处是 pressScale/wiggle 变化只会触发重绘，不会触发整棵树重组，
                    // 手指按住不放、动画一直在跑的时候这个差别对帧率影响很大。
                    scaleX = pressScale
                    scaleY = pressScale
                    rotationZ = wiggle?.value ?: 0f
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null, // 用上面的缩放动画做点击反馈，不叠加系统默认水波纹
                    onClick = { store.dispatch(HomeIntent.Tap(item)) },
                    onLongClick = { store.dispatch(HomeIntent.LongPress(item)) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (item.type == ItemType.WIDGET) {
                WidgetHost(item = item, store = store, modifier = Modifier.fillMaxSize())
            } else {
                IconLabel(label = item.title, iconSize = GridSpec.iconPlateSize(expanded)) {
                    ItemIcon(item = item, store = store, modifier = Modifier.fillMaxSize(0.8f))
                }
            }
        }

        // 编辑态才显示的左上角删除按钮，用缩放 + 淡入淡出做进出场动画。
        AnimatedVisibility(
            visible = editing && item.type != ItemType.DIVIDER,
            enter = scaleIn(animationSpec = Motion.panelScale, initialScale = 0.4f) + fadeIn(),
            exit = scaleOut(animationSpec = Motion.panelScale, targetScale = 0.4f) + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            IconButton(
                onClick = { store.dispatch(HomeIntent.Remove(item)) },
                modifier = Modifier.size(34.dp), // 按钮可点击区域比视觉图标大一圈，方便点中
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color(0xAA000000), CircleShape),
                )
            }
        }
    }
}
