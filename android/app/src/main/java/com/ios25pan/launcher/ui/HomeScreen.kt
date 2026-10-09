package com.ios25pan.launcher.ui

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.ios25pan.launcher.R
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.ItemType
import com.ios25pan.launcher.domain.WidgetProvider
import com.ios25pan.launcher.mvi.HomeEffect
import com.ios25pan.launcher.mvi.HomeIntent
import com.ios25pan.launcher.mvi.HomeStore
import kotlin.math.abs

/** 壁纸跟随翻页做视差，位移量（dp）。 */
private val PARALLAX_SHIFT_DP = 40.dp

/**
 * 桌面主页：壁纸 + 可横滑的页面（HorizontalPager）+ Dock + 覆盖层（文件夹 / 控制中心 / 小组件选择器）。
 * UI 只负责渲染并把用户动作转成 Intent 交给 Store，不含任何业务逻辑。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    store: HomeStore = hiltViewModel(),
    onBindWidget: (Intent, Int, WidgetProvider) -> Unit,
) {
    val state by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 全屏只有一个模糊源（壁纸），所有玻璃面板共享它
    val hazeState = rememberHazeState()

    LaunchedEffect(Unit) {
        store.effects.collect { effect ->
            when (effect) {
                is HomeEffect.Launch -> runCatching { context.startActivity(effect.intent) }
                is HomeEffect.Toast -> Toast.makeText(context, effect.messageRes, Toast.LENGTH_SHORT).show()
                is HomeEffect.BindWidget -> onBindWidget(effect.intent, effect.appWidgetId, effect.provider)
            }
        }
    }

    val pages = state.desktop.pages
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pagerState.currentPage) {
        if (pages.isNotEmpty()) store.dispatch(HomeIntent.PageChanged(pagerState.currentPage))
    }
    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(state.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
        }
    }

    val parallaxPx = with(LocalDensity.current) { PARALLAX_SHIFT_DP.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 壁纸：跟随翻页轻微反向位移，做出景深。
        // 位移量在 graphicsLayer 里延迟读取 currentPageOffsetFraction —— 每帧只重绘图层，不重组。
        Image(
            painter = painterResource(R.drawable.wallpaper_sunny_night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                // hazeSource 放在 graphicsLayer 之前：视差变换属于"源内容"的一部分，
                // 会被一起捕获进模糊，滑块时玻璃里的背景也跟着动
                .hazeSource(hazeState)
                .graphicsLayer {
                    translationX = -pagerState.currentPageOffsetFraction * parallaxPx
                    // 稍微放大，位移时才不会露出边缘
                    scaleX = 1.08f
                    scaleY = 1.08f
                },
        )

        Column(modifier = Modifier.fillMaxSize()) {
            StatusBar(
                editing = state.editing,
                onOpenControlCenter = { store.dispatch(HomeIntent.SetControlCenter(true)) },
                onOpenWidgetPicker = { store.dispatch(HomeIntent.SetWidgetPicker(true)) },
                onExitEdit = { store.dispatch(HomeIntent.ExitEdit) },
            )

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
                    DesktopGrid(slots = page.slots) { slot -> DesktopCell(slot.item, state.editing, store) }
                }
            }

            PageIndicator(count = pages.size, current = pagerState.currentPage)
            Dock(items = state.desktop.dock, store = store, hazeState = hazeState)
        }

        // 文件夹：记住最后打开的那个 id，这样退出动画播放期间还有内容可以画
        val folderId = state.openFolderId
        var lastFolderId by remember { mutableStateOf<String?>(null) }
        SideEffect { folderId?.let { lastFolderId = it } }

        FolderOverlay(
            visible = folderId != null,
            items = lastFolderId?.let { state.desktop.folders[it] }.orEmpty(),
            title = lastFolderId?.let { state.desktop.folderTitles[it] }.orEmpty(),
            store = store,
            onDismiss = { store.dispatch(HomeIntent.OpenFolder(null)) },
            onItemClick = { store.dispatch(HomeIntent.Tap(it)) },
        )

        ControlCenter(
            visible = state.controlCenterOpen,
            hazeState = hazeState,
            onClose = { store.dispatch(HomeIntent.SetControlCenter(false)) },
        )

        if (state.widgetPickerOpen) {
            WidgetPicker(
                hazeState = hazeState,
                providers = state.providers,
                onPick = { store.dispatch(HomeIntent.PickProvider(it)) },
                onDismiss = { store.dispatch(HomeIntent.SetWidgetPicker(false)) },
            )
        }
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
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
private fun DesktopCell(item: DesktopItem, editing: Boolean, store: HomeStore) {
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
                IconLabel(label = item.title) {
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
