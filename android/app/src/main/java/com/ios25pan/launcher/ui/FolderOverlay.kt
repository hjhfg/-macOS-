package com.ios25pan.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.mvi.HomeStore
import dev.chrisbanes.haze.HazeState

private const val FOLDER_COLUMNS = 3

/**
 * 打开文件夹：半透明遮罩 + 居中卡片，内部 3 列网格（与网页端文件夹一致）。
 *
 * 进出用 spring 做缩放淡入淡出，所以即使 [visible] 立刻变 false，
 * 退出动画播完之前内容还在（内容由调用方记住最后的 folder id 提供）。
 */
@Composable
fun FolderOverlay(
    visible: Boolean,
    hazeState: HazeState,
    items: List<DesktopItem>,
    title: String,
    store: HomeStore,
    onDismiss: () -> Unit,
    onItemClick: (DesktopItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB3000000))
                    .clickable { onDismiss() },
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(animationSpec = Motion.panelScale, initialScale = 0.85f) + fadeIn(),
            exit = scaleOut(animationSpec = Motion.panelScale, targetScale = 0.9f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .launcherGlass(hazeState, RoundedCornerShape(28.dp), cardGlass()),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnGlass,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.size(16.dp))
                    items.chunked(FOLDER_COLUMNS).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onItemClick(item) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        ItemIcon(item = item, store = store, modifier = Modifier.size(48.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnGlass,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                            repeat(FOLDER_COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}
