package com.ios25pan.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dock 下面那一小条——网页端桌面最底下是一个"🔍 搜索"胶囊按钮，再下面一条细横杠
 * （提示"从这里上滑可以做别的操作"，照抄的是 iOS 的"主屏幕指示条"视觉习惯）。
 *
 * 这一条本项目里纯粹是"外壳"还原：横杠本身不监听手势（上滑动作已经由系统手势/
 * [com.ios25pan.launcher.ui.window.FloatingWindowHost] 等别处处理），只有"搜索"
 * 胶囊是真的可以点的，点了之后交给调用方（[HomeScreen]）弹出 [SearchOverlay]。
 */
@Composable
fun BottomGestureBar(onSearchClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .clickable(onClick = onSearchClick)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.85f))
            Text(text = "搜索", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }

        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .height(4.dp)
                .width(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.55f)),
        )
    }
}
