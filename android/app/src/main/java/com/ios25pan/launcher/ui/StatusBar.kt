package com.ios25pan.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ios25pan.launcher.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * === 这个文件是干什么的 ===
 *
 * 屏幕最顶上那一条常驻的横条——很多人会把它叫"顶部菜单栏"或"状态栏"，这个项目里统一叫
 * **状态栏**（跟系统通知栏那条一样的叫法），因为它对应的是网页端 `ios.25pan.com.zip` 里同一块
 * UI（左边时间、右边按钮），不是真的像电脑那样会显示"文件 编辑 查看"这种应用菜单。
 *
 * 只有一个 Composable：[StatusBar]。它被 `HomeScreen.kt` 放在整个桌面最顶上。
 *
 * 新手提示：这个文件里能看到 Compose 里几个很常用的"状态"写法：
 *  - [produceState]：把一个"会自己变化的值"（这里是每 20 秒刷新一次的时间字符串）接进
 *    Compose 的世界，变化时自动触发重组，界面上的文字就会跟着更新。
 *  - `by`：Kotlin 的属性委托语法，`val time by produceState(...)` 等价于每次读 `time`
 *    的时候都去问一下 `produceState` 现在的值是什么，不用手动订阅/取消订阅。
 */

/**
 * 顶部状态栏：左边日期时间，右边根据是否在"编辑态"显示不同的按钮。
 *
 * - 平时（`editing = false`）：右边是一个齿轮图标，点一下打开控制中心（亮度/音量/壁纸…）。
 * - 长按桌面图标进入编辑态之后（`editing = true`）：右边换成"添加小组件"+"完成"两个按钮，
 *   方便用户一边看着抖动的图标一边操作，不用先退出编辑态再去找控制中心。
 *
 * （网页端的控制中心是从右上角下拉手势唤起的；手机屏幕下拉手势已经被系统通知栏占用了，
 * 所以这里换成一个按钮，避免两个手势"打架"。）
 *
 * @param editing 当前桌面是不是处于编辑态（对应 [com.ios25pan.launcher.mvi.HomeState.editing]）。
 * @param onOpenControlCenter 点击齿轮图标时调用。
 * @param onOpenWidgetPicker 编辑态下点击"添加小组件"时调用。
 * @param onExitEdit 编辑态下点击"完成"时调用，退出编辑态。
 */
@Composable
fun StatusBar(
    editing: Boolean,
    modifier: Modifier = Modifier,
    onOpenControlCenter: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onExitEdit: () -> Unit,
) {
    // 每 20 秒重新格式化一次当前时间；不需要精确到秒，没必要更频繁地刷新（省电）。
    val time by produceState(initialValue = "") {
        val fmt = SimpleDateFormat("M月d日 EEEE  HH:mm", Locale.CHINA)
        while (true) {
            value = fmt.format(Date())
            delay(20_000L)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // statusBarsPadding()：自动把内容往下挪，让出系统状态栏（显示电量/信号格的那一条）
            // 占用的空间，不然我们自己的内容会被系统状态栏盖住一部分。
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween, // 时间靠左、按钮靠右，两端对齐
    ) {
        Text(text = time, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        // 信号/WiFi + 电池：对应网页端状态栏右侧那一排图标，见 StatusBarIcons.kt 顶部说明。
        // 编辑态下这里让位给"添加小组件/完成"两个按钮，和网页端一样编辑时不展示系统信息区。
        if (!editing) {
            StatusBarSystemIcons(modifier = Modifier.padding(end = 10.dp))
        }
        if (editing) {
            IconButton(onClick = onOpenWidgetPicker) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_widget), tint = Color.White)
            }
            FilledTonalButton(onClick = onExitEdit) {
                Text(stringResource(R.string.edit_done))
            }
        } else {
            IconButton(onClick = onOpenControlCenter) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.control_center), tint = Color.White)
            }
        }
    }
}
