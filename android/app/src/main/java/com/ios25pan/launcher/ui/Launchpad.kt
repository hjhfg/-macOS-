package com.ios25pan.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ios25pan.launcher.R
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.DesktopPage
import com.ios25pan.launcher.domain.GridSpec
import com.ios25pan.launcher.domain.ItemType
import com.ios25pan.launcher.mvi.HomeIntent
import com.ios25pan.launcher.mvi.HomeStore
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs

/**
 * Launchpad：macOS 里"全部应用"的全屏面板。
 *
 * 旧版本把这套分页网格直接当成了"桌面本身"——点开启动器就是铺满整屏的 App 网格 + 底部 Dock，
 * 这其实是 iOS 主屏的信息架构，不是 macOS 的。真正的 macOS 桌面是：壁纸 + 菜单栏 + 一个干净的
 * 桌面区域 + Dock；"所有应用"单独收在 Launchpad 里，按 Dock 图标或手势才会盖上来。
 *
 * 这里原样保留了之前几轮做好的分页 / 网格 / 文件夹 / 编辑态抖动删除逻辑——只是把它从"桌面"
 * 挪到了"叠在桌面上的一张磨砂玻璃大面板"里，点击面板空白处会像 macOS 一样收起回到桌面。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Launchpad(
    visible: Boolean,
    pages: List<DesktopPage>,
    cols: Int,
    rows: Int,
    editing: Boolean,
    expanded: Boolean,
    pagerState: PagerState,
    hazeState: HazeState,
    glassAlpha: Float,
    store: HomeStore,
    onOpenWidgetPicker: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(animationSpec = Motion.panelScale, initialScale = 0.94f),
        exit = fadeOut() + scaleOut(animationSpec = Motion.panelScale, targetScale = 0.94f),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 磨砂遮罩铺满整个面板；点空白处收起——不消费子项（网格格子）自己的点击。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .launcherGlass(hazeState, RectangleShape, panelGlass(), alpha = glassAlpha)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            )

            Column(modifier = Modifier.fillMaxSize()) {
                // 编辑态（长按进入的抖动删除模式）才需要的"添加小组件 / 完成"条，平时完全不占地方。
                // 注意：expandVertically/shrinkVertically 要的是 FiniteAnimationSpec<IntSize>，
                // 和 Motion 里为 IntOffset（slideIn/slideOut 那套）准备的弹簧不是同一个类型，
                // 这里就不复用、直接用默认的 spring，省得类型对不上。
                AnimatedVisibility(
                    visible = editing,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = stringResource(R.string.launchpad), color = OnGlass)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onOpenWidgetPicker) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_widget), tint = OnGlass)
                            }
                            FilledTonalButton(onClick = { store.dispatch(HomeIntent.ExitEdit) }) {
                                Text(stringResource(R.string.edit_done))
                            }
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    // 预组合相邻页：滑动时不会现场组合，代价是多留一页的 AndroidView 在内存里
                    beyondBoundsPageCount = 1,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { index ->
                    val page = pages.getOrNull(index)
                    if (page != null) {
                        DesktopGrid(slots = page.slots, cols = cols, rows = rows) { slot ->
                            DesktopCell(slot.item, editing, store, expanded)
                        }
                    }
                }

                PageIndicator(count = pages.size, current = pagerState.currentPage)
            }
        }
    }
}

@Composable
internal fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
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

@Composable
internal fun DesktopCell(item: DesktopItem, editing: Boolean, store: HomeStore, expanded: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = Motion.snappyFloat,
        label = "press",
    )

    // 编辑态的抖动：给每个图标一个不同的周期，避免整齐划一地"齐步走"
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
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wiggle",
        )
    } else {
        null
    }

    Box(modifier = Modifier.fillMaxSize().padding(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 三个值都是延迟读取：按下、抖动、都不触发重组，只重绘图层
                    scaleX = pressScale
                    scaleY = pressScale
                    rotationZ = wiggle?.value ?: 0f
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null, // 用缩放做反馈，不画水波纹（更接近 iOS，也少一层绘制）
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

        AnimatedVisibility(
            visible = editing && item.type != ItemType.DIVIDER,
            enter = scaleIn(animationSpec = Motion.panelScale, initialScale = 0.4f) + fadeIn(),
            exit = scaleOut(animationSpec = Motion.panelScale, targetScale = 0.4f) + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            IconButton(
                onClick = { store.dispatch(HomeIntent.Remove(item)) },
                modifier = Modifier.size(34.dp), // 保证可点区域够大
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
