package com.ios25pan.launcher.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.mvi.HomeStore

private const val FOLDER_COLUMNS = 3

/** 打开文件夹：半透明遮罩 + 居中卡片，内部 3 列网格（与网页端文件夹一致）。 */
@Composable
fun FolderOverlay(
    items: List<DesktopItem>,
    title: String,
    store: HomeStore,
    onDismiss: () -> Unit,
    onItemClick: (DesktopItem) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        repeat(FOLDER_COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
