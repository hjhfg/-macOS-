package com.ios25pan.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ios25pan.launcher.domain.AppWindow
import dev.chrisbanes.haze.HazeState

private val CHIP_SHAPE = RoundedCornerShape(18.dp)

/**
 * 自由窗口的"运行中"条：一排芯片，点一下把对应窗口带到前台，叉掉就 force-stop 它。
 *
 * 这不是窗口管理器——我们没有也拿不到窗口本身，这里只是我们自己这边的记账（见
 * [com.ios25pan.launcher.data.window.WindowRepository] 的说明）。系统随时可能
 * 让某个窗口消失而不通知我们，芯片最终以用户下次操作时的结果为准，不保证绝对实时。
 */
@Composable
fun WindowShelf(
    windows: List<AppWindow>,
    hazeState: HazeState,
    onFocus: (String) -> Unit,
    onClose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = windows.isNotEmpty(),
        enter = fadeIn() + slideInVertically(animationSpec = Motion.panelOffset) { -it / 2 },
        exit = fadeOut() + slideOutVertically(animationSpec = Motion.panelOffset) { -it / 2 },
        modifier = modifier,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(windows, key = { it.component }) { w ->
                Row(
                    modifier = Modifier
                        .animateContentSize()
                        .launcherGlass(hazeState, CHIP_SHAPE, dockGlass())
                        .clickable { onFocus(w.component) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = OnGlass,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(16.dp),
                    )
                    Text(
                        text = w.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = OnGlass,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = OnGlass,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(16.dp)
                            .clickable { onClose(w.component) },
                    )
                }
            }
        }
    }
}
