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
 * 顶部状态栏：左边日期时间，右边编辑态的"添加小组件/完成"或浏览态的控制中心入口。
 * （网页端控制中心是从右上角下拉唤起的，这里用按钮代替，免得和桌面横滑手势打架。）
 */
@Composable
fun StatusBar(
    editing: Boolean,
    modifier: Modifier = Modifier,
    onOpenControlCenter: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onExitEdit: () -> Unit,
) {
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
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = time, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
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