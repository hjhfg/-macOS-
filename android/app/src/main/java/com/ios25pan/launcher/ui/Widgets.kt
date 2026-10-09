package com.ios25pan.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import com.ios25pan.launcher.R
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.ItemAction
import com.ios25pan.launcher.mvi.HomeStore

/** 元素的图标：种子数据里的内置图 -> 已安装应用的真实图标 -> 占位。 */
@Composable
fun ItemIcon(item: DesktopItem, store: HomeStore, modifier: Modifier = Modifier) {
    val iconRes = item.iconRes
    if (iconRes != null) {
        val context = LocalContext.current
        val id = remember(iconRes) {
            context.resources.getIdentifier(iconRes, "drawable", context.packageName)
        }
        if (id != 0) {
            Image(painter = painterResource(id), contentDescription = item.title, modifier = modifier)
            return
        }
    }
    val component = item.component
    if (component != null) {
        val bitmap by rememberAppIcon(component, store)
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = item.title, modifier = modifier)
            return
        }
    }
    when (ItemAction.parse(item.action)) {
        is ItemAction.Url -> Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        is ItemAction.Widget -> Icon(Icons.Default.Widgets, contentDescription = null, modifier = modifier)
        else -> Image(painterResource(R.drawable.default_app), contentDescription = item.title, modifier = modifier)
    }
}

@Composable
private fun rememberAppIcon(component: String, store: HomeStore): androidx.compose.runtime.State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, key1 = component) {
        value = store.loadIcon(component)
    }

/**
 * 小组件桥接层 —— 全项目唯一"不纯"的地方。
 * AppWidgetHost 返回的 RemoteViews 属于 View 体系，只能靠 AndroidView 嵌入 Compose。
 *
 * 懒加载：Compose 只在元素进入组合（即当前页及相邻页）时才创建 HostView，
 * 移出组合时 dispose，RemoteViews 由 [com.ios25pan.launcher.data.widget.WidgetRepository] 缓存复用。
 */
@Composable
fun WidgetHost(item: DesktopItem, store: HomeStore, modifier: Modifier = Modifier) {
    val appWidgetId = item.appWidgetId
    if (appWidgetId == null) {
        BuiltinClock(modifier)
        return
    }
    AndroidView(
        modifier = modifier.padding(4.dp),
        factory = { ctx -> store.widgetView(ctx, appWidgetId) ?: View(ctx) },
        update = { it.invalidate() },
    )
}

/** 网页端那个 2x2 动态时钟卡片，在没有绑定小组件前的兜底显示。 */
@Composable
fun BuiltinClock(modifier: Modifier = Modifier) {
    val time by produceState("") {
        while (true) {
            value = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            kotlinx.coroutines.delay(10_000L)
        }
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.padding(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            androidx.compose.material3.Text(
                text = time,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}