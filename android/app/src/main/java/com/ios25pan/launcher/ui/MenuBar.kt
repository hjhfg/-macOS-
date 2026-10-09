package com.ios25pan.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ios25pan.launcher.R
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 顶部菜单栏的圆角——只在底边，贴着屏幕顶边，和 macOS 菜单栏一样是一整条、不留边距。 */
private val MENU_BAR_SHAPE = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp)

/**
 * macOS 风格的顶部菜单栏：一条常驻的 Liquid Glass 细栏，取代了旧版"状态栏"。
 *
 * 真实 macOS 菜单栏左侧是当前前台 App 的菜单（文件/编辑/显示……），但 Android 里
 * 第三方启动器拿不到别的 App 内部的菜单结构（人家也不会把这个暴露给我们），
 * 所以这里刻意**不**假装能画出别人的菜单——左侧只放我们自己的身份（启动器的 Launchpad 入口），
 * 右侧是时间和控制中心，这是 Android 版本里唯一诚实、能落地的子集。
 *
 * 编辑态（Launchpad 里长按进入的抖动删除模式）不在这里处理——那是 Launchpad 内部的状态，
 * 菜单栏本身应该像真实 macOS 一样，不管前台在干什么都长一个样。
 */
@Composable
fun MenuBar(
    hazeState: HazeState,
    glassAlpha: Float,
    onOpenControlCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time by produceState(initialValue = "") {
        val fmt = SimpleDateFormat("M月d日 EEEE  HH:mm", Locale.CHINA)
        while (true) {
            value = fmt.format(Date())
            delay(20_000L)
        }
    }

    // 玻璃背景挂在最外层、不设固定高度（让 Box 按内容自动撑开）：这样玻璃会一路铺到
    // 屏幕最顶边、延伸到状态栏区域背后，和真实半透明菜单栏一样；真正 30dp 高的内容行
    // 用 statusBarsPadding() 让到状态栏下面——这两件事顺序不能反，
    // 如果先 height(30.dp) 再 statusBarsPadding()，状态栏的内边距会把这 30dp 吃掉大半，
    // 图标和文字会被挤成一条缝。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .launcherGlass(hazeState, MENU_BAR_SHAPE, dockGlass(), alpha = glassAlpha),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(30.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                Icons.Default.Apps,
                contentDescription = stringResource(R.string.launchpad),
                tint = OnGlass,
                modifier = Modifier.size(16.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = time, color = OnGlass, fontSize = 12.sp)
                IconButton(onClick = onOpenControlCenter, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.control_center), tint = OnGlass)
                }
            }
        }
    }
}
