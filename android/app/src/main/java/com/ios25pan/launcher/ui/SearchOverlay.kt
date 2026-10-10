package com.ios25pan.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.mvi.HomeStore

/**
 * === 这个文件是干什么的 ===
 *
 * 网页端桌面最下面有个"🔍 搜索"胶囊按钮，点开是一个"全局搜索"面板：输入几个字，
 * 全部图标（不分哪一页、哪个文件夹）里名字匹配的都会列出来，点一下直接启动。
 *
 * 这里是安卓版对应的实现：[SearchOverlay] 接收调用方（[com.ios25pan.launcher.ui.HomeScreen]）
 * 拍平过的全部桌面元素列表，自己只管"按输入的字过滤 + 画出结果网格 + 点击转发出去"，
 * 不关心这些元素到底是在哪一页/哪个文件夹里——因为"搜索"这个动作本来就是要跨越这些边界的。
 */
@Composable
fun SearchOverlay(
    visible: Boolean,
    allItems: List<DesktopItem>,
    onDismiss: () -> Unit,
    onItemClick: (DesktopItem) -> Unit,
    store: HomeStore,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // 每次重新打开都清空上一次的搜索词，避免"关掉再点开还留着上次搜索结果"的违和感；
    // 顺便把键盘焦点给到输入框，不用用户自己再点一下才能打字。
    LaunchedEffect(visible) {
        if (visible) {
            query = ""
            runCatching { focusRequester.requestFocus() }
        }
    }

    val results = remember(query, allItems) {
        if (query.isBlank()) {
            emptyList()
        } else {
            allItems.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9000000))
                    .clickable(onClick = onDismiss),
            )
        }

        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("搜索 App") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                        focusedLeadingIconColor = Color.White,
                        unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f),
                    ),
                )

                if (query.isNotBlank() && results.isEmpty()) {
                    Text(
                        text = "没有找到匹配的 App",
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 32.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 92.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(results, key = { it.id }) { item ->
                            Column(
                                modifier = Modifier
                                    .clickable {
                                        onItemClick(item)
                                        onDismiss()
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ItemIcon(item = item, store = store, modifier = Modifier.size(56.dp))
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
