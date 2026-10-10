package com.ios25pan.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * === 这个文件是干什么的 ===
 *
 * 状态栏右侧那几个"信号格 / WiFi / 电池"小图标——网页端 `ios.25pan.com` 的状态栏是纯 HTML/CSS
 * 画出来的一条"假的"iOS 状态栏（网页没法读到真实设备的电量/信号，只能自己画一条看起来一样的）。
 *
 * 我们是原生 App，能读到真实数据，所以反过来做得更"真"：电池读真实电量，网络读真实的
 * WiFi/移动数据状态，只是图标本身还是照着 iOS/网页的视觉风格手绘的（没有用任何图标库，
 * 全部用 [Canvas] 画，不需要额外的 Gradle 依赖）。
 *
 * 为什么不直接用 Android 自带的系统状态栏（本来就有这些图标）：这个 App 是"桌面启动器"
 * （Home App），一些设备/场景下系统状态栏不会出现在桌面上方，所以网页端是怎么做的
 * （自己画一条完整的状态栏），我们就照着做一条，不依赖系统是否显示。
 */

/** 当前联网方式：决定右上角画 WiFi 图标还是信号格图标。 */
private enum class NetworkKind { WIFI, CELLULAR, NONE }

/** 每隔几秒重新查一次电量/网络状态——这两样不需要毫秒级实时，省电优先。 */
private const val POLL_INTERVAL_MS = 8_000L

/** 读当前电量百分比（0..100）。没有拿到就显示 100，避免图标空着更奇怪。 */
@Composable
private fun rememberBatteryPercent(): Int {
    val context = LocalContext.current
    val percent by produceState(initialValue = 100) {
        while (true) {
            value = readBatteryPercent(context)
            delay(POLL_INTERVAL_MS)
        }
    }
    return percent
}

private fun readBatteryPercent(context: Context): Int = runCatching {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    if (level >= 0 && scale > 0) (level * 100f / scale).toInt().coerceIn(0, 100) else 100
}.getOrDefault(100)

/** 读当前是 WiFi 连接、移动数据连接、还是都没有（飞行模式/断网）。 */
@Composable
private fun rememberNetworkKind(): NetworkKind {
    val context = LocalContext.current
    val kind by produceState(initialValue = NetworkKind.WIFI) {
        while (true) {
            value = readNetworkKind(context)
            delay(POLL_INTERVAL_MS)
        }
    }
    return kind
}

private fun readNetworkKind(context: Context): NetworkKind = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val caps = cm?.let { it.getNetworkCapabilities(it.activeNetwork) }
    when {
        caps == null -> NetworkKind.NONE
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
        else -> NetworkKind.NONE
    }
}.getOrDefault(NetworkKind.NONE)

/**
 * 状态栏右侧的"系统信息区"：信号/WiFi 图标 + 电池图标+百分比，整体风格照着网页端状态栏画。
 *
 * 外部只需要放这一个 Composable，不需要关心里面具体是信号格还是 WiFi——用哪个图标、
 * 电量是多少，都是这个函数自己读真实系统状态决定的。
 */
@Composable
fun StatusBarSystemIcons(modifier: Modifier = Modifier, tint: Color = Color.White) {
    val networkKind = rememberNetworkKind()
    val batteryPercent = rememberBatteryPercent()

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when (networkKind) {
            NetworkKind.CELLULAR -> SignalBarsIcon(tint = tint, modifier = Modifier.padding(end = 6.dp))
            NetworkKind.WIFI -> WifiIcon(tint = tint, modifier = Modifier.padding(end = 6.dp))
            NetworkKind.NONE -> SignalBarsIcon(tint = tint.copy(alpha = 0.35f), modifier = Modifier.padding(end = 6.dp))
        }
        Text(text = "$batteryPercent%", color = tint, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
        BatteryIcon(percent = batteryPercent, tint = tint)
    }
}

/** iOS 风格的 4 格信号条：从矮到高排开，格子之间留一点缝隙。 */
@Composable
private fun SignalBarsIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(width = 18.dp, height = 12.dp)) {
        val barCount = 4
        val gap = size.width * 0.08f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            // 每一格比前一格高一点，最后一格顶到最高——模拟信号格"阶梯状"的经典样子。
            val barHeight = size.height * (0.35f + 0.65f * (i + 1) / barCount)
            val left = i * (barWidth + gap)
            val top = size.height - barHeight
            drawRoundRect(
                color = tint,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.3f, barWidth * 0.3f),
            )
        }
    }
}

/** iOS 风格的 WiFi 扇形图标：三层同心弧 + 中心一个小圆点。 */
@Composable
private fun WifiIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(16.dp)) {
        val strokeWidth = size.width * 0.14f
        val dotRadius = size.width * 0.09f
        val center = Offset(size.width / 2f, size.height * 0.78f)
        // 三层弧从大到小，圆心固定在底部的小圆点上方，视觉上像信号从中心点向外扩散。
        listOf(0.95f, 0.62f, 0.3f).forEachIndexed { index, scale ->
            val radius = size.width / 2f * scale
            drawArc(
                color = tint.copy(alpha = if (index == 2) 1f else 0.95f - index * 0.1f),
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius * 1.35f),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        drawCircle(color = tint, radius = dotRadius, center = center)
    }
}

/** iOS 风格的电池图标：圆角外框 + 右边一个小凸起（正极触点）+ 按百分比填充的内部矩形。 */
@Composable
private fun BatteryIcon(percent: Int, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Box(modifier = modifier.height(12.dp).width(22.dp)) {
        Canvas(modifier = Modifier.size(width = 20.dp, height = 12.dp)) {
            val strokeWidth = 1.3.dp.toPx()
            val bodyWidth = size.width - strokeWidth
            val cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
            // 外框
            drawRoundRect(
                color = tint,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(bodyWidth, size.height - strokeWidth),
                cornerRadius = cornerRadius,
                style = Stroke(width = strokeWidth),
            )
            // 内部按百分比填充的电量条，低电量（<=20%）变红提醒，其余跟随 tint 颜色。
            val padding = 2.dp.toPx()
            val innerWidth = (bodyWidth - padding * 2) * (percent / 100f)
            val fillColor = if (percent <= 20) Color(0xFFFF3B30) else tint
            if (innerWidth > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(padding, padding),
                    size = Size(innerWidth, size.height - padding * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                )
            }
        }
        // 电池右边那个小凸起（正极触点），用一个迷你圆角矩形单独画在电池主体外面。
        Box(
            modifier = Modifier
                .padding(start = 20.dp, top = 4.dp)
                .size(width = 2.dp, height = 4.dp),
        ) {
            Canvas(modifier = Modifier.size(width = 2.dp, height = 4.dp)) {
                drawRoundRect(color = tint, cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            }
        }
    }
}
