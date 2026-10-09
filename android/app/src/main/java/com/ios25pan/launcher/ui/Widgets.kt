package com.ios25pan.launcher.ui

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
        // 先同步取一次缓存：命中就说明这张图之前加载过，直接显示、不做淡入，
        // 否则翻回上一页时每张图标都要再淡一遍，反而更晃眼。
        val cached = remember(component) { store.cachedIcon(component) }
        val bitmap by produceState<ImageBitmap?>(initialValue = cached, key1 = component) {
            value = store.loadIcon(component) ?: value
        }
        if (bitmap != null) {
            // 只有缓存没命中（加载完成后才出现）的图标才淡入。
            // 用 Animatable 而不是 animateFloatAsState —— 后者的初始值等于目标值，动画不会跑。
            val alpha = remember(component) { Animatable(if (cached != null) 1f else 0f) }
            LaunchedEffect(bitmap) { alpha.animateTo(1f, tween(Motion.ICON_FADE_MS)) }
            Image(
                bitmap = bitmap!!,
                contentDescription = item.title,
                // 延迟读取 alpha.value：动画跑在绘制阶段，不触发重组
                modifier = modifier.graphicsLayer { this.alpha = alpha.value },
            )
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
        else -> IconPlaceholder(item.title, modifier)
    }
}

/**
 * 图标还没加载出来时的占位（首屏每个图标都会先走这里）。
 *
 * 刻意用 Compose 直接画，不引任何位图资源：
 *  - 源站导出里存在"扩展名 .png、内容其实是 HTML"的假图标，解码会失败；
 *  - 每个格子都解码一张位图做占位也浪费，画个圆角方块几乎零成本。
 */
@Composable
private fun IconPlaceholder(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.16f),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Default.Apps,
                contentDescription = label,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxSize(0.55f),
            )
        }
    }
}

/**
 * 小组件桥接层 —— 全项目唯一"不纯"的地方。
 * AppWidgetHost 返回的 RemoteViews 属于 View 体系，只能靠 AndroidView 嵌入 Compose。
 *
 * 懒加载 / 性能：
 *  - HorizontalPager 只组合当前页及相邻页，离屏的 AndroidView 会被 dispose；
 *  - RemoteViews 由 WidgetRepository 按 appWidgetId 缓存复用，翻页回来不用重新 inflate；
 *  - 这里刻意不写 update = { invalidate() }：update 每次重组都会跑，
 *    强制 invalidate 会让小组件在无关的重组里反复重绘。
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
            Text(
                text = time,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
