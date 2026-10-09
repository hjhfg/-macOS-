package com.ios25pan.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ios25pan.launcher.R
import com.ios25pan.launcher.domain.WidgetProvider

/** 选择要添加到桌面的小组件（系统 AppWidgetProvider 列表）。 */
@Composable
fun WidgetPicker(providers: List<WidgetProvider>, onPick: (WidgetProvider) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            if (providers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.widget_no_providers), color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(0.9f)) {
                    items(providers, key = { it.flat }) { provider ->
                        Text(
                            text = provider.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(provider) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}
