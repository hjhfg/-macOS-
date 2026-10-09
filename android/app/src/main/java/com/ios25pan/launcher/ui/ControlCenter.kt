package com.ios25pan.launcher.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ios25pan.launcher.R
import com.ios25pan.launcher.data.wallpaper.WallpaperRender
import com.ios25pan.launcher.data.window.ShellState
import com.ios25pan.launcher.domain.WALLPAPER_PRESETS
import com.ios25pan.launcher.domain.WallpaperMode
import com.ios25pan.launcher.domain.WindowMode
import com.ios25pan.launcher.ui.util.findActivity
import com.ios25pan.launcher.util.CrashHandler
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * 控制中心（对应网页端的 Wi-Fi / 蓝牙 / 亮度面板）。
 *
 * 通通走免权限路径：
 *  - 亮度：只改当前窗口亮度（WindowManager.LayoutParams.screenBrightness），不需要 WRITE_SETTINGS
 *  - 音量：AudioManager 直接设置，不需要权限
 *  - 网络 / 蓝牙 / NFC：Android 10+ 的 Settings.Panel 面板（系统弹窗），第三方应用也允许拉起
 *
 * 进出用 spring 滑入滑出（不从中心缩放，避免和上面的文件夹动画撞脸）。
 */
@Composable
fun ControlCenter(
    visible: Boolean,
    hazeState: HazeState,
    blurDisabled: Boolean,
    onBlurDisabledChange: (Boolean) -> Unit,
    windowMode: WindowMode,
    shellState: ShellState,
    onWindowModeChange: (WindowMode) -> Unit,
    onRequestShizuku: () -> Boolean,
    onDiagnoseWindow: suspend () -> String,
    wallpaper: WallpaperRender,
    onWallpaperModeChange: (WallpaperMode) -> Unit,
    onWallpaperPresetChange: (String) -> Unit,
    onRequestWallpaperPermission: () -> Unit,
    onPickCustomWallpaper: () -> Unit,
    forceLandscape: Boolean,
    onForceLandscapeChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var brightness by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.6f)
    }
    var music by remember { mutableFloatStateOf(streamRatio(audio, AudioManager.STREAM_MUSIC)) }
    var ring by remember { mutableFloatStateOf(streamRatio(audio, AudioManager.STREAM_RING)) }
    var alarm by remember { mutableFloatStateOf(streamRatio(audio, AudioManager.STREAM_ALARM)) }
    // 只在控制中心进入组合时读一次：拖动滑块会频繁重组，不能每次都去读文件
    val hasCrashLog = remember { CrashHandler.lastCrash() != null }
    var showCrashLog by remember { mutableStateOf(false) }

    if (showCrashLog) {
        CrashLogDialog(onDismiss = { showCrashLog = false })
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 遮罩与面板分别动画：遮罩只淡入淡出，面板从顶部滑下来
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable { onClose() },
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = Motion.panelOffset) { -it } + fadeIn(),
            exit = slideOutVertically(animationSpec = Motion.panelOffset) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .launcherGlass(
                        hazeState,
                        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        panelGlass(),
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.control_center),
                            style = MaterialTheme.typography.titleMedium,
                            color = OnGlass,
                        )
                        Spacer(Modifier.weight(1f))
                        if (hasCrashLog) {
                            TextButton(onClick = { showCrashLog = true }) {
                                Text(
                                    stringResource(R.string.crash_log_entry),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnGlass,
                                )
                            }
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleTile(
                            icon = Icons.Default.Wifi,
                            label = stringResource(R.string.toggle_network),
                            modifier = Modifier.weight(1f),
                        ) { openPanel(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIFI_SETTINGS) }
                        ToggleTile(
                            icon = Icons.Default.Bluetooth,
                            label = stringResource(R.string.toggle_bluetooth),
                            modifier = Modifier.weight(1f),
                        ) { openPanel(context, Settings.Panel.ACTION_BLUETOOTH, Settings.ACTION_BLUETOOTH_SETTINGS) }
                        ToggleTile(
                            icon = Icons.Default.Nfc,
                            label = stringResource(R.string.toggle_nfc),
                            modifier = Modifier.weight(1f),
                        ) { openPanel(context, Settings.Panel.ACTION_NFC, Settings.ACTION_NFC_SETTINGS) }
                        ToggleTile(
                            icon = Icons.Default.VolumeUp,
                            label = stringResource(R.string.volume_title),
                            modifier = Modifier.weight(1f),
                        ) { openPanel(context, Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS) }
                    }

                    Spacer(Modifier.height(12.dp))
                    SwitchRow(stringResource(R.string.glass_blur), !blurDisabled) { onBlurDisabledChange(!it) }
                    SwitchRow(stringResource(R.string.force_landscape), forceLandscape, onForceLandscapeChange)

                    Spacer(Modifier.height(4.dp))
                    SectionTitle(stringResource(R.string.window_mode))
                    WindowModeRow(windowMode, shellState, onWindowModeChange, onRequestShizuku, onDiagnoseWindow)

                    Spacer(Modifier.height(4.dp))
                    SectionTitle(stringResource(R.string.wallpaper))
                    WallpaperRow(
                        wallpaper = wallpaper,
                        onModeChange = onWallpaperModeChange,
                        onPresetChange = onWallpaperPresetChange,
                        onRequestPermission = onRequestWallpaperPermission,
                        onPickCustom = onPickCustomWallpaper,
                    )

                    Spacer(Modifier.height(8.dp))
                    SliderRow(stringResource(R.string.brightness), brightness) {
                        brightness = it
                        activity?.window?.apply {
                            attributes = attributes.apply { screenBrightness = it.coerceIn(0.02f, 1f) }
                        }
                    }
                    SliderRow(stringResource(R.string.volume_music), music) {
                        music = it
                        setStream(audio, AudioManager.STREAM_MUSIC, it)
                    }
                    SliderRow(stringResource(R.string.volume_ring), ring) {
                        ring = it
                        setStream(audio, AudioManager.STREAM_RING, it)
                    }
                    SliderRow(stringResource(R.string.volume_alarm), alarm) {
                        alarm = it
                        setStream(audio, AudioManager.STREAM_ALARM, it)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = OnGlass.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnGlass,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 窗口模式：全屏 / 自由窗口。
 *
 * 自由窗口需要 Shizuku 拿到 shell 身份，三种状态都要让用户看懂发生了什么：
 *  - 没装 / 没跑：提示去装 Shizuku，开关本身可以打开但点图标时会自动退回全屏
 *  - 跑着但没授权：给一个"授权"按钮，点了弹 Shizuku 自己的对话框
 *  - 就绪：正常切换
 */
@Composable
private fun WindowModeRow(
    mode: WindowMode,
    shellState: ShellState,
    onModeChange: (WindowMode) -> Unit,
    onRequestShizuku: () -> Boolean,
    onDiagnoseWindow: suspend () -> String,
) {
    val scope = rememberCoroutineScope()
    var diagnosis by remember { mutableStateOf<String?>(null) }

    diagnosis?.let { text ->
        AlertDialog(
            onDismissRequest = { diagnosis = null },
            confirmButton = { TextButton(onClick = { diagnosis = null }) { Text("确定") } },
            title = { Text(stringResource(R.string.window_mode)) },
            text = { Text(text, style = MaterialTheme.typography.bodySmall) },
        )
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    if (mode == WindowMode.FREEFORM) R.string.window_mode_freeform else R.string.window_mode_fullscreen,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = OnGlass,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = mode == WindowMode.FREEFORM,
                onCheckedChange = { onModeChange(if (it) WindowMode.FREEFORM else WindowMode.FULLSCREEN) },
            )
        }
        val hint = when (shellState) {
            ShellState.NOT_RUNNING -> R.string.shizuku_not_running
            ShellState.NOT_GRANTED -> R.string.shizuku_not_granted
            ShellState.READY -> R.string.shizuku_ready
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = OnGlass.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            if (shellState == ShellState.NOT_GRANTED) {
                TextButton(onClick = { onRequestShizuku() }) {
                    Text(stringResource(R.string.shizuku_grant), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (shellState == ShellState.READY && mode == WindowMode.FREEFORM) {
                TextButton(onClick = { scope.launch { diagnosis = onDiagnoseWindow() } }) {
                    Text(stringResource(R.string.window_diagnose), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun WallpaperRow(
    wallpaper: WallpaperRender,
    onModeChange: (WallpaperMode) -> Unit,
    onPresetChange: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onPickCustom: () -> Unit,
) {
    val context = LocalContext.current
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                onModeChange(WallpaperMode.SYSTEM)
                onRequestPermission()
            }) {
                Text(stringResource(R.string.wallpaper_system), style = MaterialTheme.typography.bodySmall, color = OnGlass)
            }
            TextButton(onClick = onPickCustom) {
                Text(stringResource(R.string.wallpaper_custom), style = MaterialTheme.typography.bodySmall, color = OnGlass)
            }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) }
            }) {
                Text(stringResource(R.string.wallpaper_system_picker), style = MaterialTheme.typography.bodySmall, color = OnGlass)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            WALLPAPER_PRESETS.forEach { name ->
                val resId = remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
                if (resId == 0) return@forEach
                val selected = wallpaper is WallpaperRender.Preset &&
                    context.resources.getResourceEntryName(wallpaper.resId) == name
                Image(
                    painter = painterResource(resId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onPresetChange(name) },
                )
            }
        }
        if (wallpaper is WallpaperRender.Transparent) {
            Text(
                text = stringResource(R.string.wallpaper_permission_hint),
                style = MaterialTheme.typography.bodySmall,
                color = OnGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnGlass,
            modifier = Modifier.weight(0.28f),
        )
        Slider(value = value, onValueChange = onChange, modifier = Modifier.weight(0.72f))
    }
}

@Composable
private fun ToggleTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(16.dp),
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = OnGlass)
    }
}

private fun openPanel(context: Context, panelAction: String, fallback: String) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Intent(panelAction)
    } else {
        Intent(fallback)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun streamRatio(audio: AudioManager, stream: Int): Float {
    val max = audio.getStreamMaxVolume(stream).takeIf { it > 0 } ?: return 0f
    return audio.getStreamVolume(stream).toFloat() / max
}

private fun setStream(audio: AudioManager, stream: Int, ratio: Float) {
    val max = audio.getStreamMaxVolume(stream)
    audio.setStreamVolume(stream, (ratio * max).toInt().coerceIn(0, max), 0)
}
