package com.ios25pan.launcher.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ios25pan.launcher.R
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.ItemType
import com.ios25pan.launcher.domain.WidgetProvider
import com.ios25pan.launcher.mvi.HomeEffect
import com.ios25pan.launcher.mvi.HomeIntent
import com.ios25pan.launcher.mvi.HomeStore

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

    Box(modifier = Modifier.fillMaxSize()) {
        // 壁纸（网页端导出的两张背景图）
        Image(
            painter = painterResource(R.drawable.wallpaper_sunny_night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
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
            Dock(items = state.desktop.dock, store = store)
        }

        val folderId = state.openFolderId
        if (folderId != null) {
            val items = state.desktop.folders[folderId].orEmpty()
            FolderOverlay(
                items = items,
                title = state.desktop.folderTitles[folderId].orEmpty(),
                store = store,
                onDismiss = { store.dispatch(HomeIntent.OpenFolder(null)) },
                onItemClick = { store.dispatch(HomeIntent.Tap(it)) },
            )
        }

        if (state.controlCenterOpen) {
            ControlCenter(onClose = { store.dispatch(HomeIntent.SetControlCenter(false)) })
        }

        if (state.widgetPickerOpen) {
            WidgetPicker(
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
            val size by animateDpAsState(if (i == current) 8.dp else 6.dp, label = "dot")
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
    Box(modifier = Modifier.fillMaxSize().padding(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
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

        if (editing && item.type != ItemType.DIVIDER) {
            IconButton(
                onClick = { store.dispatch(HomeIntent.Remove(item)) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp),
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